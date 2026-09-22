package com.example.upgradermod.config;

import com.example.upgradermod.UpgraderConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
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
 * Flat file configuration of the Upgrader mod.
 *
 * <p>Two JSON tables are read from {@code config/upgradermod/}:</p>
 * <ul>
 *     <li>{@code overrides.json} &ndash; hard item value overrides, consumed by
 *     {@code OverrideValueProvider} (priority 1000).</li>
 *     <li>{@code tags.json} &ndash; item tag values, consumed by {@code TagValueProvider}
 *     (priority 500).</li>
 * </ul>
 *
 * <p>Both files are created with sensible defaults on first launch. Keys that start with
 * {@code _} or {@code #} are treated as comments, every other malformed entry is skipped with a
 * warning instead of aborting the load.</p>
 */
public final class UpgraderConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DEFAULT_OVERRIDES = """
            {
              "_comment": "Hard value overrides. Key = item id, value = price in Upgrader value units.",
              "minecraft:diamond": 8000,
              "minecraft:emerald": 6000,
              "minecraft:netherite_ingot": 45000,
              "minecraft:netherite_scrap": 12000,
              "minecraft:ender_eye": 1400
            }
            """;

    private static final String DEFAULT_TAGS = """
            {
              "_comment": "Values applied to every item that is part of the given item tag.",
              "forge:ingots/iron": 256,
              "forge:ingots/gold": 512,
              "forge:gems/diamond": 8000,
              "forge:gems/emerald": 6000,
              "forge:dusts/redstone": 128,
              "minecraft:logs": 32,
              "minecraft:planks": 8
            }
            """;

    private static volatile Map<ResourceLocation, Long> overrides = Collections.emptyMap();
    private static volatile Map<ResourceLocation, Long> tagValues = Collections.emptyMap();
    private static volatile boolean loaded;

    /**
     * Reads both tables from disk, writing the defaults first when a file is missing.
     *
     * <p>Never throws: any failure leaves the previously loaded tables in place (or empty tables on
     * the very first load) and is written to the log.</p>
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
            LOGGER.error("Upgrader could not load its configuration from {}, keeping the previous tables",
                    configDirectory(), throwable);
        }

        overrides = Collections.unmodifiableMap(loadedOverrides);
        tagValues = Collections.unmodifiableMap(loadedTags);
        loaded = true;

        LOGGER.info("Upgrader config loaded: {} item overrides, {} tag values", overrides.size(), tagValues.size());
    }

    /** @return the directory that holds the Upgrader JSON tables */
    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(UpgraderConstants.CONFIG_FOLDER);
    }

    /**
     * Hard override lookup used by {@code OverrideValueProvider}.
     *
     * @param id item id
     * @return the configured value, or {@code -1} when the item is not overridden
     */
    public static long getOverride(ResourceLocation id) {
        if (id == null) {
            return -1L;
        }
        Long value = overrides.get(id);
        return value == null ? -1L : value;
    }

    /**
     * Tag value lookup used by {@code TagValueProvider}.
     *
     * @param tag item tag id
     * @return the configured value, or {@code -1} when the tag is not configured
     */
    public static long getTagValue(ResourceLocation tag) {
        if (tag == null) {
            return -1L;
        }
        Long value = tagValues.get(tag);
        return value == null ? -1L : value;
    }

    /** @return {@code true} once {@link #reload()} completed at least once */
    public static boolean isLoaded() {
        return loaded;
    }

    /** @return number of configured item overrides, mostly useful for logging and diagnostics */
    public static int overrideCount() {
        return overrides.size();
    }

    /** @return number of configured tag values, mostly useful for logging and diagnostics */
    public static int tagValueCount() {
        return tagValues.size();
    }

    private static void writeIfAbsent(Path file, String content) {
        try {
            if (!Files.exists(file)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Upgrader could not create the default config file {}", file, exception);
        }
    }

    private static String read(Path file) {
        try {
            if (!Files.exists(file)) {
                return "";
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Upgrader could not read {}", file, exception);
            return "";
        }
    }

    /**
     * Parses a {@code {"namespace:path": number}} object.
     *
     * @param json raw file content
     * @param source file the content came from, used for log messages
     * @return an immutable table of parsed entries
     */
    private static Map<ResourceLocation, Long> parseTable(String json, Path source) {
        Map<ResourceLocation, Long> table = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return table;
        }

        JsonElement root;
        try {
            root = JsonParser.parseReader(new StringReader(json));
        } catch (RuntimeException exception) {
            LOGGER.warn("Upgrader config {} is not valid JSON, the table will be empty", source, exception);
            return table;
        }

        if (!root.isJsonObject()) {
            LOGGER.warn("Upgrader config {} must contain a JSON object, the table will be empty", source);
            return table;
        }

        JsonObject object = root.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_") || key.startsWith("#")) {
                continue; // Comment entry.
            }
            try {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null) {
                    LOGGER.warn("Upgrader config {}: '{}' is not a valid resource location, entry skipped", source, key);
                    continue;
                }

                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Upgrader config {}: value of '{}' must be a number, entry skipped", source, key);
                    continue;
                }

                long parsed = value.getAsLong();
                if (parsed <= 0L) {
                    LOGGER.warn("Upgrader config {}: value of '{}' must be greater than zero, entry skipped", source, key);
                    continue;
                }

                table.put(id, Math.min(parsed, UpgraderConstants.MAX_PRICE));
            } catch (Throwable throwable) {
                LOGGER.warn("Upgrader config {}: entry '{}' could not be parsed, entry skipped", source, key, throwable);
            }
        }
        return table;
    }

    private UpgraderConfig() {
        // Static access only.
    }
}
