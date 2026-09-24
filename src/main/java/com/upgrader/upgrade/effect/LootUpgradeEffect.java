package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Loot-quality bonus consumed by the global loot modifier hook: +2% lucky roll chance per level
 * (capped at +30%). Stored as permille so it survives NBT round-trips exactly.
 */
public final class LootUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "loot");
    public static final String TAG_LOOT_PERMILLE = "loot_permille";

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return true;
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            int permille = Math.min(300, level * 20);
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putInt(TAG_LOOT_PERMILLE, permille);
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Loot effect skipped for {}", stack, t);
        }
    }
}
