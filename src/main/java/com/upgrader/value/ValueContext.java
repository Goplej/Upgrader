package com.upgrader.value;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable per-calculation state threaded through the whole value pipeline.
 *
 * <p>One instance exists per top-level engine call and is shared by every source and recipe-tree
 * node visited during that call. It owns depth/node/time budget enforcement, cycle detection,
 * subtree memoization and anti-double-counting bookkeeping.</p>
 *
 * <p><strong>Not thread-safe by design</strong>: a context belongs to exactly one calculation,
 * which always runs on a single thread.</p>
 */
public final class ValueContext {

    /** Signals an exhausted budget (node cap); caught internally, never escapes the engine. */
    public static final class LimitExceededException extends RuntimeException {
        public LimitExceededException(String message) {
            super(message);
        }
    }

    private HolderLookup.Provider registries;
    private Level level;
    private Object recipeIndex; // com.upgrader.recipe.RecipeIndex, kept generic to avoid cycles
    private final long startedAtNanos;
    private final long softTimeoutNanos;
    private final int maxDepth;
    private final int maxNodes;

    private int nodesProcessed;
    private boolean cycleDetected;
    private boolean timedOut;
    private boolean nodesExhausted;
    private int depthReached;

    private final Deque<ResourceLocation> path = new ArrayDeque<>();
    private final Set<ResourceLocation> onPath = new HashSet<>();
    private final Map<ResourceLocation, ItemValue> memo = new HashMap<>();
    private final EnumSet<SourceType> usedSources = EnumSet.noneOf(SourceType.class);

    public ValueContext(HolderLookup.Provider registries, long softTimeoutNanos, int maxDepth, int maxNodes) {
        this.registries = registries;
        this.softTimeoutNanos = Math.max(1L, softTimeoutNanos);
        this.startedAtNanos = System.nanoTime();
        this.maxDepth = Math.max(1, maxDepth);
        this.maxNodes = Math.max(1, maxNodes);
    }

    /** Attaches world context once per calculation (level + pre-baked recipe index). */
    public void attachLevel(Level level, Object index) {
        this.level = level;
        this.recipeIndex = index;
        if (level != null && registries == null) {
            this.registries = level.registryAccess();
        }
    }

    public HolderLookup.Provider registries() {
        return registries;
    }

    public Level level() {
        return level;
    }

    /** Pre-baked {@link com.upgrader.recipe.RecipeIndex} for this calculation, or null. */
    public com.upgrader.recipe.RecipeIndex recipeIndex() {
        return (com.upgrader.recipe.RecipeIndex) recipeIndex;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int nodesProcessed() {
        return nodesProcessed;
    }

    public int depthReached() {
        return depthReached;
    }

    public void recordDepth(int depth) {
        depthReached = Math.max(depthReached, depth);
    }

    public boolean cycleDetected() {
        return cycleDetected;
    }

    public void markCycleDetected() {
        this.cycleDetected = true;
    }

    public boolean timedOut() {
        return timedOut;
    }

    public boolean nodesExhausted() {
        return nodesExhausted;
    }

    /** True once elapsed wall time passed the configured soft budget (also latches the flag). */
    public boolean isTimedOut() {
        if (!timedOut && System.nanoTime() - startedAtNanos >= softTimeoutNanos) {
            timedOut = true;
        }
        return timedOut;
    }

    /** Counts one visited tree node; throws when the node budget runs out. */
    public void incrementNodes() {
        if (++nodesProcessed > maxNodes) {
            nodesExhausted = true;
            throw new LimitExceededException("maxNodesPerCalculation=" + maxNodes);
        }
    }

    /** Marks {@code id} visited on the current branch; false means it was already there (cycle). */
    public boolean visit(ResourceLocation id) {
        if (!onPath.add(id)) {
            cycleDetected = true;
            return false;
        }
        path.push(id);
        return true;
    }

    /** Removes {@code id} from the current branch; must pair with a successful {@link #visit}. */
    public void unvisit(ResourceLocation id) {
        ResourceLocation top = path.peek();
        if (top != null && top.equals(id)) {
            path.pop();
            onPath.remove(id);
        } else {
            onPath.remove(id); // defensive cleanup on mismatched pairing
        }
    }

    public ItemValue memoGet(ResourceLocation id) {
        return memo.get(id);
    }

    public void memoPut(ResourceLocation id, ItemValue value) {
        memo.put(id, value);
    }

    public void markSourceUsed(SourceType type) {
        usedSources.add(type);
    }

    public boolean isSourceUsed(SourceType type) {
        return usedSources.contains(type);
    }

    public Set<SourceType> usedSources() {
        return Set.copyOf(usedSources);
    }
}
