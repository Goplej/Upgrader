package com.upgrader.value.source;

import com.upgrader.Upgrader;
import com.upgrader.recipe.RecipeIndex;
import com.upgrader.recipe.RecipeTreeBuilder;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Priority-10 workhorse: prices items by walking their recipe dependency tree.
 *
 * <p>Owns the world's {@link RecipeIndex}; sources lower in the chain (tag/property) consult
 * {@link #currentIndex()} to know whether recipe evidence already exists. The index is rebuilt
 * lazily whenever the attached level changes or a reload invalidated it.</p>
 */
public final class RecipeValueSource implements ValueSource {

    public static final String NAME = "upgrader:recipe";

    private static volatile RecipeIndex currentIndex = RecipeIndex.EMPTY;
    private volatile Level indexedLevel;
    private volatile long indexGeneration;

    /** Index currently in use (shared read-only with other sources and the engine). */
    public static RecipeIndex currentIndex() {
        return currentIndex;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Confidence baseConfidence() {
        return Confidence.HIGH;
    }

    @Override
    public boolean isApplicable(ItemStack stack, ValueContext context) {
        return context.level() != null; // without a world there are no recipes to analyse
    }

    @Override
    public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
        Level level = context.level();
        RecipeIndex index = ensureIndex(level);
        RecipeTreeBuilder.TreeResult result =
                RecipeTreeBuilder.computeCost(index, level.registryAccess(), stack, context);
        if (!result.hasRecipe()) {
            return Optional.empty();
        }
        context.recordDepth(result.depthReached());
        if (result.cost() <= 0L) {
            return Optional.of(new ValueContribution(NAME, SourceType.RECIPE, 0L,
                    "crafted from free/unknown components", Confidence.LOW.weight()));
        }
        Confidence confidence = result.exact() ? Confidence.HIGH : Confidence.MEDIUM;
        String explanation = result.exact()
                ? String.format("minimal recipe cost %d per unit (tree walked exactly)", result.cost())
                : String.format("partial recipe cost %d (truncated by depth/node/time budget)", result.cost());
        return Optional.of(new ValueContribution(NAME, SourceType.RECIPE, result.cost(), explanation,
                confidence.weight()));
    }

    /** Rebuilds the recipe index when the world changed or an external invalidation bumped it. */
    private RecipeIndex ensureIndex(Level level) {
        RecipeIndex local = currentIndex;
        if (indexedLevel == level && local != null && !local.equals(RecipeIndex.EMPTY)) {
            return local;
        }
        synchronized (this) {
            if (indexedLevel == level && currentIndex != RecipeIndex.EMPTY) {
                return currentIndex;
            }
            try {
                long start = System.nanoTime();
                currentIndex = RecipeIndex.build(level);
                indexedLevel = level;
                Upgrader.LOGGER.info("Recipe index rebuilt: {} recipes in {} ms",
                        currentIndex.size(), (System.nanoTime() - start) / 1_000_000L);
            } catch (Throwable t) {
                Upgrader.LOGGER.error("Recipe index build failed; recipe pricing disabled this tick", t);
                currentIndex = RecipeIndex.EMPTY;
            }
            return currentIndex;
        }
    }

    /** Drops the cached index (called on reload events via the registry singleton). */
    public void invalidateIndex() {
        synchronized (this) {
            currentIndex = RecipeIndex.EMPTY;
            indexedLevel = null;
            indexGeneration++;
        }
    }
}
