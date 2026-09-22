package com.example.upgradermod;

import net.minecraft.resources.ResourceLocation;

/**
 * Section 0 (GLOBAL_CONSTANTS) of the Upgrader specification.
 *
 * <p>Every tunable number of the mod lives here so that the engine classes never carry magic
 * literals.</p>
 */
public final class UpgraderConstants {

    /** Unique mod identifier, also the resource namespace. */
    public static final String MOD_ID = "upgradermod";

    /** Root package of the mod. */
    public static final String ROOT_PACKAGE = "com.example.upgradermod";

    /** Author declared in {@code mods.toml}. */
    public static final String AUTHOR = "Popipok";

    /** Value returned when no {@code ValueProvider} was able to price an item. */
    public static final long FALLBACK_PRICE = 10L;

    /** Hard ceiling applied to every computed value. */
    public static final long MAX_PRICE = 10_000_000_000_000L;

    /** Maximum recipe recursion depth of the recipe based value provider. */
    public static final int MAX_RECIPE_DEPTH = 10;

    /** Input values above this threshold are written to {@code logs/upgradermod_suspicious.log}. */
    public static final long LOG_THRESHOLD = 1_000_000_000L;

    /** Network channel name: {@code upgradermod:main}. */
    public static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "main");

    /** Protocol version negotiated with the remote side. */
    public static final String PROTOCOL_VERSION = "1";

    /**
     * Recipe depth multipliers, index == recursion depth. Index 0 is the item itself, index 1 the
     * direct ingredients, and so on up to {@link #MAX_RECIPE_DEPTH}.
     */
    public static final double[] DEPTH_MULTIPLIERS = {
            1.0D, 1.0D, 1.2D, 1.5D, 2.0D, 2.5D, 3.0D, 3.5D, 4.0D, 4.5D, 5.0D
    };

    /** Lower bound of the spin chance (percent). */
    public static final double MIN_CHANCE = 1.0E-21D;

    /** Upper bound of the spin chance (percent). */
    public static final double MAX_CHANCE = 90.0D;

    /** Smallest accepted bet multiplier. */
    public static final int MIN_MULTIPLIER = 1;

    /** Largest accepted bet multiplier. */
    public static final int MAX_MULTIPLIER = 64;

    /** Folder below {@code config/} that holds the JSON tables. */
    public static final String CONFIG_FOLDER = "upgradermod";

    /** Item value override table. */
    public static final String OVERRIDES_FILE = "overrides.json";

    /** Tag value table. */
    public static final String TAGS_FILE = "tags.json";

    /** Suspicious spin audit log, relative to the game directory. */
    public static final String SUSPICIOUS_LOG_FILE = "logs/upgradermod_suspicious.log";

    /**
     * Depth multiplier for the given recipe recursion depth, clamped to the table bounds.
     *
     * @param depth recipe recursion depth, {@code 0} for the item itself
     * @return the multiplier that is applied to the summed ingredient values
     */
    public static double depthMultiplier(int depth) {
        if (depth <= 0) {
            return DEPTH_MULTIPLIERS[0];
        }
        if (depth >= DEPTH_MULTIPLIERS.length) {
            return DEPTH_MULTIPLIERS[DEPTH_MULTIPLIERS.length - 1];
        }
        return DEPTH_MULTIPLIERS[depth];
    }

    private UpgraderConstants() {
        // Constant holder.
    }
}
