package com.upgrader.cache;

import com.upgrader.util.NbtHashUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Identity of a cached value: item id + hash of value-relevant NBT only.
 *
 * <p>Deliberately excludes stack count, display name and lore — two stacks that behave the same
 * share one entry (see {@link NbtHashUtil#relevantHash}).</p>
 */
public record CacheKey(ResourceLocation itemId, int nbtHash) {

    public static CacheKey of(ItemStack stack, ResourceLocation id) {
        return new CacheKey(id, NbtHashUtil.relevantHash(stack.hasTag() ? stack.getTag() : null));
    }
}
