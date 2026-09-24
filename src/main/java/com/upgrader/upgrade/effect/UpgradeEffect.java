package com.upgrader.upgrade.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One gameplay behaviour an upgraded item can gain (Strategy pattern; see
 * {@link com.upgrader.upgrade.UpgradeEffectRegistry}).
 *
 * <p>Effects are pure data + policy: they stamp NBT during the upgrade transaction; runtime
 * hooks (mining events, tick queues) read that NBT later on the server thread.</p>
 */
public interface UpgradeEffect {

    /** Stable id used in config, network and NBT (e.g. {@code upgrader:efficiency}). */
    ResourceLocation getId();

    /** Whether this effect makes sense for the given stack at all (tool vs sword vs chest). */
    boolean isApplicableTo(ItemStack stack);

    /**
     * Stamps the effect onto the stack for the given total upgrade level. Must be idempotent:
     * applying twice with the same level yields the same stack state.
     */
    void apply(ItemStack stack, int level, UpgradeContext ctx);
}
