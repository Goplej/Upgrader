package com.upgrader.value;

/**
 * A single row of an {@link ItemValue} breakdown: one source's opinion about worth.
 *
 * @param sourceName  stable identifier of the producing source (e.g. {@code "upgrader:recipe"})
 * @param type        evidence category, drives anti-double-counting and GUI grouping
 * @param amount      raw positive contribution before weighting (never negative except penalties)
 * @param explanation human-readable justification, shown verbatim in debug output
 * @param weight      confidence-derived multiplier already applied to reach the effective amount;
 *                   kept separately so the UI can show "raw x weight = effective"
 */
public record ValueContribution(String sourceName, SourceType type, long amount, String explanation, double weight) {

    public ValueContribution {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        explanation = explanation == null ? "" : explanation;
        weight = com.upgrader.util.MathUtil.clamp(weight, 0.0, 1.0);
    }

    /** Contribution after applying the confidence weight. */
    public long effectiveAmount() {
        return com.upgrader.util.MathUtil.roundSaturating(amount * weight);
    }

    /** Convenience factory for penalty contributions (negative adjustments). */
    public static ValueContribution penalty(String sourceName, SourceType type, long amount, String explanation) {
        return new ValueContribution(sourceName, type, -Math.abs(amount), explanation, 1.0);
    }
}
