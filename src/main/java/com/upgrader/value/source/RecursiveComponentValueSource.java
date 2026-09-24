package com.upgrader.value.source;

import com.upgrader.Upgrader;
import com.upgrader.item.ItemIdentityResolver;
import com.upgrader.mod.ModOriginDetector;
import com.upgrader.recipe.RecipeIndex;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Optional;

/**
 * Priority-15 fallback for items whose recipes exist but could not be priced exactly by the
 * recipe tree (e.g. the full walk timed out): sums one cheap, direct evidence layer —
 * "this item is crafted from N components we DO know the origin and identity of".
 *
 * <p>Deliberately conservative: it only fires when {@link ValueContext} has no RECIPE evidence
 * yet, so it can never double-count with a successful tree walk.</p>
 */
public final class RecursiveComponentValueSource implements ValueSource {

    public static final String NAME = "upgrader:components";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public Confidence baseConfidence() {
        return Confidence.MEDIUM;
    }

    @Override
    public boolean isApplicable(ItemStack stack, ValueContext context) {
        return !context.isSourceUsed(SourceType.RECIPE) && context.recipeIndex() != null;
    }

    @Override
    public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
        RecipeIndex index = context.recipeIndex();
        ResourceLocation id = ItemIdentityResolver.resolve(stack);
        if (id == null) {
            return Optional.empty();
        }
        List<Recipe<?>> recipes = index.recipesFor(id);
        if (recipes.isEmpty()) {
            return Optional.empty();
        }
        // Evidence: the cheapest single-level ingredient count over all alternatives.
        int bestIngredientCount = Integer.MAX_VALUE;
        for (Recipe<?> recipe : recipes) {
            try {
                int total = 0;
                for (Ingredient ing : recipe.getIngredients()) {
                    if (ing != null && !ing.isEmpty()) {
                        total++;
                    }
                }
                bestIngredientCount = Math.min(bestIngredientCount, total);
            } catch (Throwable t) {
                Upgrader.LOGGER.debug("component scan failed for {}: {}", id, t.toString());
            }
        }
        if (bestIngredientCount == Integer.MAX_VALUE || bestIngredientCount == 0) {
            return Optional.empty();
        }
        long estimate = 4L * bestIngredientCount; // each unknown component anchors at 4 (UNCOMMON grain)
        String modName = ModOriginDetector.displayNameOf(id);
        return Optional.of(new ValueContribution(NAME, SourceType.COMPONENT, estimate,
                String.format("%s: crafted from %d components (direct layer only)", modName, bestIngredientCount),
                Confidence.MEDIUM.weight()));
    }
}
