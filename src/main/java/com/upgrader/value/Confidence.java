package com.upgrader.value;

import com.upgrader.util.MathUtil;

/**
 * How much the engine trusts a computed contribution.
 *
 * <p>{@link #combine(Confidence, Confidence)} keeps the stronger of two confidences (used when
 * several independent sources agree), {@link #degrade()} steps one level down (used when limits
 * truncate an analysis). {@link #weight()} feeds the weighted aggregation in
 * {@link ValueCalculator}.</p>
 */
public enum Confidence {

    /** Direct authoritative data: exact recipe, EMC table, explicit override. */
    HIGH(1.0),
    /** Solid but indirect evidence: tag material price, durability math, component sums. */
    MEDIUM(0.6),
    /** Weak heuristics only: rarity, stack size, creative tab membership. */
    LOW(0.3),
    /** Nothing was learned about this item. Value must be treated as unknown, not zero. */
    UNKNOWN(0.0);

    private final double weight;

    Confidence(double weight) {
        this.weight = weight;
    }

    /** Aggregation weight in [0,1]. */
    public double weight() {
        return weight;
    }

    /** One level weaker; {@link #UNKNOWN} stays {@link #UNKNOWN}. */
    public Confidence degrade() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM -> LOW;
            case LOW -> LOW;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /** The stronger of two confidences. */
    public static Confidence combine(Confidence a, Confidence b) {
        return a.weight >= b.weight ? a : b;
    }

    /** The weaker of two confidences. */
    public static Confidence min(Confidence a, Confidence b) {
        return a.weight <= b.weight ? a : b;
    }

    /** True when this confidence carries any trust at all. */
    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** Scales a raw contribution by this confidence's weight, saturating safely. */
    public long applyTo(long amount) {
        return MathUtil.roundSaturating(amount * weight);
    }
}
