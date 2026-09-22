package com.example.upgradermod.menu;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.SpinValidator;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SpinResultPacket;
import com.example.upgradermod.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Server authoritative container of the Upgrader.
 *
 * <p>It owns the input slot, the target slot, the bet multiplier and the last spin result. Values,
 * the chance and every random roll are computed on the server; the client only receives finished
 * numbers through {@link ContainerData} slots and the {@link SpinResultPacket}.</p>
 *
 * <p>Data slot layout (synced to the client every tick by
 * {@link AbstractContainerMenu#broadcastChanges()}):</p>
 * <table border="1">
 *     <caption>Container data</caption>
 *     <tr><th>Index</th><th>Meaning</th></tr>
 *     <tr><td>0</td><td>bet multiplier</td></tr>
 *     <tr><td>1</td><td>chance * 100</td></tr>
 *     <tr><td>2/3</td><td>input value, low/high 32 bit</td></tr>
 *     <tr><td>4/5</td><td>target value, low/high 32 bit</td></tr>
 *     <tr><td>6</td><td>last result: 0 none, 1 success, 2 failure</td></tr>
 *     <tr><td>7</td><td>last pre-spin check: 0 allowed, 1..6 = C1..C6</td></tr>
 * </table>
 */
public class UpgraderMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Index of the slot that holds the item to be consumed. */
    public static final int SLOT_INPUT = 0;

    /** Index of the slot that holds the wanted item. */
    public static final int SLOT_TARGET = 1;

    /** First index of the player inventory slots. */
    public static final int SLOT_PLAYER_START = 2;

    /** Exclusive end index of the player inventory slots. */
    public static final int SLOT_PLAYER_END = SLOT_PLAYER_START + 36;

    /** Number of synced data slots. */
    public static final int DATA_COUNT = 8;

    /** Data slot: bet multiplier. */
    public static final int DATA_MULTIPLIER = 0;

    /** Data slot: chance in hundredths of a percent. */
    public static final int DATA_CHANCE_X100 = 1;

    /** Data slot: low 32 bit of the input value. */
    public static final int DATA_INPUT_LOW = 2;

    /** Data slot: high 32 bit of the input value. */
    public static final int DATA_INPUT_HIGH = 3;

    /** Data slot: low 32 bit of the target value. */
    public static final int DATA_TARGET_LOW = 4;

    /** Data slot: high 32 bit of the target value. */
    public static final int DATA_TARGET_HIGH = 5;

    /** Data slot: last spin result. */
    public static final int DATA_LAST_RESULT = 6;

    /** Data slot: last pre-spin check code. */
    public static final int DATA_LAST_CHECK = 7;

    /** No spin has been performed yet. */
    public static final int RESULT_NONE = 0;

    /** The last spin succeeded. */
    public static final int RESULT_SUCCESS = 1;

    /** The last spin failed. */
    public static final int RESULT_FAILURE = 2;

    /** Screen x of the input slot. */
    public static final int INPUT_SLOT_X = 34;

    /** Screen y of the input slot. */
    public static final int INPUT_SLOT_Y = 43;

    /** Screen x of the target slot. */
    public static final int TARGET_SLOT_X = 34;

    /** Screen y of the target slot. */
    public static final int TARGET_SLOT_Y = 81;

    /** Screen x of the first player inventory slot. */
    public static final int PLAYER_INV_X = 48;

    /** Screen y of the first player inventory slot. */
    public static final int PLAYER_INV_Y = 187;

    private final SimpleContainer inputContainer = new SimpleContainer(1);
    private final SimpleContainer targetContainer = new SimpleContainer(1);
    private final ContainerData data;
    private final Player owner;

    private ItemStack lastInput = ItemStack.EMPTY;
    private ItemStack lastTarget = ItemStack.EMPTY;
    private int lastMultiplier = -1;
    private boolean valuesValid;

    private long inputValue;
    private long targetValue;

    /**
     * Server side constructor used by {@link com.example.upgradermod.item.UpgraderItem}.
     *
     * @param containerId     window id
     * @param playerInventory inventory of the opening player
     */
    public UpgraderMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainerData(DATA_COUNT));
    }

    /**
     * Client side constructor used through {@code IForgeMenuType}.
     *
     * @param containerId     window id
     * @param playerInventory inventory of the local player
     * @param buffer          extra data sent by the server, unused for this menu
     */
    public UpgraderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, new SimpleContainerData(DATA_COUNT));
    }

    /**
     * Shared constructor.
     *
     * @param containerId     window id
     * @param playerInventory inventory of the player
     * @param data            data slots, must have exactly {@link #DATA_COUNT} entries
     */
    public UpgraderMenu(int containerId, Inventory playerInventory, ContainerData data) {
        super(ModMenus.UPGRADER_MENU.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);

        this.owner = playerInventory.player;
        this.data = data;

        this.inputContainer.addListener(this::onContainerChanged);
        this.targetContainer.addListener(this::onContainerChanged);

        this.addSlot(new Slot(this.inputContainer, 0, INPUT_SLOT_X, INPUT_SLOT_Y));
        this.addSlot(new Slot(this.targetContainer, 0, TARGET_SLOT_X, TARGET_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, PLAYER_INV_Y + 58));
        }

        this.addDataSlots(data);
        this.data.set(DATA_MULTIPLIER, UpgraderConstants.MIN_MULTIPLIER);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.inputContainer.stillValid(player) && this.targetContainer.stillValid(player);
    }

    @Override
    public void broadcastChanges() {
        if (!this.owner.level().isClientSide) {
            try {
                this.refreshValues();
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader could not refresh the container values", throwable);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void slotsChanged(Container container) {
        this.valuesValid = false;
        super.slotsChanged(container);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack moved = slot.getItem();
            result = moved.copy();

            if (index < SLOT_PLAYER_START) {
                if (!this.moveItemStackTo(moved, SLOT_PLAYER_START, SLOT_PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(moved, SLOT_INPUT, SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }

            if (moved.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (moved.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, moved);
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.inputContainer);
        this.clearContainer(player, this.targetContainer);
    }

    /**
     * Performs one spin. Server side only, every step guarded by {@code try-catch(Throwable)}.
     *
     * <p>The result packet is always sent from the {@code finally} block, so the client can never
     * stay stuck in the spinning state.</p>
     *
     * @param player the spinning player
     */
    public void doSpin(ServerPlayer player) {
        boolean success = false;
        double chance = 0.0D;
        float rollAngle = 0.0F;

        try {
            this.refreshValues();

            ItemStack input = this.inputContainer.getItem(SLOT_INPUT);
            ItemStack target = this.targetContainer.getItem(SLOT_TARGET);
            int multiplier = this.getMultiplier();

            SpinValidator.Validation validation =
                    SpinValidator.validate(input, target, this.inputValue, this.targetValue, player);
            this.data.set(DATA_LAST_CHECK, validation.code());

            if (!validation.allowed()) {
                this.data.set(DATA_LAST_RESULT, RESULT_NONE);
                if (player != null && validation.messageKey() != null) {
                    player.displayClientMessage(Component.translatable(validation.messageKey()), true);
                }
                return;
            }

            chance = ChanceCalculator.chance(this.inputValue, this.targetValue, multiplier);

            RandomSource random = player.getRandom();
            success = ChanceCalculator.roll(chance, random);
            rollAngle = (float) (random.nextDouble() * 360.0D);

            int bet = Math.min(multiplier, input.getCount());
            if (success && bet > 0) {
                input.shrink(bet);
                this.inputContainer.setChanged();

                ItemStack reward = target.copy();
                reward.setCount(bet);
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
            }

            this.data.set(DATA_LAST_RESULT, success ? RESULT_SUCCESS : RESULT_FAILURE);
            LOGGER.debug("Upgrader spin by {}: input={} target={} chance={} success={}",
                    player.getGameProfile().getName(),
                    ValueCalculator.describe(input), ValueCalculator.describe(target),
                    ChanceCalculator.format(chance), success);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader spin failed, no item is consumed", throwable);
            success = false;
        } finally {
            try {
                this.valuesValid = false;
                this.refreshValues();
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader could not refresh its values after a spin", throwable);
            }

            try {
                NetworkHandler.sendToPlayer(new SpinResultPacket(success, chance, rollAngle), player);
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader could not send the spin result", throwable);
            }
        }
    }

    /**
     * Sets the bet multiplier. Server side, called from {@code SetMultiplierPacket}.
     *
     * @param multiplier requested multiplier, clamped to the supported range
     */
    public void setMultiplier(int multiplier) {
        this.data.set(DATA_MULTIPLIER,
                Mth.clamp(multiplier, UpgraderConstants.MIN_MULTIPLIER, UpgraderConstants.MAX_MULTIPLIER));
    }

    /**
     * Sets the target item. Server side, called from {@code SetTargetPacket}.
     *
     * @param target requested target, the count is normalised to one
     */
    public void setTarget(ItemStack target) {
        if (target == null || target.isEmpty()) {
            this.targetContainer.setItem(SLOT_TARGET, ItemStack.EMPTY);
            return;
        }

        ItemStack copy = target.copy();
        copy.setCount(1);
        this.targetContainer.setItem(SLOT_TARGET, copy);
    }

    /** @return the bet multiplier, always at least {@code 1} */
    public int getMultiplier() {
        return Math.max(UpgraderConstants.MIN_MULTIPLIER, this.data.get(DATA_MULTIPLIER));
    }

    /** @return the current chance in percent */
    public double getChance() {
        return this.data.get(DATA_CHANCE_X100) / 100.0D;
    }

    /** @return the value of the item in the input slot */
    public long getInputValue() {
        return combine(this.data.get(DATA_INPUT_LOW), this.data.get(DATA_INPUT_HIGH));
    }

    /** @return the value of the item in the target slot */
    public long getTargetValue() {
        return combine(this.data.get(DATA_TARGET_LOW), this.data.get(DATA_TARGET_HIGH));
    }

    /** @return {@link #RESULT_NONE}, {@link #RESULT_SUCCESS} or {@link #RESULT_FAILURE} */
    public int getLastResult() {
        return this.data.get(DATA_LAST_RESULT);
    }

    /** @return the code of the last pre-spin check, {@code 0} when the spin was allowed */
    public int getLastCheck() {
        return this.data.get(DATA_LAST_CHECK);
    }

    /** @return the stack currently shown in the input slot, valid on both sides */
    public ItemStack getDisplayedInput() {
        return this.getSlot(SLOT_INPUT).getItem();
    }

    /** @return the stack currently shown in the target slot, valid on both sides */
    public ItemStack getDisplayedTarget() {
        return this.getSlot(SLOT_TARGET).getItem();
    }

    /**
     * Recomputes input value, target value and chance when one of them can have changed.
     */
    private void refreshValues() {
        ItemStack input = this.inputContainer.getItem(SLOT_INPUT);
        ItemStack target = this.targetContainer.getItem(SLOT_TARGET);
        int multiplier = this.getMultiplier();

        if (this.valuesValid
                && multiplier == this.lastMultiplier
                && sameStack(input, this.lastInput)
                && sameStack(target, this.lastTarget)) {
            return;
        }

        this.lastInput = input.copy();
        this.lastTarget = target.copy();
        this.lastMultiplier = multiplier;
        this.valuesValid = true;

        this.inputValue = input.isEmpty() ? 0L : ValueCalculator.calculate(input);
        this.targetValue = target.isEmpty() ? 0L : ValueCalculator.calculate(target);

        double chance = ChanceCalculator.chance(this.inputValue, this.targetValue, multiplier);

        this.data.set(DATA_INPUT_LOW, low(this.inputValue));
        this.data.set(DATA_INPUT_HIGH, high(this.inputValue));
        this.data.set(DATA_TARGET_LOW, low(this.targetValue));
        this.data.set(DATA_TARGET_HIGH, high(this.targetValue));
        this.data.set(DATA_CHANCE_X100, (int) Math.round(chance * 100.0D));
    }

    private void onContainerChanged(Container container) {
        this.valuesValid = false;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return ItemStack.isSameItemSameTags(left, right) && left.getCount() == right.getCount();
    }

    private static int low(long value) {
        return (int) (value & 0xFFFFFFFFL);
    }

    private static int high(long value) {
        return (int) (value >>> 32);
    }

    private static long combine(int low, int high) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }
}
