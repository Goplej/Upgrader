package com.upgrader.util;

/**
 * Overflow-safe arithmetic helpers used by the value pipeline.
 *
 * <p>The engine never throws on numeric overflow: every saturating operation clamps to
 * {@link Long#MAX_VALUE} (or 0 for negative results), because "astronomically expensive" is a
 * legitimate outcome for neutronium-tier items while an exception mid-tick is not.</p>
 */
public final class MathUtil {

    private MathUtil() {
    }

    /** {@code a + b} saturated at {@link Long#MAX_VALUE}; inputs are treated as non-negative. */
    public static long addSaturating(long a, long b) {
        if (a < 0) a = 0;
        if (b < 0) b = 0;
        try {
            return java.lang.Math.addExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** {@code a * b} saturated at {@link Long#MAX_VALUE}. */
    public static long multiplySaturating(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        try {
            return java.lang.Math.multiplyExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Rounds a finite double into a non-negative long, saturating instead of wrapping. */
    public static long roundSaturating(double value) {
        if (!(value > 0) || Double.isNaN(value)) return 0;          // NaN and negatives -> 0
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(value);
    }

    /** Clamps {@code v} into [{@code lo}, {@code hi}] with {@code lo <= hi}. */
    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Clamps {@code v} into [lo, hi]. */
    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
