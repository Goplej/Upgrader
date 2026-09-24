package com.goplej.upgrader;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Screen handler for the upgrade menu.
 *
 * <p>Slot 0 is the item being upgraded (the upgrade happens in place, so the
 * item keeps its identity); the rest is the player inventory. The list of
 * possible upgrades is derived from the item in slot 0 and rendered by the
 * client screen as buttons; a click is sent to {@link #tryApplyUpgrade}.
 */
public class UpgraderScreenHandler extends ScreenHandler {
    public static final int INPUT_X = 14;
    public static final int INPUT_Y = 30;
    public static final int INV_X = 29;
    public static final int INV_Y = 142;
    public static final int HOTBAR_Y = 202;

    private final Inventory inventory;

    public UpgraderScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1));
    }

    public UpgraderScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(UpgraderMod.UPGRADER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        inventory.onOpen(playerInventory.player);
        this.addSlot(new InputSlot(inventory, 0, INPUT_X, INPUT_Y));
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public Inventory getInputInventory() {
        return this.inventory;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!this.insertItem(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.insertItem(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return copy;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) {
            ItemStack leftover = this.inventory.removeStack(0);
            if (!leftover.isEmpty()) {
                player.getInventory().offerOrDrop(leftover);
            }
        }
    }

    /**
     * Server-side: validates the requested upgrade, consumes the materials and
     * replaces the item in the input slot with the upgraded one.
     */
    public void tryApplyUpgrade(ServerPlayerEntity player, int optionIndex) {
        ItemStack input = this.inventory.getStack(0);
        List<UpgradeOption> options = UpgradeRegistry.getOptions(input);
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        UpgradeOption option = options.get(optionIndex);
        if (!player.isCreative()) {
            for (UpgradeOption.Cost cost : option.costs()) {
                if (countItem(player.getInventory(), cost.item()) < cost.count()) {
                    return;
                }
            }
            for (UpgradeOption.Cost cost : option.costs()) {
                removeItem(player.getInventory(), cost.item(), cost.count());
            }
        }
        this.inventory.setStack(0, UpgradeRegistry.upgrade(input, option.target()));
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.8f, 1.3f);
        this.sendContentUpdates();
    }

    private static int countItem(PlayerInventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeItem(PlayerInventory inventory, Item item, int amount) {
        for (int i = 0; i < inventory.size() && amount > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == item) {
                int taken = Math.min(amount, stack.getCount());
                stack.decrement(taken);
                amount -= taken;
            }
        }
    }

    /** Only upgradable items may be placed into the input slot. */
    public static class InputSlot extends Slot {
        public InputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return UpgradeRegistry.isUpgradable(stack.getItem());
        }
    }
}
