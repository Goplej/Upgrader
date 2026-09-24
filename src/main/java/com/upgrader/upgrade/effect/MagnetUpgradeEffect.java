package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Item-attraction radius around the holder: level metres, capped at 16. The runtime tick handler
 * pulls dropped items within this radius when the stack is equipped.
 */
public final class MagnetUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "magnet");
    public static final String TAG_MAGNET_RADIUS = "magnet_radius";
    private static final int MAX_RADIUS = 16;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return true; // any held item can carry the magnet property
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putInt(TAG_MAGNET_RADIUS, Math.min(MAX_RADIUS, level));
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Magnet effect skipped for {}", stack, t);
        }
    }
}
