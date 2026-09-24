package com.upgrader.value;

/**
 * Coarse ordinal classification of a numeric item value.
 *
 * <p>Tiers exist for display and gameplay gating (upgrade recipes can require "at least RARE");
 * they are derived purely from the number, never configured per item.</p>
 */
public enum ValueTier {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC,
    DIVINE;

    /** Inclusive lower bound of every tier above {@link #COMMON}. */
    private static final long[] THRESHOLDS = {16L, 256L, 4096L, 65536L, 1_048_576L, 16_777_216L};

    /** Maps a non-negative numeric value onto its tier. Values &lt; 16 are {@link #COMMON}. */
    public static ValueTier fromValue(long value) {
        if (value <= 0) {
            return COMMON;
        }
        ValueTier tier = COMMON;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (value >= THRESHOLDS[i]) {
                tier = values()[i + 1];
            } else {
                break;
            }
        }
        return tier;
    }

    /** Ordinal index (0-based), handy for config and network encoding. */
    public int id() {
        return ordinal();
    }

    /** Reverse of {@link #id()}; clamps instead of throwing on out-of-range input. */
    public static ValueTier byId(int id) {
        ValueTier[] all = values();
        return all[com.upgrader.util.MathUtil.clamp(id, 0, all.length - 1)];
    }
}
