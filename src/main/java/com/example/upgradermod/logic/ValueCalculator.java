package com.example.upgradermod.logic;

import com.example.upgradermod.UpgraderConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * The value calculation pipeline (Section 1.1).
 *
 * <p>Runs every registered {@link ValueProvider} in descending priority order and returns the first
 * strictly positive result, clamped to {@code [1, MAX_PRICE]}. When no provider answers, the
 * {@link UpgraderConstants#FALLBACK_PRICE fallback price} is used.</p>
 *
 * <p>Every provider call and the whole public entry point are wrapped in
 * {@code try-catch(Throwable)}: a broken provider (a modded item throwing from
 * {@code getMaxDamage}, a recipe graph blowing up, ...) degrades the value, it never crashes the
 * server.</p>
 */
public final class ValueCalculator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Prices a stack, using the shared cache for plain (tag free) stacks.
     *
     * @param stack stack to price, {@code null} and empty stacks are worth zero
     * @return a value in {@code [1, MAX_PRICE]}, or {@code 0} for an empty stack
     */
    public static long calculate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }

        try {
            boolean cacheable = !stack.hasTag();
            if (cacheable) {
                Long cached = ItemRegistryCache.getCachedValue(stack.getItem());
                if (cached != null) {
                    return cached;
                }
            }

            long value = clamp(calculate(stack, ValueContext.root()));

            if (cacheable) {
                ItemRegistryCache.putCachedValue(stack.getItem(), value);
            }
            return value;
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader value calculation failed for {}, using the fallback price", describe(stack), throwable);
            return UpgraderConstants.FALLBACK_PRICE;
        }
    }

    /**
     * Prices a stack inside an ongoing recursion. Results are not cached here because they depend on
     * the visited set and the depth.
     *
     * @param stack   stack to price
     * @param context recursion context
     * @return the first positive provider result, or the fallback price
     */
    public static long calculate(ItemStack stack, ValueContext context) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        if (context == null) {
            context = ValueContext.root();
        }

        for (ValueProvider provider : ValueProviderRegistry.getProviders()) {
            if (context.isDisabled(provider.getName())) {
                continue;
            }

            long value;
            try {
                value = provider.getValue(stack, context);
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader value provider '{}' threw while pricing {}, falling through to the next provider",
                        provider.getName(), describe(stack), throwable);
                continue;
            }

            if (value > 0L) {
                return Math.min(value, UpgraderConstants.MAX_PRICE);
            }
        }

        return UpgraderConstants.FALLBACK_PRICE;
    }

    /** Drops every cached value, e.g. after a datapack reload or a config change. */
    public static void clearCache() {
        ItemRegistryCache.clearValueCache();
    }

    /**
     * Clamps a raw value into the supported range.
     *
     * @param value raw value
     * @return a value in {@code [1, MAX_PRICE]}
     */
    public static long clamp(long value) {
        if (value <= 0L) {
            return 1L;
        }
        return Math.min(value, UpgraderConstants.MAX_PRICE);
    }

    /**
     * Small, exception safe description used in log messages.
     *
     * @param stack stack to describe
     * @return {@code namespace:path} or {@code <empty>}
     */
    public static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }
        try {
            ResourceLocation id = ItemRegistryCache.id(stack.getItem());
            return id == null ? stack.getItem().toString() : id.toString();
        } catch (Throwable throwable) {
            return "<unknown>";
        }
    }

    private ValueCalculator() {
        // Static access only.
    }
}
