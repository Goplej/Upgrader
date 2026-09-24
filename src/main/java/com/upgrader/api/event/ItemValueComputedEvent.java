package com.upgrader.api.event;

import com.upgrader.recipe.ItemIdentity;
import com.upgrader.value.ItemValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the FORGE bus every time the engine computes a fresh (non-cached) value.
 * Listeners may read but not mutate: {@link ItemValue} is an immutable record, and posting this
 * event never holds the engine's lock.
 */
public final class ItemValueComputedEvent extends Event {

    private final ItemStack stack;
    private final ItemValue value;

    public ItemValueComputedEvent(final ItemStack stack, final ItemValue value) {
        this.stack = stack.copy(); // defensive copy: listeners must not touch live stacks
        this.value = value;
    }

    public ItemStack getStack() {
        return stack;
    }

    public ItemValue getValue() {
        return value;
    }

    /** Registry id of the priced item (null for unregistered stacks). */
    public ResourceLocation getItemId() {
        return ItemIdentity.of(stack);
    }
}
