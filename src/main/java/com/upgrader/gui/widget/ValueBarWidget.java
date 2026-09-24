package com.upgrader.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.upgrader.client.GuiColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Horizontal stacked bar visualising a value breakdown: one coloured segment per source row,
 * widths proportional to contribution. Purely presentational — it never computes anything.
 */
public final class ValueBarWidget extends AbstractWidget {

    private List<Double> fractions = List.of(); // share of total per segment (sums <= 1)
    private List<Integer> colors = List.of();

    public ValueBarWidget(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.translatable("gui.upgrader.value_bar"));
    }

    /** Feeds the widget; lengths must match between fractions and colors. */
    public void setData(final List<Double> fractions, final List<Integer> colors) {
        this.fractions = fractions == null ? List.of() : fractions;
        this.colors = colors == null ? List.of() : colors;
    }

    @Override
    public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY,
                             final float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), GuiColors.BAR_BG);
        double cursor = 0.0;
        for (int i = 0; i < Math.min(fractions.size(), colors.size()); i++) {
            double f = fractions.get(i);
            if (f <= 0) {
                continue;
            }
            int x0 = (int) Math.round(getX() + cursor * getWidth());
            int x1 = (int) Math.min(getX() + getWidth(), Math.round(getX() + (cursor + f) * getWidth()));
            int color = isHoveredOrFocused() ? colors.get(i) : blend(colors.get(i));
            graphics.fill(x0, getY(), x1, getY() + getHeight(), color);
            cursor += f;
        }
    }

    /** Slightly dims a segment when not hovered so the active bar reads clearly. */
    private static int blend(final int argb) {
        int a = (argb >>> 24) * 85 / 100;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
