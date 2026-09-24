package com.upgrader.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.TreeMap;

/**
 * Value-relevant NBT hashing used by cache keys and item identity.
 *
 * <p>Deliberately ignores cosmetic data (display name, lore, block entity position) so that two
 * stacks which behave identically share one cache entry. Enchantments, damage and arbitrary
 * custom data DO affect the hash because they change computed value.</p>
 */
public final class NbtHashUtil {

    private NbtHashUtil() {
    }

    /**
     * Order-independent hash over the value-relevant portion of a tag tree.
     * Returns 0 for {@code null}/empty tags so plain items collapse to a single cache slot.
     */
    public static int relevantHash(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return 0;
        }
        int h = 1;
        // TreeMap gives key-order independence regardless of internal NBT ordering.
        for (Map.Entry<String, Tag> e : flatten(tag).entrySet()) {
            h = 31 * h + e.getKey().hashCode();
            h = 31 * h + relevantValueHash(e.getValue());
        }
        return h;
    }

    private static Map<String, Tag> flatten(CompoundTag tag) {
        Map<String, Tag> out = new TreeMap<>();
        for (String key : tag.getAllKeys()) {
            if (isCosmetic(key)) {
                continue;
            }
            Tag child = tag.get(key);
            if (child != null) {
                out.put(key, child);
            }
        }
        return out;
    }

    /** Cosmetic keys that must NOT influence value identity. */
    private static boolean isCosmetic(String key) {
        return "Name".equals(key) || "Lore".equals(key) || "PublicBukkitValues".equals(key);
    }

    private static int relevantValueHash(Tag tag) {
        if (tag instanceof CompoundTag c) {
            return relevantHash(c);
        }
        if (tag instanceof CollectionTag<?> list) {
            int h = 1;
            for (Tag el : list) {
                h = 31 * h + relevantValueHash(el);
            }
            return h;
        }
        // Ints/strings/floats etc.: structural equals/hashCode of the boxed Tag is stable.
        return tag.hashCode();
    }

    /** True when a compound carries a BlockPos-like sub-tag (data-driven items from other mods). */
    public static boolean hasBlockPos(CompoundTag tag) {
        return tag != null && tag.contains("pos", Tag.TAG_INT_ARRAY);
    }
}
