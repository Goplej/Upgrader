package com.upgrader.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Cancellable: fired <em>before</em> the level increment commits. Mods that veto the upgrade
 * (quest gates, anti-cheat, dimension rules) should cancel it — the engine then refunds the
 * planned debit and aborts without mutating anything.
 */
@Cancelable
public final class UpgradeAppliedEvent extends Event {

    private final Player player;
    private final ItemStack target;
    private final int fromLevel;
    private final int toLevel;
    private final long cost;

    public UpgradeAppliedEvent(final Player player, final ItemStack target,
                               final int fromLevel, final int toLevel, final long cost) {
        this.player = player;
        this.target = target;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.cost = cost;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getTarget() {
        return target;
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    public long getCost() {
        return cost;
    }
}
