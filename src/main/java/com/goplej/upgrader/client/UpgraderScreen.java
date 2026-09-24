package com.goplej.upgrader.client;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.goplej.upgrader.UpgraderMod;
import com.goplej.upgrader.UpgraderNetworking;
import com.goplej.upgrader.UpgraderScreenHandler;
import com.goplej.upgrader.UpgradeOption;
import com.goplej.upgrader.UpgradeRegistry;

/**
 * The upgrade menu: item slot on the left, one button per possible target on the right.
 * The GUI is drawn with solid fills — no background texture needed.
 */
public class UpgraderScreen extends HandledScreen<UpgraderScreenHandler> {
    private static final int OPTIONS_X = 66;
    private static final int OPTIONS_Y = 22;
    private static final int OPTIONS_W = 138;
    private static final int ROW_H = 25;
    private static final int ROW_H2 = 23;

    private static final int COL_PANEL = 0xE610141C;
    private static final int COL_PANEL_EDGE = 0xFF3A4A5F;
    private static final int COL_OPTIONS_BG = 0xFF111722;
    private static final int COL_OPTIONS_EDGE = 0xFF26303F;
    private static final int COL_ROW = 0xFF1C2433;
    private static final int COL_ROW_HOVER = 0xFF2E3B52;
    private static final int COL_ROW_EDGE = 0xFF3C4C63;
    private static final int COL_ROW_EDGE_HOVER = 0xFF5D7FA6;
    private static final int COL_SLOT_FRAME = 0xFF060A12;
    private static final int COL_SLOT_INNER = 0xFF10151F;
    private static final int COL_TITLE = 0xFF9BD1FF;
    private static final int COL_LABEL = 0xFFC7CFDB;
    private static final int COL_TEXT_DIM = 0xFF8FB8D8;
    private static final int COL_HINT = 0xFF7E8898;
    private static final int COL_ARROW = 0xFF7CE86B;

    public UpgraderScreen(UpgraderScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 220;
        this.backgroundHeight = 232;
        this.titleX = 10;
        this.titleY = 8;
        this.playerInventoryTitleX = UpgraderScreenHandler.INV_X;
        this.playerInventoryTitleY = 128;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawOptions(context, mouseX, mouseY);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        int w = this.backgroundWidth;
        int h = this.backgroundHeight;

        // Main panel
        fillBorder(context, x, y, w, h, COL_PANEL_EDGE);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL);

        // Input slot
        drawSlotSquare(context, x + UpgraderScreenHandler.INPUT_X, y + UpgraderScreenHandler.INPUT_Y);

        // Player inventory + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotSquare(context, x + UpgraderScreenHandler.INV_X + col * 18, y + UpgraderScreenHandler.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotSquare(context, x + UpgraderScreenHandler.INV_X + col * 18, y + UpgraderScreenHandler.HOTBAR_Y);
        }

        // Arrow: input -> upgrades
        context.fill(x + 36, y + 38, x + 55, y + 41, COL_ARROW);
        context.fill(x + 55, y + 35, x + 57, y + 44, COL_ARROW);
        context.fill(x + 57, y + 37, x + 59, y + 42, COL_ARROW);
        context.fill(x + 59, y + 39, x + 61, y + 40, COL_ARROW);

        // Options panel background
        fillBorder(context, x + 64, y + 20, 144, 104, COL_OPTIONS_EDGE);
        context.fill(x + 65, y + 21, x + 207, y + 123, COL_OPTIONS_BG);
    }

    private void drawOptions(DrawContext context, int mouseX, int mouseY) {
        ItemStack input = this.handler.getSlot(0).getStack();
        List<UpgradeOption> options = UpgradeRegistry.getOptions(input);

        if (options.isEmpty()) {
            Text hint = Text.translatable(input.isEmpty()
                    ? "upgrader.screen.hint_empty"
                    : "upgrader.screen.hint_no_upgrades");
            context.drawCenteredTextWithShadow(this.textRenderer, this.trim(hint, 138),
                    this.x + 64 + 72, this.y + 66, COL_HINT);
            return;
        }

        int hovered = this.optionAt(mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) {
            UpgradeOption option = options.get(i);
            boolean hover = i == hovered;
            int rx = this.x + OPTIONS_X;
            int ry = this.y + OPTIONS_Y + i * ROW_H;
            fillBorder(context, rx, ry, OPTIONS_W, ROW_H2, hover ? COL_ROW_EDGE_HOVER : COL_ROW_EDGE);
            context.fill(rx + 1, ry + 1, rx + OPTIONS_W - 1, ry + ROW_H2 - 1, hover ? COL_ROW_HOVER : COL_ROW);
            context.drawItem(new ItemStack(option.target()), rx + 3, ry + 3);
            context.drawTextWithShadow(this.textRenderer, this.trim(option.target().getName(), 110), rx + 22, ry + 4, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, this.trim(costSummary(option), 110), rx + 22, ry + 13, COL_TEXT_DIM);
        }

        if (hovered >= 0) {
            context.drawTooltip(this.textRenderer, this.optionTooltip(options.get(hovered)), mouseX + 14, mouseY + 6);
        }
    }

    private int optionAt(double mouseX, double mouseY) {
        ItemStack input = this.handler.getSlot(0).getStack();
        List<UpgradeOption> options = UpgradeRegistry.getOptions(input);
        for (int i = 0; i < options.size(); i++) {
            int rx = this.x + OPTIONS_X;
            int ry = this.y + OPTIONS_Y + i * ROW_H;
            if (mouseX >= rx && mouseX < rx + OPTIONS_W && mouseY >= ry && mouseY < ry + ROW_H2) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int option = this.optionAt(mouseX, mouseY);
        if (option >= 0) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(option);
            ClientPlayNetworking.send(UpgraderNetworking.SELECT_UPGRADE, buf);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, this.title, this.titleX, this.titleY, COL_TITLE);
        context.drawTextWithShadow(this.textRenderer, this.playerInventoryTitle,
                this.playerInventoryTitleX, this.playerInventoryTitleY, COL_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("upgrader.screen.item_slot"),
                UpgraderScreenHandler.INPUT_X, 21, COL_LABEL);
    }

    private List<Text> optionTooltip(UpgradeOption option) {
        List<Text> lines = new ArrayList<>();
        lines.add(option.target().getName().copy().formatted(Formatting.AQUA));
        lines.add(Text.translatable("upgrader.screen.cost").formatted(Formatting.GRAY));
        for (UpgradeOption.Cost cost : option.costs()) {
            lines.add(Text.literal(" " + cost.count() + "x ").append(cost.item().getName()).formatted(Formatting.GRAY));
        }
        lines.add(Text.empty());
        lines.add(Text.translatable("upgrader.screen.keep_hint").formatted(Formatting.DARK_GRAY));
        return lines;
    }

    private static Text costSummary(UpgradeOption option) {
        StringBuilder sb = new StringBuilder();
        for (UpgradeOption.Cost cost : option.costs()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(cost.count()).append("x ").append(cost.item().getName().getString());
        }
        return Text.literal(sb.toString());
    }

    private Text trim(Text text, int width) {
        return this.textRenderer.trimToWidth(text, width);
    }

    private static void drawSlotSquare(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, COL_SLOT_FRAME);
        context.fill(x + 1, y + 1, x + 17, y + 17, COL_SLOT_INNER);
    }

    private static void fillBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
