package com.upgrader.upgrade;

import com.upgrader.Upgrader;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.upgrade.capability.IUpgradeableItem;
import com.upgrader.upgrade.capability.UpgradeCapabilities;
import com.upgrader.upgrade.capability.UpgradeItemEvents;
import com.upgrader.upgrade.effect.UpgradeContext;
import com.upgrader.upgrade.effect.UpgradeEffect;
import com.upgrader.value.ItemValue;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side authority for performing upgrades.
 *
 * <h2>Transaction</h2>
 * <ol>
 *   <li>Re-validate everything server-side (never trust the client that asked).</li>
 *   <li>Read/compute the base value snapshot (first upgrade freezes the economy for this item).</li>
 *   <li>Price the next level via {@link UpgradeCostCalculator}.</li>
 *   <li>Charge the player's inventory value pool (items are consumed by value, cheapest first —
 *       see {@link InventoryValueBank}).</li>
 *   <li>Bump the capability level and apply every applicable {@link UpgradeEffect}.</li>
 * </ol>
 * Any failure rolls nothing back implicitly because each step is guarded before mutation.
 */
public final class UpgradeEngine {

    /** Immutable outcome of an upgrade attempt. */
    public record Result(boolean success, int newLevel, long charged, String reasonKey) {
        public static Result fail(String key) {
            return new Result(false, -1, 0L, key);
        }
    }

    private UpgradeEngine() {
    }

    /** Current level of a stack (capability, falling back to raw NBT mirror). */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        var cap = stack.getCapability(UpgradeCapabilities.UPGRADEABLE);
        Integer level = cap.map(IUpgradeableItem::getUpgradeLevel).orElse(null);
        return level != null ? level : IUpgradeableItem.levelOf(stack);
    }

    /** Preview cost without mutating anything; -1 when unpriceable. */
    public static long previewNextCost(ItemStack stack) {
        ItemValue value = UpgraderAPI.getItemValue(stack);
        long base = effectiveBase(stack, value);
        if (base <= 0) {
            return -1L;
        }
        return UpgradeCostCalculator.nextCostFromBase(base, levelOf(stack));
    }

    /** Full server-side upgrade transaction. Must run on the server thread. */
    public static Result tryUpgrade(ItemStack stack, InventoryValueBank paymentSource) {
        if (stack == null || stack.isEmpty()) {
            return Result.fail("upgrader.msg.empty_hand");
        }
        UpgradeCurve curve = UpgradeCostCalculator.currentCurve();
        int level = levelOf(stack);
        if (!curve.canGoBeyond(level)) {
            return Result.fail("upgrader.msg.max_level");
        }
        ItemValue value = UpgraderAPI.getItemValue(stack);
        long base = effectiveBase(stack, value);
        if (base <= 0L) {
            return Result.fail("upgrader.msg.unknown_value");
        }
        long cost = curve.costOf(base, level);
        long paid = paymentSource.charge(cost);
        if (paid < cost) {
            paymentSource.refund(paid); // discard the simulated debit plan
            return Result.fail("upgrader.msg.insufficient_value");
        }
        paymentSource.commit(); // only now items actually leave the inventory

        int newLevel = level + 1;
        var ctx = new UpgradeContext(curve.growthFactor(), 32, true);
        stack.getCapability(UpgradeCapabilities.UPGRADEABLE).ifPresent(cap -> {
            cap.setBaseValueSnapshot(base);
            cap.setUpgradeLevel(newLevel);
        });
        UpgradeItemEvents.writeLevelToStack(stack, newLevel, base);
        for (UpgradeEffect effect : UpgradeEffectRegistry.all()) {
            try {
                if (effect.isApplicableTo(stack)) {
                    effect.apply(stack, newLevel, ctx);
                }
            } catch (Throwable t) {
                Upgrader.LOGGER.error("Upgrade effect {} failed on {}; level still applied",
                        effect.getId(), stack.getItem(), t);
            }
        }
        Upgrader.LOGGER.info("Upgraded {} to level {} (charged {} value)",
                stack.getDescriptionId(), newLevel, cost);
        return new Result(true, newLevel, cost, "upgrader.msg.upgraded");
    }

    private static long effectiveBase(ItemStack stack, ItemValue fresh) {
        long snapshot = stack.getCapability(UpgradeCapabilities.UPGRADEABLE)
                .map(IUpgradeableItem::getBaseValueSnapshot).orElse(-1L);
        if (snapshot > 0) {
            return snapshot;
        }
        return fresh != null && fresh.isKnown() ? Math.max(1L, fresh.numericValue()) : 0L;
    }
}
