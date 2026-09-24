package com.upgrader.value;

import com.upgrader.Upgrader;
import com.upgrader.cache.CacheKey;
import com.upgrader.cache.ValueCache;
import com.upgrader.recipe.RecipeIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full value pipeline for one {@code ItemStack}:
 * cache probe → ordered source chain → {@link ValueCalculator} aggregation → override resolution
 * → cache write.
 *
 * <p>Thread-safety: the engine itself is stateless apart from the shared
 * {@link ValueSourceRegistry} and {@link ValueCache} (both concurrent). A fresh
 * {@link ValueContext} is created per calculation, so parallel calls never share mutable state.</p>
 */
public final class ValueEngine {

    private final ValueCache cache;
    private final OverrideResolver overrides;

    public ValueEngine(ValueCache cache, OverrideResolver overrides) {
        this.cache = cache;
        this.overrides = overrides;
    }

    /**
     * Convenience overload without a world: recipe/tag sources self-disable via
     * {@code isApplicable}, so heuristics still produce an honest (LOW/UNKNOWN) answer.
     */
    public ItemValue computeValue(ItemStack stack) {
        return computeValue(stack, null, null);
    }

    /**
     * Full pipeline entry point.
     *
     * @param stack   item to analyse; empty stacks short-circuit to {@link ItemValue#emptyStack()}
     * @param level   current world for recipe access (nullable in tests / before worlds load)
     * @param index   pre-baked recipe index (rebuilt lazily by RecipeValueSource when null)
     */
    public ItemValue computeValue(ItemStack stack, Level level, RecipeIndex index) {
        if (stack == null || stack.isEmpty()) {
            return ItemValue.emptyStack();
        }
        ResourceLocation id = com.upgrader.recipe.ItemIdentity.of(stack);
        if (id == null) {
            return ItemValue.unknown(new ResourceLocation("minecraft", "air"), "unregistered item");
        }

        CacheKey key = CacheKey.of(stack, id);
        Optional<ItemValue> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        HolderLookup.Provider registries = level != null ? level.registryAccess() : null;
        ValueContext context = new ValueContext(registries,
                com.upgrader.config.UpgraderConfig.softTimeoutNanos(),
                com.upgrader.config.UpgraderConfig.maxRecursionDepth(),
                com.upgrader.config.UpgraderConfig.maxNodesPerCalculation());
        context.attachLevel(level, index);

        List<ValueContribution> contributions = new ArrayList<>();
        Confidence bestConfidence = Confidence.UNKNOWN;
        String primarySource = "none";
        long primaryAmount = -1;

        for (com.upgrader.value.source.ValueSource source : ValueSourceRegistry.get().ordered()) {
            try {
                if (!source.isApplicable(stack, context)) {
                    continue;
                }
                Optional<ValueContribution> contribution = source.evaluate(stack, context);
                if (contribution.isEmpty()) {
                    continue;
                }
                ValueContribution c = contribution.get();
                contributions.add(c);
                context.markSourceUsed(c.type());
                Confidence effective = degradeIfTruncated(source.baseConfidence(), context);
                if (effective.weight() > bestConfidence.weight()) {
                    bestConfidence = effective;
                }
                if (Math.abs(c.effectiveAmount()) > primaryAmount) {
                    primaryAmount = Math.abs(c.effectiveAmount());
                    primarySource = source.name();
                }
            } catch (Throwable t) {
                // One broken source must never take down the chain (third-party code included).
                Upgrader.LOGGER.error("Value source {} failed on {}; skipping", source.name(), id, t);
            }
        }

        ItemValue computed = ValueCalculator.aggregate(id, contributions, bestConfidence, primarySource,
                context.cycleDetected(), context.depthReached(), context.nodesProcessed());

        // Operator overrides win over everything and carry HIGH confidence.
        ItemValue finalValue = overrides.resolve(stack, id, computed);

        cache.put(key, finalValue);
        if (finalValue.confidence() == Confidence.UNKNOWN) {
            Upgrader.LOGGER.debug("No trustworthy evidence for {}: reporting UNKNOWN", id);
        } else if (finalValue.confidence() == Confidence.LOW) {
            Upgrader.LOGGER.warn("Degraded confidence ({}) for {}: {}", finalValue.primarySource(), id,
                    context.timedOut() ? "soft timeout" : "heuristic-only evidence");
        }
        return finalValue;
    }

    private static Confidence degradeIfTruncated(Confidence base, ValueContext ctx) {
        Confidence c = base;
        if (ctx.timedOut() || ctx.nodesExhausted()) {
            c = c.degrade();
        }
        if (ctx.cycleDetected()) {
            c = c.degrade();
        }
        return c;
    }

    /** Drops every cached value (used by /upgrader reload and invalidation events). */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    public ValueCache cache() {
        return cache;
    }

}
