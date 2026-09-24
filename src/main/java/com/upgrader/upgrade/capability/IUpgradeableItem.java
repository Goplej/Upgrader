package com.upgrader.upgrade.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Attachment contract for items that can carry Upgrader levels.
 *
 * <p>Implemented by the default {@link UpgradeCapability} stored on every ItemStack via an
 * {@code ICapabilityProvider}. The base-value snapshot is captured the first time the engine
 * prices an item so later recipe changes cannot silently re-price already-upgraded gear
 * (players keep the economy they bought into).</p>
 */
public interface IUpgradeableItem {

    /** Current upgrade level, &ge; 0. */
    int getUpgradeLevel();

    /** Sets the level; clamped to &ge; 0 by implementations. */
    void setUpgradeLevel(int level);

    /** Value of the pristine item at snapshot time, or -1 when never snapshotted. */
    long getBaseValueSnapshot();

    /** Captures the snapshot once; later calls are no-ops unless reset explicitly. */
    void setBaseValueSnapshot(long value);

    /** Serialises to NBT under the {@code upgrader} root key. */
    CompoundTag serializeNBT();

    /** Restores from NBT written by {@link #serializeNBT()}; tolerant of missing keys. */
    void deserializeNBT(CompoundTag tag);

    /** Convenience: read the level straight off a stack without touching capabilities. */
    static int levelOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        CompoundTag up = tag.getCompound(UpgradeCapability.TAG_ROOT);
        return Math.max(0, up.getInt(UpgradeCapability.TAG_LEVEL));
    }
}
