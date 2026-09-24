package com.upgrader.upgrade;

import com.upgrader.util.MathUtil;

/**
 * Pure math of the cost ladder: {@code cost(L) = base * growth^L / (1 + diminishing*L)}.
 *
 * <p>Stateless value object so both the server engine and the client GUI compute identical
 * numbers from the same three parameters — no sync packet needed for previewing costs.</p>
 */
public record UpgradeCurve(double growthFactor, double diminishingFactor, int maxLevel) {

    public UpgradeCurve {
        growthFactor = MathUtil.clamp(growthFactor, 1.0D, 10.0D);
        diminishingFactor = MathUtil.clamp(diminishingFactor, 0.0D, 1.0D);
        maxLevel = Math.max(0, maxLevel); // 0 == unlimited
    }

    /** Cost of upgrading FROM {@code level} TO {@code level+1}. Always &ge; 0, saturating. */
    public long costOf(long baseValue, int level) {
        if (baseValue <= 0L || level < 0) {
            return 0L;
        }
        try {
            double growth = Math.pow(growthFactor, level);
            if (!(growth < Double.MAX_VALUE / 4.0)) {
                return Long.MAX_VALUE; // exponent blew up: astronomically expensive, honestly
            }
            double raw = baseValue * growth / (1.0 + diminishingFactor * level);
            return MathUtil.roundSaturating(raw);
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    /** Whether {@code level -> level+1} is allowed by the configured cap. */
    public boolean canGoBeyond(int level) {
        return maxLevel <= 0 || level < maxLevel;
    }
}
