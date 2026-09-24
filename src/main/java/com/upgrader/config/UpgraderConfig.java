package com.upgrader.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * Typed accessors over the two {@link ForgeConfigSpec}s shipped by Upgrader.
 *
 * <p>COMMON holds every engine tuning knob (recursion limits, cache sizing, upgrade curve,
 * value overrides). CLIENT holds pure presentation options. All getters are safe to call from
 * any thread; values refresh automatically when Forge reloads the config files.</p>
 */
public final class UpgraderConfig {

    /** COMMON spec: engine limits, cache sizing, upgrade curve and manual overrides. */
    public static final class Common {
        public static final ForgeConfigSpec SPEC;

        public static final ForgeConfigSpec.IntValue MAX_RECURSION_DEPTH;
        public static final ForgeConfigSpec.IntValue MAX_NODES_PER_CALCULATION;
        public static final ForgeConfigSpec.IntValue SOFT_TIMEOUT_MILLIS;
        public static final ForgeConfigSpec.IntValue MAX_CACHE_SIZE;
        public static final ForgeConfigSpec.IntValue CACHE_TTL_MINUTES;
        public static final ForgeConfigSpec.DoubleValue GROWTH_FACTOR;
        public static final ForgeConfigSpec.DoubleValue DIMINISHING_FACTOR;
        public static final ForgeConfigSpec.IntValue MAX_UPGRADE_LEVEL;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES_EXACT;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES_TAGS;

        static {
            ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

            b.comment("Value engine analysis limits.").push("engine");
            MAX_RECURSION_DEPTH = b
                    .comment("Maximum depth of the recipe dependency tree (1..32).",
                            "Lower values truncate exotic chains (Avaritia-like) early but keep calculations fast;",
                            "higher values risk long server ticks even with the soft timeout in place.")
                    .defineInRange("maxRecursionDepth", 12, 1, 32);
            MAX_NODES_PER_CALCULATION = b
                    .comment("Hard cap on recipe-tree nodes visited per single value calculation (100..100000).",
                            "Protects against pathological ingredient fan-out. Exceeding it degrades confidence to LOW.")
                    .defineInRange("maxNodesPerCalculation", 5000, 100, 100000);
            SOFT_TIMEOUT_MILLIS = b
                    .comment("Soft timeout in milliseconds for one value calculation (5..5000).",
                            "When exceeded, partial results are returned with degraded confidence.",
                            "Keep well below 50 ms if calculations ever run inline on the server tick thread.")
                    .defineInRange("softTimeoutMillis", 50, 5, 5000);
            b.pop();

            b.comment("Result cache settings.").push("cache");
            MAX_CACHE_SIZE = b
                    .comment("Maximum number of cached item values (100..1000000). A warning logs at 90% occupancy.")
                    .defineInRange("maxCacheSize", 10000, 100, 1000000);
            CACHE_TTL_MINUTES = b
                    .comment("Cache entry time-to-live in minutes (1..1440). Entries also drop on recipe/tag reloads.")
                    .defineInRange("cacheTtlMinutes", 10, 1, 1440);
            b.pop();

            b.comment("Upgrade cost curve: cost(level) = base * growth^level / (1 + diminishing*level).").push("upgrade");
            GROWTH_FACTOR = b
                    .comment("Exponential growth factor per upgrade level (1.01..10.0). 2.5 means each level costs ~2.5x more.")
                    .defineInRange("growthFactor", 2.5D, 1.01D, 10.0D);
            DIMINISHING_FACTOR = b
                    .comment("Divisor damping term (0..1). 0 disables damping; 0.05 slightly flattens late-level costs.")
                    .defineInRange("diminishingFactor", 0.05D, 0.0D, 1.0D);
            MAX_UPGRADE_LEVEL = b
                    .comment("Highest reachable upgrade level. 0 means unlimited (bounded only by long arithmetic).")
                    .defineInRange("maxUpgradeLevel", 0, 0, 10000);
            b.pop();

            b.comment("Manual value overrides. EMPTY BY DEFAULT - the engine must compute values dynamically.",
                    "Format: 'namespace:item=value' or 'tag:namespace/path=value'. Exact beats tag.").push("overrides");
            OVERRIDES_EXACT = b
                    .comment("Exact item overrides, e.g. [\"minecraft:nether_star=100000\"]. Use sparingly.")
                    .defineListAllowEmpty(List.of("exact"), List::of, OverrideEntry::isValid);
            OVERRIDES_TAGS = b
                    .comment("Tag-based overrides, e.g. [\"tag:forge:ingots/osmium=64\"]. Applies to every member of the tag.")
                    .defineListAllowEmpty(List.of("tags"), List::of, OverrideEntry::isValid);
            b.pop();

            SPEC = b.build();
        }

        private Common() {
        }
    }

    /** CLIENT spec: presentation-only options. */
    public static final class Client {
        public static final ForgeConfigSpec SPEC;
        public static final ForgeConfigSpec.BooleanValue SHOW_BREAKDOWN_ON_SHIFT;
        public static final ForgeConfigSpec.IntValue BREAKDOWN_SCROLL_SPEED;

        static {
            ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
            SHOW_BREAKDOWN_ON_SHIFT = b
                    .comment("Hold SHIFT over an item to show its computed value breakdown tooltip.")
                    .define("showBreakdownOnShift", true);
            BREAKDOWN_SCROLL_SPEED = b
                    .comment("Scroll wheel steps in the breakdown screen (1..100).")
                    .defineInRange("breakdownScrollSpeed", 20, 1, 100);
            SPEC = b.build();
        }

        private Client() {
        }
    }

    /** Registers both specs with Forge. Call once from the mod constructor. */
    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, Common.SPEC, "upgrader-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, Client.SPEC, "upgrader-client.toml");
    }

    public static int maxRecursionDepth() {
        return Common.MAX_RECURSION_DEPTH.get();
    }

    public static int maxNodesPerCalculation() {
        return Common.MAX_NODES_PER_CALCULATION.get();
    }

    public static long softTimeoutNanos() {
        return Common.SOFT_TIMEOUT_MILLIS.get() * 1_000_000L;
    }

    public static int maxCacheSize() {
        return Common.MAX_CACHE_SIZE.get();
    }

    public static long cacheTtlMinutes() {
        return Common.CACHE_TTL_MINUTES.get();
    }

    public static double growthFactor() {
        return Common.GROWTH_FACTOR.get();
    }

    public static double diminishingFactor() {
        return Common.DIMINISHING_FACTOR.get();
    }

    public static int maxUpgradeLevel() {
        return Common.MAX_UPGRADE_LEVEL.get();
    }

    public static List<? extends String> overridesExact() {
        return Common.OVERRIDES_EXACT.get();
    }

    public static List<? extends String> overridesTags() {
        return Common.OVERRIDES_TAGS.get();
    }

    public static boolean showBreakdownOnShift() {
        return Client.SHOW_BREAKDOWN_ON_SHIFT.get();
    }

    public static int breakdownScrollSpeed() {
        return Client.BREAKDOWN_SCROLL_SPEED.get();
    }

    private UpgraderConfig() {
    }
}
