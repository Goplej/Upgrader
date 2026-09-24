package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;

/**
 * Vein/area mining unlocks at level 3+, radius grows by 1 every 4 levels and is hard-capped by
 * {@link UpgradeContext#blocksPerTickLimit()} — the server decides, the item only stores intent.
 */
public final class AreaMiningUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "area_mining");
    public static final String TAG_RADIUS = "area_radius";

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            int radius = level >= 3 ? 1 + (level - 3) / 4 : 0;
            if (!ctx.areaMiningEnabled()) {
                radius = 0;
            }
            radius = Math.min(radius, Math.max(1, (int) Math.sqrt(ctx.blocksPerTickLimit())));
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putInt(TAG_RADIUS, radius);
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Area mining effect skipped for {}", stack, t);
        }
    }
}
