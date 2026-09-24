package com.upgrader.util;

/** Locale-stable formatting helpers for value numbers shown to players and admins. */
public final class TextFormatUtil {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

    private TextFormatUtil() {
    }

    /**
     * Compact human-readable form of a value: {@code 950}, {@code 12.3K}, {@code 4.7M}, ...
     * Uses {@link java.util.Locale#ROOT} so output never depends on player locale (tests stable).
     */
    public static String formatValue(final long value) {
        if (value < 0) {
            return "-" + formatValue(Math.abs(value)); // Long.MIN_VALUE-safe via saturation below
        }
        if (value < 1000L) {
            return Long.toString(value);
        }
        double d = value;
        int unit = 0;
        while (d >= 1000.0 && unit < SUFFIXES.length - 1) {
            d /= 1000.0;
            unit++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", d, SUFFIXES[unit]);
    }

    /** Full digit-grouped form used in debug output: {@code 1,234,567}. */
    public static String formatExact(final long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /** Percent with one decimal from a 0..1 ratio. */
    public static String formatPercent(final double ratio) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", MathUtil.clamp(ratio, 0.0, 1.0) * 100.0);
    }
}
