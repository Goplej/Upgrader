package com.upgrader.api;

import com.upgrader.Upgrader;
import com.upgrader.cache.ValueCache;
import com.upgrader.integration.IntegrationManager;
import com.upgrader.integration.IntegrationProvider;
import com.upgrader.value.ItemValue;
import com.upgrader.value.OverrideResolver;
import com.upgrader.value.ValueEngine;
import com.upgrader.value.ValueSourceRegistry;
import com.upgrader.value.source.RecipeValueSource;
import com.upgrader.value.source.ValueSource;
import net.minecraft.world.item.ItemStack;

/**
 * Public, stable entry point of the Upgrader value engine.
 *
 * <h2>For addon mods</h2>
 * <ul>
 *   <li>{@link #registerValueSource(ValueSource)} — join the value chain with your own evidence.</li>
 *   <li>{@link #registerIntegration(IntegrationProvider)} — contribute an optional-mod adapter.</li>
 *   <li>{@link #getItemValue(ItemStack)} — ask the engine what an item is worth (never throws,
 *       never returns null; unknown items honestly report {@link ItemValue#isKnown()} false).</li>
 * </ul>
 *
 * <h2>Lifecycle contract</h2>
 * Registration methods are safe from {@code FMLCommonSetupEvent} and later. The singleton engine
 * is created lazily on first access so class-loading order between mods never matters. Breaking
 * signature changes are impossible without a prior {@link Deprecated} release per semver policy.
 */
public final class UpgraderAPI {

    /** API version advertised to consumers; bumped on every additive release. */
    public static final int API_VERSION = 1;

    private static volatile ValueEngine engine;
    private static volatile OverrideResolver overrideResolver;
    private static boolean loaded;

    private UpgraderAPI() {
    }

    /** Called once by the mod constructor; purely informational for load-order debugging. */
    public static void markLoaded() {
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** Lazily creates the shared engine (cache + overrides + source-chain aware). */
    private static ValueEngine ensureEngine() {
        ValueEngine local = engine;
        if (local == null) {
            synchronized (UpgraderAPI.class) {
                local = engine;
                if (local == null) {
                    overrideResolver = new OverrideResolver();
                    overrideResolver.reload();
                    local = new ValueEngine(new ValueCache(), overrideResolver);
                    engine = local;
                }
            }
        }
        return local;
    }

    /** The shared, thread-safe value engine. */
    public static ValueEngine getValueEngine() {
        return ensureEngine();
    }

    /** The active override resolver (config {@code [overrides]} section). */
    public static OverrideResolver getOverrideResolver() {
        ensureEngine();
        return overrideResolver;
    }

    /** Computes (or reads from cache) the full value record for one stack. Never throws. */
    public static ItemValue getItemValue(ItemStack stack) {
        try {
            return ensureEngine().computeValue(stack);
        } catch (Throwable t) {
            Upgrader.LOGGER.error("Value computation failed; reporting UNKNOWN", t);
            return ItemValue.unknown(new net.minecraft.resources.ResourceLocation("minecraft", "air"),
                    "engine failure: " + t.getClass().getSimpleName());
        }
    }

    /** Adds a custom evidence source to the chain; takes effect immediately. */
    public static void registerValueSource(ValueSource source) {
        ValueSourceRegistry.get().register(source);
    }

    /** Registers an optional-dependency integration; init happens via {@link IntegrationManager}. */
    public static void registerIntegration(IntegrationProvider provider) {
        IntegrationManager.register(provider);
    }

    /**
     * Registers the built-in sources shipped with Upgrader. Called exactly once from common
     * setup; third-party registrations should NOT call this.
     */
    public static void bootstrapDefaults() {
        var registry = ValueSourceRegistry.get();
        if (registry.size() > 0) {
            return; // idempotent guard (e.g. duplicated event delivery in tests)
        }
        registry.register(new RecipeValueSource());
        registry.register(new com.upgrader.value.source.RecursiveComponentValueSource());
        registry.register(new com.upgrader.value.source.TagAndMaterialValueSource());
        registry.register(new com.upgrader.value.source.ItemPropertyValueSource());
        registry.register(new com.upgrader.value.source.NBTComplexityValueSource());
        registry.register(new com.upgrader.value.source.FallbackHeuristicValueSource());
        Upgrader.LOGGER.info("Built-in value chain registered: {} sources", registry.size());
    }

    /** Drops all cached values and re-reads the override tables (wired to /upgrader reload). */
    public static void reloadRuntime() {
        ensureEngine().invalidateAll();
        RecipeValueSourceIndexHelper.dropRecipeIndex();
        overrideResolver.reload();
        Upgrader.LOGGER.info("Upgrader runtime reloaded ({} overrides active)", overrideResolver.configuredCount());
    }

    /** Tiny indirection so this file does not hard-depend on the mutable index holder layout. */
    private static final class RecipeValueSourceIndexHelper {
        static void dropRecipeIndex() {
            for (ValueSource s : ValueSourceRegistry.get().ordered()) {
                if (s instanceof RecipeValueSource rvs) {
                    rvs.invalidateIndex();
                }
            }
        }
    }
}
