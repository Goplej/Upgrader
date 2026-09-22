package com.example.upgradermod.logic;

import com.example.upgradermod.UpgraderConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry lookup cache.
 *
 * <p>Iterating the item registry and resolving ids is done on every value lookup, so the registry
 * snapshot (id table, suffix index) and the computed values are cached here. The snapshot is rebuilt
 * lazily and dropped when tags or the config change.</p>
 *
 * <p>The value cache is deliberately keyed by {@link Item} only: NBT sensitive stacks bypass the
 * cache completely (see {@link ValueCalculator}).</p>
 */
public final class ItemRegistryCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hard ceiling for the value cache, prevents unbounded growth on huge modpacks. */
    private static final int VALUE_CACHE_LIMIT = 8192;

    private static final Map<Item, Long> VALUE_CACHE = new ConcurrentHashMap<>();

    private static volatile Map<ResourceLocation, Item> idToItem = Map.of();
    private static volatile Map<Item, ResourceLocation> itemToId = Map.of();
    private static volatile Map<String, List<Item>> pathIndex = Map.of();
    private static volatile List<Item> allItems = List.of();
    private static volatile boolean built;

    /**
     * Rebuilds the registry snapshot. Cheap enough to call on datapack reload.
     */
    public static synchronized void rebuild() {
        try {
            Map<ResourceLocation, Item> ids = new HashMap<>();
            Map<Item, ResourceLocation> reverse = new HashMap<>();
            Map<String, List<Item>> paths = new HashMap<>();
            List<Item> items = new ArrayList<>();

            for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                Item item = entry.getValue();
                if (id == null || item == null || item == Items.AIR) {
                    continue;
                }
                ids.put(id, item);
                reverse.put(item, id);
                items.add(item);
                paths.computeIfAbsent(id.getPath(), key -> new ArrayList<>()).add(item);
            }

            Map<String, List<Item>> immutablePaths = new HashMap<>();
            paths.forEach((path, list) -> immutablePaths.put(path, List.copyOf(list)));

            idToItem = Map.copyOf(ids);
            itemToId = Map.copyOf(reverse);
            pathIndex = Map.copyOf(immutablePaths);
            allItems = List.copyOf(items);
            built = true;

            LOGGER.debug("Upgrader registry cache rebuilt with {} items", allItems.size());
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not rebuild its registry cache", throwable);
        }
    }

    /** Drops the registry snapshot and the value cache. */
    public static synchronized void invalidate() {
        built = false;
        VALUE_CACHE.clear();
    }

    /** Drops the value cache only, keeping the registry snapshot. */
    public static void clearValueCache() {
        VALUE_CACHE.clear();
    }

    /**
     * @param id item id
     * @return the item, or {@code null} when the id is not registered
     */
    public static Item byId(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        ensureBuilt();
        return idToItem.get(id);
    }

    /**
     * @param id item id
     * @return {@code true} when the id is registered
     */
    public static boolean exists(ResourceLocation id) {
        return byId(id) != null;
    }

    /**
     * @param item item
     * @return the registry id, or {@code null} when the item is not registered
     */
    public static ResourceLocation id(Item item) {
        if (item == null) {
            return null;
        }
        ensureBuilt();
        ResourceLocation cached = itemToId.get(item);
        if (cached != null) {
            return cached;
        }
        // Items registered after the snapshot (runtime registration) are resolved directly.
        try {
            return BuiltInRegistries.ITEM.getKey(item);
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not resolve the id of {}", item, throwable);
            return null;
        }
    }

    /**
     * @return every registered item except air, in registry order
     */
    public static List<Item> allItems() {
        ensureBuilt();
        return allItems;
    }

    /**
     * Items whose registry path matches the given path exactly, in any namespace.
     *
     * @param path registry path, e.g. {@code diamond_sword}
     * @return matching items, never {@code null}
     */
    public static List<Item> byPath(String path) {
        if (path == null) {
            return List.of();
        }
        ensureBuilt();
        return pathIndex.getOrDefault(path, List.of());
    }

    /**
     * Case insensitive search used by the catalogue screen.
     *
     * @param needle text to look for in the registry id
     * @return matching items, never {@code null}
     */
    public static List<Item> search(String needle) {
        ensureBuilt();
        if (needle == null || needle.isBlank()) {
            return allItems;
        }
        String lowered = needle.toLowerCase(Locale.ROOT).trim();
        List<Item> matches = new ArrayList<>();
        for (Map.Entry<Item, ResourceLocation> entry : itemToId.entrySet()) {
            if (entry.getValue().toString().toLowerCase(Locale.ROOT).contains(lowered)) {
                matches.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * @param item item
     * @return the cached value, or {@code null} when nothing is cached
     */
    public static Long getCachedValue(Item item) {
        return item == null ? null : VALUE_CACHE.get(item);
    }

    /**
     * Stores a computed value.
     *
     * @param item  item
     * @param value computed value
     */
    public static void putCachedValue(Item item, long value) {
        if (item == null || value <= 0L) {
            return;
        }
        if (VALUE_CACHE.size() >= VALUE_CACHE_LIMIT) {
            VALUE_CACHE.clear();
        }
        VALUE_CACHE.put(item, Math.min(value, UpgraderConstants.MAX_PRICE));
    }

    /** @return number of cached values, used by diagnostics only */
    public static int cachedValueCount() {
        return VALUE_CACHE.size();
    }

    private static void ensureBuilt() {
        if (!built) {
            rebuild();
        }
    }

    private ItemRegistryCache() {
        // Static access only.
    }
}
