package com.upgrader.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves the registry id of an {@link ItemStack}'s item. Single source of truth so the recipe
 * index, tree builder and identity resolver never disagree.
 */
public final class ItemIdentity {

    private ItemIdentity() {
    }

    /** Registry id of the stack's item, or null for empty/air-like unregistered stacks. */
    public static ResourceLocation of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
