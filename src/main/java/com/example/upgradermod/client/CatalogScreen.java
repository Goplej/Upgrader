package com.example.upgradermod.client;

import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SetTargetPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Item catalogue used to pick the upgrade target.
 *
 * <p>Drawn in the same dark theme as {@link UpgraderScreen}. The list is scrollable, filterable and
 * paginated by the visible grid; picking an entry sends a {@link SetTargetPacket} to the server and
 * returns to the Upgrader screen. The server keeps the authoritative copy of the target.</p>
 */
public class CatalogScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Columns of the visible grid. */
    public static final int GRID_COLUMNS = 8;

    /** Rows of the visible grid. */
    public static final int GRID_ROWS = 6;

    /** Cell size in pixels. */
    public static final int CELL_SIZE = 20;

    /** Width of the search box. */
    public static final int SEARCH_WIDTH = 200;

    /** Scroll step per mouse wheel notch, in items. */
    public static final int SCROLL_STEP = 3;

    private final Screen parent;

    private final List<ItemStack> entries = new ArrayList<>();

    @Nullable
    private EditBox searchBox;

    private int scrollOffset;
    private int hoveredIndex = -1;

    /**
     * @param parent screen to return to, normally the {@link UpgraderScreen}
     */
    public CatalogScreen(Screen parent) {
        super(Component.translatable("upgradermod.catalog.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.width / 2 - SEARCH_WIDTH / 2, 36, SEARCH_WIDTH, 20,
                Component.translatable("upgradermod.gui.search"));
        this.searchBox.setResponder(text -> this.applyFilter());
        this.searchBox.setMaxLength(64);
        this.addWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        this.applyFilter();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int gridLeft = this.gridLeft();
        int gridTop = this.gridTop();
        int gridWidth = GRID_COLUMNS * CELL_SIZE;
        int gridHeight = GRID_ROWS * CELL_SIZE;

        guiGraphics.fill(gridLeft - 8, gridTop - 8, gridLeft + gridWidth + 8, gridTop + gridHeight + 8,
                UpgraderScreen.COLOR_PANEL);
        guiGraphics.renderOutline(gridLeft - 8, gridTop - 8, gridWidth + 16, gridHeight + 16,
                UpgraderScreen.COLOR_BORDER);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, UpgraderScreen.COLOR_GOLD);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.hoveredIndex = -1;
        ItemStack hoveredStack = null;

        for (int index = 0; index < this.visibleCount(); index++) {
            int absolute = this.scrollOffset + index;
            if (absolute >= this.entries.size()) {
                break;
            }

            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int cellX = gridLeft + column * CELL_SIZE;
            int cellY = gridTop + row * CELL_SIZE;

            boolean hovered = mouseX >= cellX - 2 && mouseX < cellX + 18
                    && mouseY >= cellY - 2 && mouseY < cellY + 18;

            guiGraphics.fill(cellX - 2, cellY - 2, cellX + 18, cellY + 18, UpgraderScreen.COLOR_SLOT);
            guiGraphics.renderOutline(cellX - 2, cellY - 2, 20, 20,
                    hovered ? UpgraderScreen.COLOR_GOLD : UpgraderScreen.COLOR_BORDER);

            ItemStack stack = this.entries.get(absolute);
            guiGraphics.renderItem(stack, cellX, cellY);
            guiGraphics.renderItemDecorations(this.font, stack, cellX, cellY);

            if (hovered) {
                this.hoveredIndex = absolute;
                hoveredStack = stack;
            }
        }

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("upgradermod.catalog.count", this.entries.size()),
                this.width / 2, gridTop + gridHeight + 14, UpgraderScreen.COLOR_TEXT);

        guiGraphics.drawCenteredString(this.font, this.currentTargetText(),
                this.width / 2, gridTop + gridHeight + 26, UpgraderScreen.COLOR_ACCENT);

        guiGraphics.drawCenteredString(this.font, Component.translatable("upgradermod.catalog.hint"),
                this.width / 2, gridTop + gridHeight + 40, UpgraderScreen.COLOR_BORDER);

        if (hoveredStack != null) {
            List<Component> tooltip = new ArrayList<>();
            if (this.minecraft != null) {
                tooltip.addAll(Screen.getTooltipFromItem(this.minecraft, hoveredStack));
            }
            long value = ValueCalculator.calculate(hoveredStack);
            tooltip.add(Component.translatable("upgradermod.catalog.tooltip.value",
                    UpgraderScreen.formatValue(value)));

            UpgraderMenu menu = this.parent instanceof UpgraderScreen screen ? screen.getMenu() : null;
            if (menu != null) {
                double chance = ChanceCalculator.chance(menu.getInputValue(), value, menu.getMultiplier());
                tooltip.add(Component.translatable("upgradermod.catalog.tooltip.chance",
                        ChanceCalculator.format(chance)));
            }
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.hoveredIndex >= 0 && this.hoveredIndex < this.entries.size()) {
            this.select(this.entries.get(this.hoveredIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int direction = delta > 0.0D ? -SCROLL_STEP : (delta < 0.0D ? SCROLL_STEP : 0);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset + direction, this.maxScrollOffset()));
        return true;
    }

    @Override
    public void onClose() {
        this.goBack();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Sends the chosen target to the server and returns to the Upgrader screen.
     *
     * @param stack the chosen stack
     */
    private void select(ItemStack stack) {
        try {
            ItemStack target = stack.copy();
            target.setCount(1);
            NetworkHandler.sendToServer(new SetTargetPacket(target));
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not send the catalogue selection", throwable);
        }
        this.goBack();
    }

    private void goBack() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** Rebuilds the entry list from the current search text. */
    private void applyFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        String lowered = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<ItemStack> result = new ArrayList<>();
        try {
            for (Item item : ItemRegistryCache.allItems()) {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!lowered.isEmpty() && !matches(stack, lowered)) {
                    continue;
                }
                result.add(stack);
            }
            result.sort(Comparator.comparing(CatalogScreen::idOf));
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not build the catalogue list", throwable);
        }

        this.entries.clear();
        this.entries.addAll(result);
        this.scrollOffset = 0;
    }

    private static boolean matches(ItemStack stack, String loweredQuery) {
        if (idOf(stack).contains(loweredQuery)) {
            return true;
        }
        try {
            return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(loweredQuery);
        } catch (Throwable throwable) {
            LOGGER.debug("Upgrader could not read the display name of a catalogue entry", throwable);
            return false;
        }
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation id = ItemRegistryCache.id(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private Component currentTargetText() {
        UpgraderMenu menu = ClientSetup.openMenuOrNull();
        if (menu == null) {
            return Component.translatable("upgradermod.catalog.target.none");
        }
        ItemStack target = menu.getDisplayedTarget();
        if (target.isEmpty()) {
            return Component.translatable("upgradermod.catalog.target.none");
        }
        return Component.translatable("upgradermod.catalog.target.current", target.getHoverName());
    }

    private int visibleCount() {
        return GRID_COLUMNS * GRID_ROWS;
    }

    private int maxScrollOffset() {
        return Math.max(0, this.entries.size() - this.visibleCount());
    }

    private int gridLeft() {
        return this.width / 2 - (GRID_COLUMNS * CELL_SIZE) / 2;
    }

    private int gridTop() {
        return 68;
    }
}
