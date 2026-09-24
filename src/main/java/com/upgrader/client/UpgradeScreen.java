package com.upgrader.client;

import com.upgrader.api.UpgraderAPI;
import com.upgrader.gui.widget.ValueBarWidget;
import com.upgrader.network.UpgraderPayloadHandler;
import com.upgrader.network.UpgradeRequestPacket;
import com.upgrader.upgrade.UpgradeCostCalculator;
import com.upgrader.upgrade.UpgradeCurve;
import com.upgrader.upgrade.UpgradeEngine;
import com.upgrader.util.TextFormatUtil;
import com.upgrader.value.ItemValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client screen showing the engine's verdict for the analyzed stack: total value, a stacked bar
 * of the breakdown, current level and the exact next-level cost (computed locally from the same
 * curve config — identical math to the server). The button only sends a slot-less request packet;
 * the server re-validates everything, so this screen is trusted for display only.
 */
public final class UpgradeScreen extends Screen {

    private final ItemStack target;
    private ValueBarWidget valueBar;

    public UpgradeScreen(final ItemStack target) {
        super(Component.translatable("gui.upgrader.title"));
        this.target = target.copy();
    }

    @Override
    protected void init() {
        super.init();
        this.valueBar = new ValueBarWidget(width / 2 - 90, height / 2 - 10, 180, 12);
        addRenderableWidget(valueBar);
        addRenderableWidget(Button.builder(Component.translatable("gui.upgrader.upgrade"),
                        btn -> UpgraderPayloadHandler.INSTANCE.sendToServer(new UpgradeRequestPacket(0)))
                .bounds(width / 2 - 60, height / 2 + 40, 120, 20)
                .build());
        feedBar();
    }

    private void feedBar() {
        ItemValue value = UpgraderAPI.getItemValue(target);
        var rows = value.breakdown();
        List<Double> fractions = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        long totalAbs = 0;
        for (var r : rows) {
            totalAbs += Math.abs(r.amount());
        }
        if (totalAbs > 0) {
            for (var r : rows) {
                fractions.add(Math.abs(r.amount()) / (double) totalAbs);
                colors.add(switch (r.type()) {
                    case RECIPE, COMPONENT -> GuiColors.SEGMENT_RECIPE;
                    case TAG -> GuiColors.SEGMENT_TAG;
                    case NBT -> GuiColors.SEGMENT_NBT;
                    case HEURISTIC, PROPERTY, OVERRIDE -> GuiColors.SEGMENT_HEURISTIC;
                    case INTEGRATION -> GuiColors.SEGMENT_INTEGRATION;
                });
            }
        }
        valueBar.setData(fractions, colors);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
                       final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemValue value = UpgraderAPI.getItemValue(target);
        graphics.drawCenteredString(font,
                "Value: " + TextFormatUtil.formatValue(value.numericValue())
                        + " [" + value.tier().name() + "]",
                width / 2, height / 2 - 40, GuiColors.TEXT_PRIMARY);
        int level = UpgradeEngine.levelOf(target);
        graphics.drawCenteredString(font,
                Component.translatable("gui.upgrader.current_level", level).getString(),
                width / 2, height / 2 - 28, GuiColors.TEXT_MUTED);
        UpgradeCurve curve = UpgradeCostCalculator.currentCurve();
        long base = Math.max(1L, value.numericValue());
        graphics.drawCenteredString(font,
                Component.translatable("gui.upgrader.next_cost",
                        TextFormatUtil.formatValue(curve.costOf(base, level))).getString(),
                width / 2, height / 2 + 16, GuiColors.TEXT_PRIMARY);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // open over live gameplay; upgrades are server-authoritative anyway
    }
}
