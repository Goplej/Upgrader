package com.example.upgradermod.logic;

import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable recursion context handed to every {@link ValueProvider}.
 *
 * <p>It carries three things:</p>
 * <ul>
 *     <li><b>visited</b> &ndash; items already on the current recursion path, the protection against
 *     circular recipes required by the specification;</li>
 *     <li><b>depth</b> &ndash; recipe recursion depth, used for the depth multiplier table;</li>
 *     <li><b>disabled</b> &ndash; provider names that must be skipped, used by the analogy provider
 *     so that pricing a vanilla counterpart cannot recurse back into the analogy provider.</li>
 * </ul>
 */
public final class ValueContext {

    private static final ValueContext ROOT = new ValueContext(Collections.emptySet(), 0, Collections.emptySet());

    private final Set<Item> visited;
    private final int depth;
    private final Set<String> disabled;

    private ValueContext(Set<Item> visited, int depth, Set<String> disabled) {
        this.visited = visited;
        this.depth = depth;
        this.disabled = disabled;
    }

    /** @return the context used for a top level price lookup */
    public static ValueContext root() {
        return ROOT;
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
        return new ValueContext(Collections.unmodifiableSet(nextVisited), this.depth + 1, this.disabled);
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
        return new ValueContext(this.visited, this.depth, Collections.unmodifiableSet(nextDisabled));
    }

    /**
     * @param item item to test
     * @return {@code true} when the item is already on the current recursion path
     */
    public boolean isVisited(Item item) {
        return item != null && this.visited.contains(item);
    }

    /**
     * @param providerName provider name to test
     * @return {@code true} when the provider must be skipped in this context
     */
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
