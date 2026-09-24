package com.upgrader.value;

import com.upgrader.Upgrader;
import com.upgrader.util.MathUtil;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.util.List;

/**
 * Folds a list of {@link ValueContribution}s into a single immutable {@link ItemValue}.
 *
 * <h2>Aggregation rules</h2>
 * <ol>
 *   <li><b>Anti-double-counting:</b> the first RECIPE-class evidence (RECIPE or COMPONENT) wins;
 *       later ones are dropped so a block is never priced as "recipe cost + component sum".
 *       TAG evidence is only trusted while no recipe evidence exists — otherwise iron_ingot would
 *       add its tag price on top of the iron_block recipe that already includes it.</li>
 *   <li>Positive amounts saturate-sum; penalties (negative amounts, e.g. NBT complexity) apply
 *       afterwards and clamp at zero rather than going negative.</li>
 *   <li>The result keeps every contribution in the breakdown for transparency, even dropped ones
 *       (they were never added to the total).</li>
 * </ol>
 */
public final class ValueCalculator {

    private ValueCalculator() {
    }

    /** Weighted, de-duplicated aggregation. Never throws. */
    public static ItemValue aggregate(ResourceLocation id, List<ValueContribution> contributions,
                                      Confidence bestConfidence, String primarySource,
                                      boolean cycleDetected, int depthReached, int nodesProcessed) {
        long total = 0L;
        boolean haveRecipeEvidence = false;
        int effectiveCount = 0;

        for (ValueContribution c : contributions) {
            if (c.amount() == 0 && c.weight() == 0.0) {
                continue; // placeholder rows (e.g. UNKNOWN marker)
            }
            switch (c.type()) {
                case RECIPE, COMPONENT -> {
                    if (haveRecipeEvidence) {
                        Upgrader.LOGGER.debug("anti-double-count: dropping {} contribution for {}", c.sourceName(), id);
                        continue;
                    }
                    haveRecipeEvidence = true;
                }
                case TAG -> {
                    if (haveRecipeEvidence) {
                        continue; // recipe already prices the materials
                    }
                }
                default -> {
                }
            }
            if (c.amount() >= 0) {
                total = MathUtil.addSaturating(total, c.effectiveAmount());
                effectiveCount++;
            } else {
                total = Math.max(0L, total + c.amount()); // penalties clamp at zero
            }
        }

        if (effectiveCount == 0 && total == 0L) {
            return ItemValue.unknown(id, "no source produced a usable value");
        }
        Confidence confidence = bestConfidence == null ? Confidence.UNKNOWN : bestConfidence;
        if (!confidence.isKnown()) {
            return ItemValue.unknown(id, "all sources reported UNKNOWN");
        }
        return new ItemValue(id, total, ValueTier.fromValue(total), confidence,
                List.copyOf(contributions), primarySource, Instant.now(),
                cycleDetected, depthReached, nodesProcessed);
    }
}
