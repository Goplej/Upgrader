package com.upgrader.value;

import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of analysing one {@code ItemStack}: the number plus full provenance.
 *
 * <p>Every field required by the spec is present — value, tier, confidence, per-source
 * breakdown, primary source, computation timestamp and recipe-tree diagnostics
 * ({@code cycleDetected}, {@code depthReached}, {@code nodesProcessed}) so operators can audit
 * <em>why</em> an item is worth what it is worth.</p>
 */
public record ItemValue(
        ResourceLocation itemId,
        long numericValue,
        ValueTier tier,
        Confidence confidence,
        List<ValueContribution> breakdown,
        String primarySource,
        Instant computedAt,
        boolean cycleDetected,
        int depthReached,
        int nodesProcessed
) {

    public ItemValue {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(confidence, "confidence");
        breakdown = breakdown == null ? List.of() : List.copyOf(breakdown);
        primarySource = primarySource == null ? "none" : primarySource;
        computedAt = computedAt == null ? Instant.EPOCH : computedAt;
        numericValue = Math.max(0L, numericValue);
        depthReached = Math.max(0, depthReached);
        nodesProcessed = Math.max(0, nodesProcessed);
    }

    /** Honest "we know nothing" result — value 0 with {@link Confidence#UNKNOWN}, never a fake number. */
    public static ItemValue unknown(ResourceLocation id, String reason) {
        return new ItemValue(id, 0L, ValueTier.COMMON, Confidence.UNKNOWN,
                List.of(new ValueContribution("upgrader:none", SourceType.HEURISTIC, 0L,
                        reason == null ? "no evidence available" : reason, 0.0)),
                "none", Instant.now(), false, 0, 0);
    }

    /** Zero-value placeholder for {@code ItemStack.EMPTY}. */
    public static ItemValue emptyStack() {
        return new ItemValue(new ResourceLocation("minecraft", "air"), 0L, ValueTier.COMMON,
                Confidence.HIGH, List.of(), "empty_stack", Instant.now(), false, 0, 0);
    }

    /** True when at least one source produced trustworthy evidence. */
    public boolean isKnown() {
        return confidence.isKnown();
    }

    /** Same identity and number but recomputed tier/confidence metadata. */
    public ItemValue withNumericValue(long newValue) {
        return new ItemValue(itemId, newValue, ValueTier.fromValue(newValue), confidence,
                breakdown, primarySource, computedAt, cycleDetected, depthReached, nodesProcessed);
    }
}
