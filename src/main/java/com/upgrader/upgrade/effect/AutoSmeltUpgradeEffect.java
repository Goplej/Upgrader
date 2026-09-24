package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-smelt unlocks at level 6+ (one block mined = one furnace output). Stored as a flag the
 * loot handler consults; deliberately NOT a Silk-Touch-style behaviour so it stays orthogonal.
 */
public final class AutoSmeltUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "auto_smelt");
    public static final String TAG_AUTO_SMELT = "auto_smelt";

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
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putBoolean(TAG_AUTO_SMELT, level >= 6);
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Auto smelt effect skipped for {}", stack, t);
        }
    }
}
