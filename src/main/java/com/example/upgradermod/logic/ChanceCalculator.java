package com.example.upgradermod.logic;

import com.example.upgradermod.UpgraderConstants;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Chance calculation (Section 1.2).
 *
 * <pre>
 *   ratio  = inputValue / (targetValue * multiplier)
 *   chance = clamp(ratio * 100, 1e-21, 90.0)
 *   result = random.nextDouble() &lt; (chance / 100)
 * </pre>
 *
 * <p>All arithmetic is done in {@code double} so that the huge value range
 * ({@code 1 .. 10_000_000_000_000}) times the bet multiplier can never overflow.</p>
 */
public final class ChanceCalculator {

    /**
     * Computes the success chance in percent.
     *
     * @param inputValue value of the item being consumed
     * @param targetValue value of the item being won
     * @param multiplier bet multiplier, values below {@code 1} are treated as {@code 1}
     * @return the chance in percent, inside {@code [1e-21, 90.0]}
     */
    public static double chance(long inputValue, long targetValue, int multiplier) {
        if (inputValue <= 0L || targetValue <= 0L) {
            return UpgraderConstants.MIN_CHANCE;
        }

        int bet = Math.max(1, multiplier);
        double denominator = (double) targetValue * (double) bet;
        if (!(denominator > 0.0D) || !Double.isFinite(denominator)) {
            return UpgraderConstants.MIN_CHANCE;
        }

        double ratio = (double) inputValue / denominator;
        if (!Double.isFinite(ratio)) {
            // Absurd input value: cap at the maximum allowed chance instead of producing NaN.
            return UpgraderConstants.MAX_CHANCE;
        }

        return Mth.clamp(ratio * 100.0D, UpgraderConstants.MIN_CHANCE, UpgraderConstants.MAX_CHANCE);
    }

    /**
     * Rolls the dice. Called on the server only.
     *
     * @param chance chance in percent
     * @param random random source of the spinning player
     * @return {@code true} when the spin succeeded
     */
    public static boolean roll(double chance, RandomSource random) {
        if (random == null) {
            return false;
        }
        double clamped = Mth.clamp(chance, UpgraderConstants.MIN_CHANCE, UpgraderConstants.MAX_CHANCE);
        return random.nextDouble() < (clamped / 100.0D);
    }

    /**
     * Formats a chance for the GUI.
     *
     * @param chance chance in percent
     * @return a human readable percentage string
     */
    public static String format(double chance) {
        double clamped = Mth.clamp(chance, 0.0D, 100.0D);
        if (clamped <= 0.0D) {
            return "0%";
        }
        if (clamped < 0.01D) {
            return String.format(java.util.Locale.ROOT, "%.2E%%", clamped);
        }
        if (clamped < 1.0D) {
            return String.format(java.util.Locale.ROOT, "%.3f%%", clamped);
        }
        return String.format(java.util.Locale.ROOT, "%.2f%%", clamped);
    }

    private ChanceCalculator() {
        // Static access only.
    }
}
