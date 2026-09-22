package com.example.upgradermod.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A single step of the value calculation pipeline (Section 1.1).
 *
 * <p>Providers are executed by {@link ValueCalculator} in descending
 * {@link #getPriority() priority} order; the first provider that returns a strictly positive value
 * wins. Implementations must be side effect free and must never throw &ndash; the calculator wraps
 * every call in a {@code try-catch(Throwable)} anyway, but a clean {@link #UNKNOWN} result keeps the
 * pipeline moving to the next provider.</p>
 */
public interface ValueProvider {

    /** Returned when a provider cannot price the given stack. */
    long UNKNOWN = -1L;

    /**
     * Execution priority. Higher runs first.
     *
     * @return the priority of this provider
     */
    int getPriority();

    /**
     * Stable, lowercase identifier used for logging and for temporarily disabling a provider inside
     * {@link ValueContext}.
     *
     * @return the provider name
     */
    String getName();

    /**
     * Prices the given stack.
     *
     * @param stack   stack to price, never {@code null} and never empty
     * @param context recursion context carrying the visited items and the current depth
     * @return a value greater than zero, or {@link #UNKNOWN} when this provider declines
     */
    long getValue(ItemStack stack, ValueContext context);

    /**
     * Public level-aware provider API required by the architecture specification.
     *
     * @param stack item stack to price
     * @param level world supplying recipe data, may be {@code null}
     * @return provider value or {@link #UNKNOWN}
     */
    default long getValue(ItemStack stack, Level level) {
        try {
            return getValue(stack, ValueContext.root(level));
        } catch (Throwable throwable) {
            return UNKNOWN;
        }
    }
}
