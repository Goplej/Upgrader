package com.example.upgradermod.logic;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable recursion context handed to every {@link ValueProvider}.
 *
 * <p>It carries the current level, the visited item set that breaks circular recipes, the recipe
 * depth, and provider names temporarily disabled for analogy lookups.</p>
 */
public final class ValueContext {

    private static final ValueContext ROOT = new ValueContext(null, Collections.emptySet(), 0, Collections.emptySet());

    @Nullable
    private final Level level;
    private final Set<Item> visited;
    private final int depth;
    private final Set<String> disabled;

    private ValueContext(@Nullable Level level, Set<Item> visited, int depth, Set<String> disabled) {
        this.level = level;
        this.visited = visited;
        this.depth = depth;
        this.disabled = disabled;
    }

    /** @return the context used for a top level price lookup without a level */
    public static ValueContext root() {
        return ROOT;
    }

    /**
     * Creates a top-level context for a particular world.
     *
     * @param level world used by recipe providers, may be {@code null}
     * @return a new root context
     */
    public static ValueContext root(@Nullable Level level) {
        return level == null ? ROOT : new ValueContext(level, Collections.emptySet(), 0, Collections.emptySet());
    }

    /** @return the world associated with this calculation, or {@code null} */
    @Nullable
    public Level getLevel() {
        return this.level;
    }

    /**
     * Creates the context for one level deeper recursion.
     *
     * @param item item that is currently being priced and therefore has to be marked as visited
     * @return a child context with {@code depth + 1}
     */
    public ValueContext descend(Item item) {
        Set<Item> nextVisited = new HashSet<>(this.visited);
        if (item != null) {
            nextVisited.add(item);
        }
        return new ValueContext(this.level, Collections.unmodifiableSet(nextVisited), this.depth + 1, this.disabled);
    }

    /**
     * Creates a context in which one provider is switched off.
     *
     * @param providerName name of the provider to skip
     * @return a context with the provider disabled
     */
    public ValueContext disabling(String providerName) {
        if (providerName == null) {
            return this;
        }
        Set<String> nextDisabled = new HashSet<>(this.disabled);
        nextDisabled.add(providerName);
        return new ValueContext(this.level, this.visited, this.depth, Collections.unmodifiableSet(nextDisabled));
    }

    /** @return {@code true} when the item is already on the current recursion path */
    public boolean isVisited(Item item) {
        return item != null && this.visited.contains(item);
    }

    /** @return {@code true} when the provider must be skipped in this context */
    public boolean isDisabled(String providerName) {
        return providerName != null && this.disabled.contains(providerName);
    }

    /** @return the current recipe recursion depth */
    public int getDepth() {
        return this.depth;
    }

    /** @return an unmodifiable view of the visited items */
    public Set<Item> getVisited() {
        return this.visited;
    }
}
