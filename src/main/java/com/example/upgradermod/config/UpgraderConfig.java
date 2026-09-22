package com.example.upgradermod.config;

import com.example.upgradermod.UpgraderConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Forge configuration and JSON value tables for the Upgrader.
 *
 * <p>The Forge common config contains gameplay and safety values. The two JSON files are deliberately
 * separate because they are intended to be edited by modpack authors as item-value tables:</p>
 * <ul>
 *     <li>{@code config/upgradermod/overrides.json} maps item ids to exact prices;</li>
 *     <li>{@code config/upgradermod/tags.json} maps item tag ids to prices.</li>
 * </ul>
 *
 * <p>Every public read is defensive. Forge validates configured scalar values, while malformed JSON
 * entries are skipped individually so one bad line cannot stop the mod from loading.</p>
 */
public final class UpgraderConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Forge common configuration specification. */
    public static final ForgeConfigSpec SPEC;

    /** Maximum chance percentage. */
    public static final ForgeConfigSpec.DoubleValue MAX_CHANCE;

    /** Minimum chance percentage. */
    public static final ForgeConfigSpec.DoubleValue MIN_CHANCE;

    /** Heuristic weight for common and uncommon enchantments. */
    public static final ForgeConfigSpec.IntValue ENCHANT_WEIGHT_COMMON;

    /** Heuristic weight for rare enchantments. */
    public static final ForgeConfigSpec.IntValue ENCHANT_WEIGHT_RARE;

    /** Heuristic weight for very rare enchantments. */
    public static final ForgeConfigSpec.IntValue ENCHANT_WEIGHT_LEGENDARY;

    /** Heuristic weight per item attribute modifier. */
    public static final ForgeConfigSpec.IntValue ATTRIBUTE_WEIGHT;

    /** Maximum recursive recipe depth. */
    public static final ForgeConfigSpec.IntValue RECIPE_MAX_DEPTH;

    /** Maximum price returned by recipe analysis. */
    public static final ForgeConfigSpec.LongValue RECIPE_MAX_PRICE;

    /** Whether every successful spin must pay the configured tax. */
    public static final ForgeConfigSpec.BooleanValue TAX_ENABLED;

    /** Item id consumed as the tax. */
    public static final ForgeConfigSpec.ConfigValue<String> TAX_ITEM;

    /** Number of tax items charged per successful spin. */
    public static final ForgeConfigSpec.IntValue TAX_AMOUNT;

    /** Whether creative players may target endgame values. */
    public static final ForgeConfigSpec.BooleanValue ALLOW_CREATIVE_ENDGAME;

    /** Input value above which the spin is audited. */
    public static final ForgeConfigSpec.LongValue LOG_THRESHOLD;

    /** Maximum allowed input-to-target value ratio. */
    public static final ForgeConfigSpec.IntValue MAX_DOWNGRADE_RATIO;

    private static final String DEFAULT_OVERRIDES = "{}\n";
    private static final String DEFAULT_TAGS = "{}\n";

    private static volatile Map<ResourceLocation, Long> overrides = Collections.emptyMap();
    private static volatile Map<ResourceLocation, Long> tagValues = Collections.emptyMap();
    private static volatile boolean loaded;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Upgrader gameplay and safety settings").push("upgrader");

        MAX_CHANCE = builder.comment("Maximum spin chance in percent")
                .defineInRange("maxChance", UpgraderConstants.MAX_CHANCE, 0.000000000000000000001D, 100.0D);
        MIN_CHANCE = builder.comment("Minimum spin chance in percent")
                .defineInRange("minChance", UpgraderConstants.MIN_CHANCE, 0.000000000000000000001D, 100.0D);

        ENCHANT_WEIGHT_COMMON = builder.comment("Value bonus per level of a common or uncommon enchantment")
                .defineInRange("enchantWeightCommon", 30, 0, 1_000_000);
        ENCHANT_WEIGHT_RARE = builder.comment("Value bonus per level of a rare enchantment")
                .defineInRange("enchantWeightRare", 100, 0, 1_000_000);
        ENCHANT_WEIGHT_LEGENDARY = builder.comment("Value bonus per level of a very rare enchantment")
                .defineInRange("enchantWeightLegendary", 200, 0, 1_000_000);
        ATTRIBUTE_WEIGHT = builder.comment("Value bonus per item attribute modifier")
                .defineInRange("attributeWeight", 50, 0, 1_000_000);

        RECIPE_MAX_DEPTH = builder.comment("Maximum recursive recipe depth")
                .defineInRange("recipeMaxDepth", UpgraderConstants.MAX_RECIPE_DEPTH, 0, UpgraderConstants.MAX_RECIPE_DEPTH);
        RECIPE_MAX_PRICE = builder.comment("Maximum price returned by recipe analysis")
                .defineInRange("recipeMaxPrice", UpgraderConstants.MAX_PRICE, 1L, UpgraderConstants.MAX_PRICE);

        TAX_ENABLED = builder.comment("Charge a tax item on successful spins")
                .define("taxEnabled", false);
        TAX_ITEM = builder.comment("Item id used as the spin tax")
                .define("taxItem", "minecraft:diamond");
        TAX_AMOUNT = builder.comment("Number of tax items charged per successful spin")
                .defineInRange("taxAmount", 1, 0, 64);

        ALLOW_CREATIVE_ENDGAME = builder.comment("Allow creative players to target values at or above one million")
                .define("allowCreativeEndgame", false);
        LOG_THRESHOLD = builder.comment("Input value above which a spin is audited")
                .defineInRange("logThreshold", UpgraderConstants.LOG_THRESHOLD, 1L, UpgraderConstants.MAX_PRICE);
        MAX_DOWNGRADE_RATIO = builder.comment("Maximum input-to-target value ratio")
                .defineInRange("maxDowngradeRatio", 100, 1, Integer.MAX_VALUE);

        builder.pop();
        SPEC = builder.build();
    }

    /**
     * Reads both JSON tables from disk and creates empty files when they do not exist.
     *
     * <p>The default files are intentionally empty. The value engine's heuristic provider remains
     * the fallback until a pack author explicitly adds an override or tag value.</p>
     */
    public static synchronized void reload() {
        Map<ResourceLocation, Long> loadedOverrides = overrides;
        Map<ResourceLocation, Long> loadedTags = tagValues;
        try {
            Path directory = configDirectory();
            Files.createDirectories(directory);

            Path overridesFile = directory.resolve(UpgraderConstants.OVERRIDES_FILE);
            Path tagsFile = directory.resolve(UpgraderConstants.TAGS_FILE);
            writeIfAbsent(overridesFile, DEFAULT_OVERRIDES);
            writeIfAbsent(tagsFile, DEFAULT_TAGS);

            loadedOverrides = parseTable(read(overridesFile), overridesFile);
            loadedTags = parseTable(read(tagsFile), tagsFile);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not load its JSON tables from {}, keeping the previous tables",
                    configDirectory(), throwable);
        }

        overrides = Collections.unmodifiableMap(loadedOverrides);
        tagValues = Collections.unmodifiableMap(loadedTags);
        loaded = true;
        LOGGER.info("Upgrader JSON tables loaded: {} item overrides, {} tag values",
                overrides.size(), tagValues.size());
    }

    /** @return the directory that holds the JSON tables */
    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(UpgraderConstants.CONFIG_FOLDER);
    }

    /** @return configured item override, or {@code -1} when absent */
    public static long getOverride(ResourceLocation id) {
        if (id == null) {
            return -1L;
        }
        Long value = overrides.get(id);
        return value == null ? -1L : value;
    }

    /** @return configured tag value, or {@code -1} when absent */
    public static long getTagValue(ResourceLocation tag) {
        if (tag == null) {
            return -1L;
        }
        Long value = tagValues.get(tag);
        return value == null ? -1L : value;
    }

    /** @return validated maximum chance percentage */
    public static double maxChance() {
        return readDouble(MAX_CHANCE, UpgraderConstants.MAX_CHANCE);
    }

    /** @return validated minimum chance percentage */
    public static double minChance() {
        return Math.min(readDouble(MIN_CHANCE, UpgraderConstants.MIN_CHANCE), maxChance());
    }

    /** @return configured common-enchantment weight */
    public static int enchantWeightCommon() {
        return readInt(ENCHANT_WEIGHT_COMMON, 30);
    }

    /** @return configured rare-enchantment weight */
    public static int enchantWeightRare() {
        return readInt(ENCHANT_WEIGHT_RARE, 100);
    }

    /** @return configured very-rare-enchantment weight */
    public static int enchantWeightLegendary() {
        return readInt(ENCHANT_WEIGHT_LEGENDARY, 200);
    }

    /** @return configured attribute modifier weight */
    public static int attributeWeight() {
        return readInt(ATTRIBUTE_WEIGHT, 50);
    }

    /** @return validated recipe depth, never above the hard safety limit */
    public static int recipeMaxDepth() {
        return Math.min(readInt(RECIPE_MAX_DEPTH, UpgraderConstants.MAX_RECIPE_DEPTH),
                UpgraderConstants.MAX_RECIPE_DEPTH);
    }

    /** @return validated recipe price ceiling */
    public static long recipeMaxPrice() {
        return Math.min(readLong(RECIPE_MAX_PRICE, UpgraderConstants.MAX_PRICE), UpgraderConstants.MAX_PRICE);
    }

    /** @return whether tax is enabled */
    public static boolean taxEnabled() {
        return readBoolean(TAX_ENABLED, false);
    }

    /** @return configured tax item id, or the safe diamond default */
    public static String taxItemId() {
        try {
            String value = TAX_ITEM.get();
            return value == null || value.isBlank() ? "minecraft:diamond" : value;
        } catch (Throwable throwable) {
            LOGGER.warn("Upgrader taxItem config could not be read, using minecraft:diamond", throwable);
            return "minecraft:diamond";
        }
    }

    /** @return validated tax amount */
    public static int taxAmount() {
        return readInt(TAX_AMOUNT, 1);
    }

    /** @return whether creative endgame restrictions are disabled */
    public static boolean allowCreativeEndgame() {
        return readBoolean(ALLOW_CREATIVE_ENDGAME, false);
    }

    /** @return validated suspicious-log threshold */
    public static long logThreshold() {
        return readLong(LOG_THRESHOLD, UpgraderConstants.LOG_THRESHOLD);
    }

    /** @return validated maximum input-to-target ratio */
    public static int maxDowngradeRatio() {
        return Math.max(1, readInt(MAX_DOWNGRADE_RATIO, 100));
    }

    /** @return whether the JSON tables have been loaded at least once */
    public static boolean isLoaded() {
        return loaded;
    }

    /** @return number of configured item overrides */
    public static int overrideCount() {
        return overrides.size();
    }

    /** @return number of configured tag values */
    public static int tagValueCount() {
        return tagValues.size();
    }

    private static double readDouble(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            double result = value.get();
            return Double.isFinite(result) && result > 0.0D ? result : fallback;
        } catch (Throwable throwable) {
            LOGGER.warn("Upgrader numeric config could not be read, using {}", fallback, throwable);
            return fallback;
        }
    }

    private static int readInt(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return Math.max(0, value.get());
        } catch (Throwable throwable) {
            LOGGER.warn("Upgrader integer config could not be read, using {}", fallback, throwable);
            return fallback;
        }
    }

    private static long readLong(ForgeConfigSpec.LongValue value, long fallback) {
        try {
            return Math.max(1L, value.get());
        } catch (Throwable throwable) {
            LOGGER.warn("Upgrader long config could not be read, using {}", fallback, throwable);
            return fallback;
        }
    }

    private static boolean readBoolean(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (Throwable throwable) {
            LOGGER.warn("Upgrader boolean config could not be read, using {}", fallback, throwable);
            return fallback;
        }
    }

    private static void writeIfAbsent(Path file, String content) {
        try {
            if (!Files.exists(file)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Upgrader could not create the JSON table {}", file, exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Upgrader could not read {}", file, exception);
            return "";
        }
    }

    /** Parses a {@code {"namespace:path": number}} JSON object defensively. */
    private static Map<ResourceLocation, Long> parseTable(String json, Path source) {
        Map<ResourceLocation, Long> table = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return table;
        }

        JsonElement root;
        try {
            root = JsonParser.parseReader(new StringReader(json));
        } catch (RuntimeException exception) {
            LOGGER.warn("Upgrader JSON table {} is malformed, ignoring it", source, exception);
            return table;
        }

        if (!root.isJsonObject()) {
            LOGGER.warn("Upgrader JSON table {} must contain an object", source);
            return table;
        }

        JsonObject object = root.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                JsonElement value = entry.getValue();
                if (id == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Upgrader skipped invalid JSON table entry '{}' in {}", entry.getKey(), source);
                    continue;
                }
                long parsed = value.getAsLong();
                if (parsed > 0L) {
                    table.put(id, Math.min(parsed, UpgraderConstants.MAX_PRICE));
                }
            } catch (Throwable throwable) {
                LOGGER.warn("Upgrader skipped JSON table entry '{}' in {}", entry.getKey(), source, throwable);
            }
        }
        return table;
    }

    private UpgraderConfig() {
        // Static configuration holder.
    }
}
