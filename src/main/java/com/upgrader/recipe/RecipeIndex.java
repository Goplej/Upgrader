package com.upgrader.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable, pre-baked index of every recipe in a world, keyed by output item id.
 *
 * <p>Building it is O(total recipes × ingredients); looking up "what crafts this?" afterwards is
 * O(1). Rebuilt on {@code RecipesUpdatedEvent} and cached inside
 * {@link com.upgrader.value.source.RecipeValueSource}; never rebuilt mid-calculation.</p>
 */
public final class RecipeIndex {

    /** Empty index used before any world exists (and as a safe fallback). */
    public static final RecipeIndex EMPTY = new RecipeIndex(Map.of(), 0);

    private final Map<ResourceLocation, List<Recipe<?>>> byOutput;
    private final int totalRecipes;
    /** Monotonic id of the datapack reload this index was built from. */
    public static volatile long currentGeneration = 0L;

    private RecipeIndex(Map<ResourceLocation, List<Recipe<?>>> byOutput, int totalRecipes) {
        this.byOutput = byOutput;
        this.totalRecipes = totalRecipes;
    }

    /** All recipes whose result item has the given item id (never null, possibly empty). */
    public List<Recipe<?>> recipesFor(ResourceLocation outputId) {
        return byOutput.getOrDefault(outputId, List.of());
    }

    public int size() {
        return totalRecipes;
    }

    /**
     * Scans the level's recipe manager once. Custom recipe types are included automatically as
     * long as they expose {@link Recipe#getIngredients()} and a non-empty result — no reflection,
     * no type whitelist. Vanilla CRAFTING/SMELTING/BLASTING/SMOKING/CAMPFIRE/STONECUTTING/SMITHING
     * all flow through the same generic path.
     */
    public static RecipeIndex build(Level level) {
        Map<ResourceLocation, List<Recipe<?>>> map = new Object2ObjectOpenHashMap<>();
        int count = 0;
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (recipe == null) {
                continue; // corrupted datapack entry — skip instead of crashing
            }
            count++;
            ItemStack result;
            try {
                result = recipe.getResultItem(level.registryAccess());
            } catch (Throwable t) {
                continue; // defensive: broken custom recipe implementations
            }
            if (result.isEmpty()) {
                continue;
            }
            ResourceLocation outId = ItemIdentity.of(result);
            if (outId == null) {
                continue;
            }
            map.computeIfAbsent(outId, k -> new ArrayList<>(2)).add(recipe);
        }
        Map<ResourceLocation, List<Recipe<?>>> frozen = new Object2ObjectOpenHashMap<>(map.size() * 2);
        map.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return new RecipeIndex(Collections.unmodifiableMap(frozen), count);
    }

    /** True when an ingredient slot can never be filled (data-driven mods sometimes emit these). */
    public static boolean isEmptyIngredient(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty();
    }
}
