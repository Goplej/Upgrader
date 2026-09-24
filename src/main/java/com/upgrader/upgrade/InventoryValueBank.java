package com.upgrader.upgrade;

import com.upgrader.api.UpgraderAPI;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Charges upgrade costs out of a player's inventory by <em>value</em>, not by any specific
 * currency item: every stack is priced through the engine, cheapest first are consumed until the
 * bill is settled. This keeps the economy mod-agnostic — diamonds or neutronium both pay, each
 * at its computed worth.
 *
 * <p>Two-phase for atomicity: {@link #charge(long)} only simulates and records which slots to
 * shrink; nothing mutates until {@link #commit()} runs (the caller commits after all further
 * validation passed). {@link #refund(long)} simply drops the pending plan.</p>
 */
public final class InventoryValueBank {

    private record Debit(int slot, int startIndex, int count, long value) {
    }

    private final Player player;
    private final List<Debit> pending = new ArrayList<>();
    private long pendingValue;

    public InventoryValueBank(Player player) {
        this.player = player;
    }

    /** Total computable value sitting in the main inventory (armour excluded on purpose). */
    public long totalValue() {
        Inventory inv = player.getInventory();
        long total = 0L;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            var v = UpgraderAPI.getItemValue(stack);
            if (v.isKnown()) {
                total += Math.max(0L, v.numericValue()) * stack.getCount();
            }
        }
        return total;
    }

    /**
     * Plans payment of {@code cost}. Returns the amount actually coverable (&lt; cost when the
     * player is short — the caller must then refund and abort). Nothing is removed yet.
     */
    public long charge(long cost) {
        clearPending();
        if (cost <= 0L) {
            return 0L;
        }
        Inventory inv = player.getInventory();
        record Priced(int slot, ItemStack stack, long unitValue) {
        }
        List<Priced> priced = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || stack == player.getMainHandItem()) {
                continue; // never eat the item being upgraded itself
            }
            var v = UpgraderAPI.getItemValue(stack);
            if (!v.isKnown() || v.numericValue() <= 0) {
                continue;
            }
            priced.add(new Priced(i, stack, v.numericValue()));
        }
        priced.sort(Comparator.comparingLong(Priced::unitValue)); // cheapest first
        long remaining = cost;
        for (Priced p : priced) {
            if (remaining <= 0) {
                break;
            }
            int maxUnits = p.stack().getCount();
            long affordableUnits = Math.min(maxUnits, (remaining + p.unitValue() - 1) / p.unitValue());
            int take = (int) Math.min(maxUnits, Math.max(1L, affordableUnits));
            long value = take * p.unitValue();
            if (value > remaining) {
                // Trim so we never over-charge due to ceil division.
                take = (int) (remaining / p.unitValue());
                value = (long) take * p.unitValue();
            }
            if (take <= 0) {
                continue;
            }
            pending.add(new Debit(p.slot(), maxUnits - take, take, value));
            remaining -= value;
            pendingValue += value;
        }
        return cost - Math.max(0L, remaining);
    }

    /** Applies the planned debits to the real inventory. Call once, right before success. */
    public void commit() {
        Inventory inv = player.getInventory();
        for (Debit d : pending) {
            ItemStack stack = inv.getItem(d.slot());
            if (stack.isEmpty()) {
                continue;
            }
            stack.shrink(d.count());
        }
        clearPending();
    }

    /** Discards the plan (failure path); safe to call with an empty plan. */
    public void refund(long ignoredAmount) {
        clearPending();
    }

    public long pendingValue() {
        return pendingValue;
    }

    private void clearPending() {
        pending.clear();
        pendingValue = 0L;
    }
}
