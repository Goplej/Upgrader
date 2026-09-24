package com.upgrader.upgrade.capability;

import net.minecraft.nbt.CompoundTag;

/**
 * Default {@link IUpgradeableItem} implementation backed by a mutable field pair.
 *
 * <p>Lives inside {@link UpgradeCapabilityProvider}; state is mirrored into the stack's NBT on
 * save through {@link #serializeNBT()} / {@link #deserializeNBT(CompoundTag)}.</p>
 */
public final class UpgradeCapability implements IUpgradeableItem {

    /** Root NBT key owned by this mod. */
    public static final String TAG_ROOT = "upgrader";
    public static final String TAG_LEVEL = "upgrade_level";
    public static final String TAG_BASE_SNAPSHOT = "base_value_snapshot";

    private int level;
    private long baseSnapshot = -1L;

    @Override
    public int getUpgradeLevel() {
        return level;
    }

    @Override
    public void setUpgradeLevel(int lvl) {
        this.level = Math.max(0, lvl);
    }

    @Override
    public long getBaseValueSnapshot() {
        return baseSnapshot;
    }

    @Override
    public void setBaseValueSnapshot(long value) {
        if (this.baseSnapshot < 0) {
            this.baseSnapshot = Math.max(0L, value); // write-once semantics
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        root.putInt(TAG_LEVEL, level);
        if (baseSnapshot >= 0) {
            root.putLong(TAG_BASE_SNAPSHOT, baseSnapshot);
        }
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        try {
            this.level = Math.max(0, tag.getInt(TAG_LEVEL));
            if (tag.contains(TAG_BASE_SNAPSHOT)) {
                this.baseSnapshot = Math.max(0L, tag.getLong(TAG_BASE_SNAPSHOT));
            }
        } catch (Throwable ignored) {
            // Corrupt payload: keep defaults rather than crash the load.
        }
    }
}
