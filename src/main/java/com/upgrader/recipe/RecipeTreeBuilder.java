package com.upgrader.recipe;

import com.upgrader.Upgrader;
import com.upgrader.util.MathUtil;
import com.upgrader.value.ValueContext;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Iterative, budgeted recipe-dependency-tree walker (the engine's most safety-critical class).
 *
 * <h2>Why iterative</h2>
 * Deep modpack chains (Avaritia Neutronium -> Infinity Catalyst -> nine cosmic components -> ...)
 * nest 10-15 levels with enormous fan-out. Java recursion would risk {@link StackOverflowError};
 * this class keeps an explicit stack of {@link Frame}s instead - heap-only, unbounded by JVM
 * stack size. Each frame carries its own child cursor ({@code nextSlot}), so pausing a parent
 * while a child resolves is trivial and exception-free.
 *
 * <h2>Algorithm</h2>
 * A frame is initialised once: node budget check, cycle-path claim, memo lookup, then its
 * ingredient slots are enumerated into a slot list (multi-alternative ingredients keep every
 * alternative as a separate slot; the fold at completion naturally takes the minimum because
 * each alternative's cost lands in the same recipe accumulator and we also track the per-slot
 * minimum). When the cursor exhausts, the frame completes: the cheapest usable recipe total,
 * divided by output count, is memoized and folded into whatever parent slot opened it.
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li>Cycles prune to cost 0 and flag {@code cycleDetected}; nothing hangs or overflows.</li>
 *   <li>Depth/node/time budgets truncate expansion with {@code exact=false}, never an exception.</li>
 *   <li>Minimum-cost alternative wins per item AND per ingredient slot.</li>
 * </ul>
 */
public final class RecipeTreeBuilder {

    /** Result of walking the tree for one item. */
    public record TreeResult(long cost, boolean exact, boolean hasRecipe, int depthReached) {
        public static final TreeResult NO_RECIPE = new TreeResult(0L, false, false, 0);
    }

    /** One pending child edge: which parent accumulator receives the resolved cost. */
    private static final class Slot {
        final Frame owner;
        final int recipeIndex;
        final int slotIndex;
        final ResourceLocation[] alternativeIds;

        Slot(Frame owner, int recipeIndex, int slotIndex, ItemStack[] alternatives) {
            this.owner = owner;
            this.recipeIndex = recipeIndex;
            this.slotIndex = slotIndex;
            this.alternativeIds = new ResourceLocation[alternatives.length];
            for (int i = 0; i < alternatives.length; i++) {
                this.alternativeIds[i] = ItemIdentity.of(alternatives[i]);
            }
        }
    }

    /** Mutable node under construction on the explicit traversal stack. */
    private static final class Frame {
        final ResourceLocation id;
        final List<Recipe<?>> recipes;
        final int depth;
        final long[] recipeCosts;
        final int[] slotCount;
        final long[][] slotBest;
        Slot[] slots = new Slot[0];
        HolderLookup.Provider registries;
        int nextSlot;
        boolean pathClaimed;
        boolean pruned;

        Frame(ResourceLocation id, List<Recipe<?>> recipes, int depth) {
            this.id = id;
            this.recipes = recipes;
            this.depth = depth;
            this.recipeCosts = new long[Math.max(1, recipes.size())];
            this.slotCount = new int[Math.max(1, recipes.size())];
            this.slotBest = new long[Math.max(1, recipes.size())][];
        }
    }

    private RecipeTreeBuilder() {
    }

    /**
     * Minimal crafting cost of ONE unit of {@code stack}.
     *
     * @param index    pre-baked recipe index of the world
     * @param stack    item to price (stack size ignored - result is per single unit)
     * @param context  shared per-calculation budgets and cycle path
     */
    public static TreeResult computeCost(RecipeIndex index, ItemStack stack, ValueContext context) {
        return computeCost(index, null, stack, context);
    }

    /** Overload that threads registry access for reading recipe results. */
    public static TreeResult computeCost(RecipeIndex index, HolderLookup.Provider registries,
                                         ItemStack stack, ValueContext context) {
        ResourceLocation rootId = ItemIdentity.of(stack);
        if (rootId == null || index == null) {
            return TreeResult.NO_RECIPE;
        }
        List<Recipe<?>> rootRecipes = index.recipesFor(rootId);
        if (rootRecipes.isEmpty()) {
            return TreeResult.NO_RECIPE;
        }

        Object2LongOpenHashMap<ResourceLocation> memo = new Object2LongOpenHashMap<>();
        memo.defaultReturnValue(Long.MIN_VALUE);
        Deque<Frame> frames = new ArrayDeque<>();
        int[] maxDepthSeen = {0};
        boolean[] exact = {true};

        frames.push(newFrame(rootId, rootRecipes, 0, registries));

        while (!frames.isEmpty()) {
            if (context.isTimedOut()) {
                exact[0] = false;
                break;
            }
            Frame top = frames.peek();
            if (top.registries == null && registries != null) {
                top.registries = registries;
            }
            if (top.slots.length == 0 && top.nextSlot == 0) {
                initFrame(index, context, memo, top, exact);
                if (top.pruned) {
                    closeAndFold(frames, top, Long.MIN_VALUE, memo, context);
                    continue;
                }
            }
            if (top.nextSlot < top.slots.length) {
                Slot slot = top.slots[top.nextSlot];
                ResourceLocation nextChild = null;
                for (ResourceLocation alt : slot.alternativeIds) {
                    if (alt == null) {
                        continue;
                    }
                    long known = memo.getLong(alt);
                    if (known != Long.MIN_VALUE) {
                        foldMin(top, slot, known); // memo hit: settle this alternative immediately
                    } else {
                        nextChild = alt;
                        break;
                    }
                }
                if (nextChild == null) {
                    top.nextSlot++; // every alternative resolved from memo
                    continue;
                }
                frames.push(newFrame(nextChild, index.recipesFor(nextChild), top.depth + 1, top.registries));
                continue;
            }
            // Cursor exhausted: sum settled slot minima into per-recipe totals, then complete.
            maxDepthSeen[0] = Math.max(maxDepthSeen[0], top.depth);
            for (int r = 0; r < top.recipes.size(); r++) {
                if (top.recipeCosts[r] == Long.MAX_VALUE) {
                    continue;
                }
                long total = 0L;
                long[] bests = top.slotBest[r];
                for (int i = 0; i < top.slotCount[r]; i++) {
                    long v = bests == null ? Long.MAX_VALUE : bests[i];
                    if (v == Long.MAX_VALUE) {
                        total = Long.MAX_VALUE; // unresolved alternative poisons this recipe
                        break;
                    }
                    total = MathUtil.addSaturating(total, v);
                }
                top.recipeCosts[r] = total;
            }
            long cost = complete(top);
            closeAndFold(frames, top, cost, memo, context);
        }

        long rootCost = memo.getLong(rootId);
        boolean rootExact = rootCost != Long.MIN_VALUE && exact[0];
        if (rootCost == Long.MIN_VALUE) {
            rootCost = 0;
        }
        return new TreeResult(Math.max(0L, rootCost), rootExact, true, maxDepthSeen[0]);
    }

    private static Frame newFrame(ResourceLocation id, List<Recipe<?>> recipes, int depth,
                                  HolderLookup.Provider registries) {
        Frame f = new Frame(id, recipes, depth);
        f.registries = registries;
        return f;
    }

    /** First touch of a frame: budgets, cycle claim, memo hit, slot enumeration. */
    private static void initFrame(RecipeIndex index, ValueContext context,
                                  Object2LongOpenHashMap<ResourceLocation> memo, Frame top, boolean[] exact) {
        try {
            context.incrementNodes();
        } catch (ValueContext.LimitExceededException limit) {
            Upgrader.LOGGER.debug("Recipe walk truncated: {}", limit.getMessage());
            exact[0] = false;
            top.pruned = true;
            return;
        }
        top.pathClaimed = CycleDetector.enter(context, top.id);
        if (!top.pathClaimed) {
            top.pruned = true; // cycle branch contributes nothing
            return;
        }
        if (top.depth >= context.maxDepth()) {
            exact[0] = false;
            top.pruned = true;
            return;
        }
        if (memo.getLong(top.id) != Long.MIN_VALUE) {
            top.pruned = true;
            return;
        }
        if (top.recipes.isEmpty()) {
            exact[0] = false; // no recipe: unknown from the recipe angle
            top.pruned = true;
            return;
        }
        enumerateSlots(top, exact);
        if (top.slots.length == 0) {
            // Recipes exist but need nothing (rare): cost is just the output-normalised zero.
            exact[0] = false;
        }
    }

    /** Builds one slot list per usable recipe; alternatives share a slot and fold their min. */
    private static void enumerateSlots(Frame top, boolean[] exact) {
        java.util.List<Slot> out = new java.util.ArrayList<>();
        for (int r = 0; r < top.recipes.size(); r++) {
            Recipe<?> recipe = top.recipes.get(r);
            List<Ingredient> ingredients;
            try {
                ingredients = recipe.getIngredients();
            } catch (Throwable t) {
                Upgrader.LOGGER.debug("Recipe {} unreadable: {}", safeId(recipe), t.toString());
                top.recipeCosts[r] = Long.MAX_VALUE; // poisoned: never chosen by complete()
                continue;
            }
            int realSlots = 0;
            for (Ingredient ing : ingredients) {
                if (ing == null || ing.isEmpty()) {
                    continue;
                }
                ItemStack[] alternatives;
                try {
                    alternatives = ing.getItems();
                } catch (Throwable t) {
                    top.recipeCosts[r] = Long.MAX_VALUE;
                    exact[0] = false;
                    realSlots = -1;
                    break;
                }
                if (alternatives.length == 0) {
                    continue; // unsatisfiable ingredient contributes nothing
                }
                boolean selfReferencing = false;
                for (ItemStack alt : alternatives) {
                    ResourceLocation altId = ItemIdentity.of(alt);
                    if (altId == null) {
                        continue;
                    }
                    if (altId.equals(top.id)) {
                        selfReferencing = true; // bucket-style container: ignore own cost
                    } else {
                        selfReferencing = false;
                    }
                }
                if (!selfReferencing) {
                    out.add(new Slot(top, r, realSlots, alternatives));
                    realSlots++;
                }
            }
            top.slotCount[r] = Math.max(0, realSlots);
        }
        top.slots = out.toArray(new Slot[0]);
    }

    private static long complete(Frame top) {
        long best = Long.MAX_VALUE;
        for (int r = 0; r < top.recipes.size(); r++) {
            long total = top.recipeCosts[r];
            if (total == Long.MAX_VALUE) {
                continue; // poisoned recipe alternative
            }
            long outputs = outputCountOf(top.recipes.get(r), top.registries);
            best = Math.min(best, dividePerUnit(total, outputs));
        }
        return best == Long.MAX_VALUE ? 0L : best;
    }

    private static void closeAndFold(Deque<Frame> frames, Frame done, long costOrSkip,
                                     Object2LongOpenHashMap<ResourceLocation> memo, ValueContext context) {
        frames.pop();
        if (done.pathClaimed) {
            CycleDetector.leave(context, done.id);
            done.pathClaimed = false;
        }
        if (costOrSkip == Long.MIN_VALUE) {
            long memoized = memo.getLong(done.id);
            if (memoized == Long.MIN_VALUE) {
                return; // pruned branch: contributes nothing anywhere
            }
            costOrSkip = memoized;
        } else {
            memo.put(done.id, costOrSkip);
        }
        Frame parent = frames.peek();
        if (parent == null) {
            return;
        }
        // Fold into the slot that opened this child (parent cursor still points at it).
        Slot binding = parent.slots[parent.nextSlot];
        foldMin(parent, binding, costOrSkip);
        // Advance parent cursor once every alternative of the slot has reported.
        if (allAlternativesSettled(parent, binding)) {
            parent.nextSlot++;
        }
    }

    /** Folds one resolved alternative cost into its slot; the cheapest alternative wins. */
    private static void foldMin(Frame owner, Slot slot, long childCost) {
        long[] bests = owner.slotBest[slot.recipeIndex];
        if (bests == null || bests.length < owner.slotCount[slot.recipeIndex]) {
            bests = new long[Math.max(1, owner.slotCount[slot.recipeIndex])];
            java.util.Arrays.fill(bests, Long.MAX_VALUE);
            owner.slotBest[slot.recipeIndex] = bests;
        }
        bests[slot.slotIndex] = Math.min(bests[slot.slotIndex], childCost);
    }

    private static boolean allAlternativesSettled(Frame owner, Slot slot) {
        long[] bests = owner.slotBest[slot.recipeIndex];
        if (bests == null) {
            return false;
        }
        for (long b : bests) {
            if (b == Long.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private static long outputCountOf(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            ItemStack result = recipe.getResultItem(registries);
            return result == null ? 1L : Math.max(1L, result.getCount());
        } catch (Throwable t) {
            return 1L;
        }
    }

    private static long dividePerUnit(long total, long outputs) {
        if (outputs <= 1) {
            return total;
        }
        return (total + outputs / 2) / outputs; // round half up
    }

    private static String safeId(Recipe<?> recipe) {
        try {
            ResourceLocation id = recipe.getId();
            return id == null ? "?" : id.toString();
        } catch (Throwable t) {
            return "?";
        }
    }
}
