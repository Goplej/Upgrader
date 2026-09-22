package com.example.upgradermod.logic.providers;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Priority 700 &ndash; recipe based pricing.
 *
 * <pre>
 *   value(item) = min over all recipes producing the item of
 *                 ceil( SUM(quantity * ingredientValue) * depthMultiplier ) / outputCount
 * </pre>
 *
 * <p>Two protections are in place:</p>
 * <ul>
 *     <li>a {@link ValueContext} visited set: an ingredient that is already on the current recursion
 *     path is never priced again, which breaks circular recipes;</li>
 *     <li>{@link UpgraderConstants#MAX_RECIPE_DEPTH}: the recursion stops at depth 10.</li>
 * </ul>
 *
 * <p>The cheapest matching recipe wins, so the result does not depend on recipe iteration order.</p>
 */
public class RecipeValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 700;

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "recipe";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        try {
            if (stack == null || stack.isEmpty()) {
            return UNKNOWN;
        }
        if (context.getDepth() >= UpgraderConstants.MAX_RECIPE_DEPTH) {
            return UNKNOWN;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // No world loaded (main menu, early startup) - recipes are unknown right now.
            return UNKNOWN;
        }

        RecipeManager recipeManager;
        RegistryAccess registryAccess;
        try {
            recipeManager = server.getRecipeManager();
            registryAccess = server.registryAccess();
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not reach the recipe manager", throwable);
            return UNKNOWN;
        }
        if (recipeManager == null || registryAccess == null) {
            return UNKNOWN;
        }

        Item target = stack.getItem();
        ValueContext ingredientContext = context.descend(target);
        double multiplier = UpgraderConstants.depthMultiplier(context.getDepth());

        long best = UNKNOWN;
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            try {
                ItemStack output = recipe.getResultItem(registryAccess);
                if (output.isEmpty() || output.getItem() != target) {
                    continue;
                }

                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients.isEmpty()) {
                    continue;
                }

                long sum = 0L;
                boolean complete = true;
                for (Ingredient ingredient : ingredients) {
                    if (ingredient == null || ingredient.isEmpty()) {
                        continue; // Empty slot of a shaped recipe.
                    }

                    long price = priceIngredient(ingredient, ingredientContext);
                    if (price < 0L) {
                        complete = false;
                        break;
                    }
                    sum += price;
                }

                if (!complete || sum <= 0L) {
                    continue;
                }

                long scaled = (long) Math.ceil((double) sum * multiplier);
                int outputs = Math.max(1, output.getCount());
                scaled = Math.max(1L, scaled / outputs);
                scaled = Math.min(scaled, UpgraderConstants.MAX_PRICE);

                if (best < 0L || scaled < best) {
                    best = scaled;
                }
            } catch (Throwable throwable) {
                LOGGER.debug("Upgrader skipped a recipe while pricing {}", ValueCalculator.describe(stack), throwable);
            }
        }

            return best;
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader recipe provider failed", throwable);
            return UNKNOWN;
        }
    }

    /**
     * Prices one ingredient as {@code quantity * unit price}, using the cheapest candidate item.
     *
     * @param ingredient ingredient of a recipe
     * @param context    recursion context of the ingredient level
     * @return the ingredient price, or {@code -1} when it cannot be priced
     */
    private long priceIngredient(Ingredient ingredient, ValueContext context) {
        ItemStack[] candidates;
        try {
            candidates = ingredient.getItems();
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not expand an ingredient", throwable);
            return UNKNOWN;
        }
        if (candidates == null || candidates.length == 0) {
            return UNKNOWN;
        }

        long best = UNKNOWN;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (context.isVisited(candidate.getItem())) {
                // Circular recipe protection: never walk back onto the current recursion path.
                continue;
            }

            int quantity = Math.max(1, candidate.getCount());
            long unitPrice;

            Long cached = ItemRegistryCache.getCachedValue(candidate.getItem());
            if (cached != null) {
                unitPrice = cached;
            } else {
                ItemStack unit = candidate.copy();
                unit.setCount(1);
                unitPrice = ValueCalculator.calculate(unit, context);
            }

            if (unitPrice <= 0L) {
                continue;
            }

            long total = Math.min(unitPrice * quantity, UpgraderConstants.MAX_PRICE);
            if (best < 0L || total < best) {
                best = total;
            }
        }

        return best;
    }
}
