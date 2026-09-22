package com.example.upgradermod.client;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.logic.ChanceCalculator;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.network.SpinPacket;
import com.example.upgradermod.network.SpinResultPacket;
import com.example.upgradermod.network.SetMultiplierPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * The Upgrader screen (Section 4).
 *
 * <p>Dark theme, drawn completely procedurally &ndash; no texture files are required. The compass on
 * the right shows the roll: 60 minor ticks every 6&deg;, 12 major ticks every 30&deg;, radius 55,
 * centred at (190, 130). It scales from 0.75 to 1.0 over 250&nbsp;ms with an ease-out curve when the
 * screen opens.</p>
 */
public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Width of the drawn area. */
    public static final int IMAGE_WIDTH = 256;

    /** Height of the drawn area. */
    public static final int IMAGE_HEIGHT = 270;

    /** Compass centre, x. */
    public static final int COMPASS_X = 190;

    /** Compass centre, y. */
    public static final int COMPASS_Y = 130;

    /** Compass radius. */
    public static final int COMPASS_RADIUS = 55;

    /** Number of minor ticks, one every 6 degrees. */
    public static final int COMPASS_TICKS = 60;

    /** Number of major ticks, one every 30 degrees. */
    public static final int COMPASS_MAJOR_TICKS = 12;

    /** Background colour (ARGB). */
    public static final int COLOR_BACKGROUND = 0xFF1a1a2e;

    /** Panel colour (ARGB). */
    public static final int COLOR_PANEL = 0xFF16213e;

    /** Slot colour (ARGB). */
    public static final int COLOR_SLOT = 0xFF0f3460;

    /** Border colour (ARGB). */
    public static final int COLOR_BORDER = 0xFF533483;

    /** Gold accent (ARGB). */
    public static final int COLOR_GOLD = 0xFFffd700;

    /** Orange accent (ARGB). */
    public static final int COLOR_ACCENT = 0xFFff9800;

    /** Text colour (ARGB). */
    public static final int COLOR_TEXT = 0xFFe8e8e8;

    /** Success colour (ARGB), a brightened gold. */
    public static final int COLOR_SUCCESS = 0xFF7CFC00;

    /** Failure colour (ARGB). */
    public static final int COLOR_FAILURE = 0xFFff5555;

    /** Intro animation duration in milliseconds. */
    public static final long INTRO_DURATION_MS = 250L;

    /** Starting scale of the intro animation. */
    public static final float INTRO_START_SCALE = 0.75F;

    /** Duration of the free spin started while the server decides. */
    public static final long FREE_SPIN_MS = 1500L;

    /** Duration of the landing animation once the result is known. */
    public static final long RESULT_SPIN_MS = 2000L;

    /** Turns added on top of the final angle. */
    public static final float EXTRA_TURNS = 1080.0F;

    private final long openedAt = Util.getMillis();

    private Button spinButton;
    private Button catalogButton;
    private Button multiplierDownButton;
    private Button multiplierUpButton;

    private boolean awaitingResult;
    private Boolean lastSuccess;
    private double lastChance;

    private float angleFrom;
    private float angleTo;
    private long angleStartedAt;
    private long angleDurationMs;

    /**
     * @param menu            the container
     * @param playerInventory inventory of the local player
     * @param title           screen title
     */
    public UpgraderScreen(UpgraderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.titleLabelX = 10;
        this.titleLabelY = 10;
        this.inventoryLabelX = UpgraderMenu.PLAYER_INV_X - 1;
        this.inventoryLabelY = UpgraderMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();

        int left = this.leftPos;
        int top = this.topPos;

        this.spinButton = this.addRenderableWidget(Button
                .builder(Component.translatable("upgradermod.gui.spin"), button -> this.requestSpin())
                .bounds(left + 24, top + 154, 100, 18)
                .build());

        this.catalogButton = this.addRenderableWidget(Button
                .builder(Component.translatable("upgradermod.gui.catalog"), button -> this.openCatalog())
                .bounds(left + 24, top + 104, 100, 16)
                .build());

        this.multiplierDownButton = this.addRenderableWidget(Button
                .builder(Component.literal("-"), button -> this.changeMultiplier(-1))
                .bounds(left + 24, top + 134, 16, 16)
                .build());

        this.multiplierUpButton = this.addRenderableWidget(Button
                .builder(Component.literal("+"), button -> this.changeMultiplier(1))
                .bounds(left + 70, top + 134, 16, 16)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        // Base plate and outer border.
        guiGraphics.fill(left, top, left + IMAGE_WIDTH, top + IMAGE_HEIGHT, COLOR_BACKGROUND);
        guiGraphics.renderOutline(left, top, IMAGE_WIDTH, IMAGE_HEIGHT, COLOR_BORDER);

        // Control panel on the left.
        guiGraphics.fill(left + 16, top + 24, left + 130, top + 178, COLOR_PANEL);
        guiGraphics.renderOutline(left + 16, top + 24, 114, 154, COLOR_BORDER);

        // Slot plates.
        for (Slot slot : this.menu.slots) {
            int slotX = left + slot.x;
            int slotY = top + slot.y;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, COLOR_SLOT);
            guiGraphics.renderOutline(slotX - 1, slotY - 1, 18, 18, COLOR_BORDER);
        }

        // Slot captions.
        guiGraphics.drawString(this.font, Component.translatable("upgradermod.gui.input"),
                left + 24, top + 30, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("upgradermod.gui.target"),
                left + 24, top + 68, COLOR_TEXT, false);

        // Values.
        ItemStack input = this.menu.getDisplayedInput();
        ItemStack target = this.menu.getDisplayedTarget();

        guiGraphics.drawString(this.font,
                Component.translatable("upgradermod.gui.value", formatValue(this.menu.getInputValue())),
                left + 58, top + 47, input.isEmpty() ? COLOR_BORDER : COLOR_ACCENT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("upgradermod.gui.value", formatValue(this.menu.getTargetValue())),
                left + 58, top + 85, target.isEmpty() ? COLOR_BORDER : COLOR_GOLD, false);

        // Multiplier row.
        guiGraphics.drawString(this.font, Component.translatable("upgradermod.gui.multiplier"),
                left + 44, top + 122, COLOR_TEXT, false);
        guiGraphics.drawCenteredString(this.font, "x" + this.menu.getMultiplier(),
                left + 55, top + 138, COLOR_GOLD);

        // Chance read-out above the compass.
        double chance = this.lastSuccess == null ? this.menu.getChance() : this.lastChance;
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("upgradermod.gui.chance", ChanceCalculator.format(chance)),
                left + COMPASS_X, top + 46, COLOR_GOLD);

        guiGraphics.drawCenteredString(this.font, this.statusText(),
                left + COMPASS_X, top + 58, this.statusColor());

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("upgradermod.gui.ratio",
                        formatValue(this.menu.getInputValue()), formatValue(this.menu.getTargetValue())),
                left + COMPASS_X, top + 192, COLOR_TEXT);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("upgradermod.gui.hint.key"),
                left + COMPASS_X, top + 204, COLOR_BORDER);

        this.drawCompass(guiGraphics, left, top);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COLOR_GOLD, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, COLOR_TEXT, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.spinButton != null) {
            this.spinButton.active = !this.awaitingResult;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        try {
            if (KeyBindings.SPIN.matches(keyCode, scanCode)) {
                this.requestSpin();
                return true;
            }
            if (KeyBindings.OPEN_CATALOG.matches(keyCode, scanCode)) {
                this.openCatalog();
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader key handling failed", throwable);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Applies a spin result coming from the server.
     *
     * @param packet the result packet
     */
    public void handleSpinResult(SpinResultPacket packet) {
        this.awaitingResult = false;
        this.lastSuccess = packet.isSuccess();
        this.lastChance = packet.getChance();

        float current = this.currentAngle();
        float finalAngle = ((packet.getRollAngle() % 360.0F) + 360.0F) % 360.0F;
        float currentNormalised = ((current % 360.0F) + 360.0F) % 360.0F;
        float delta = ((finalAngle - currentNormalised) + 360.0F) % 360.0F + EXTRA_TURNS;

        this.angleFrom = current;
        this.angleTo = current + delta;
        this.angleStartedAt = Util.getMillis();
        this.angleDurationMs = RESULT_SPIN_MS;
    }

    /** Sends the spin request and starts the free spinning animation. */
    public void requestSpin() {
        if (this.awaitingResult) {
            return;
        }

        try {
            this.awaitingResult = true;
            this.lastSuccess = null;
            this.lastChance = this.menu.getChance();

            this.angleFrom = this.currentAngle();
            this.angleTo = this.angleFrom + EXTRA_TURNS;
            this.angleStartedAt = Util.getMillis();
            this.angleDurationMs = FREE_SPIN_MS;

            NetworkHandler.sendToServer(new SpinPacket());
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not request a spin", throwable);
            this.awaitingResult = false;
        }
    }

    /** Opens the item catalogue on top of this screen. */
    public void openCatalog() {
        try {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new CatalogScreen(this));
            }
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not open the catalogue", throwable);
        }
    }

    /**
     * Sends a multiplier change to the server.
     *
     * @param delta change applied to the current multiplier
     */
    public void changeMultiplier(int delta) {
        try {
            int next = Mth.clamp(this.menu.getMultiplier() + delta,
                    UpgraderConstants.MIN_MULTIPLIER, UpgraderConstants.MAX_MULTIPLIER);
            NetworkHandler.sendToServer(new SetMultiplierPacket(next));
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not change the multiplier", throwable);
        }
    }

    /**
     * Draws the compass, including the 250&nbsp;ms ease-out intro scaling.
     *
     * @param guiGraphics render target
     * @param left        screen x of the gui
     * @param top         screen y of the gui
     */
    private void drawCompass(GuiGraphics guiGraphics, int left, int top) {
        int centreX = left + COMPASS_X;
        int centreY = top + COMPASS_Y;
        float scale = this.introScale();

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centreX, centreY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-centreX, -centreY, 0.0F);

        // Dial face.
        fillCircle(guiGraphics, centreX, centreY, COMPASS_RADIUS, COLOR_PANEL);
        fillCircle(guiGraphics, centreX, centreY, COMPASS_RADIUS - 14, COLOR_BACKGROUND);

        // Minor ticks: 60 of them, one every 6 degrees.
        for (int tick = 0; tick < COMPASS_TICKS; tick++) {
            double radians = Math.toRadians(tick * (360.0D / COMPASS_TICKS));
            if (tick % (COMPASS_TICKS / COMPASS_MAJOR_TICKS) == 0) {
                drawRadialLine(guiGraphics, centreX, centreY, radians,
                        COMPASS_RADIUS - 13, COMPASS_RADIUS - 1, COLOR_GOLD);
            } else {
                drawRadialLine(guiGraphics, centreX, centreY, radians,
                        COMPASS_RADIUS - 7, COMPASS_RADIUS - 2, COLOR_ACCENT);
            }
        }

        // Needle.
        float angle = this.currentAngle();
        double needle = Math.toRadians(angle - 90.0D);
        double tail = Math.toRadians(angle + 90.0D);
        int needleColour = this.lastSuccess == null ? COLOR_TEXT
                : (this.lastSuccess ? COLOR_SUCCESS : COLOR_FAILURE);

        drawLine(guiGraphics,
                centreX + (int) Math.round(Math.cos(tail) * 12.0D),
                centreY + (int) Math.round(Math.sin(tail) * 12.0D),
                centreX + (int) Math.round(Math.cos(needle) * (COMPASS_RADIUS - 16.0D)),
                centreY + (int) Math.round(Math.sin(needle) * (COMPASS_RADIUS - 16.0D)),
                needleColour);

        guiGraphics.fill(centreX - 2, centreY - 2, centreX + 3, centreY + 3, COLOR_GOLD);

        pose.popPose();
    }

    /**
     * @return the intro scale, 0.75 &rarr; 1.0 over {@link #INTRO_DURATION_MS}
     */
    private float introScale() {
        float progress = (float) (Util.getMillis() - this.openedAt) / (float) INTRO_DURATION_MS;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        float easeOut = 1.0F - (float) Math.pow(1.0D - progress, 3.0D);
        return INTRO_START_SCALE + (1.0F - INTRO_START_SCALE) * easeOut;
    }

    /**
     * @return the needle angle in degrees, eased out towards {@link #angleTo}
     */
    private float currentAngle() {
        if (this.angleDurationMs <= 0L) {
            return this.angleTo;
        }
        float progress = (float) (Util.getMillis() - this.angleStartedAt) / (float) this.angleDurationMs;
        if (progress >= 1.0F) {
            this.angleFrom = this.angleTo;
            this.angleDurationMs = 0L;
            return this.angleTo;
        }
        float easeOut = 1.0F - (float) Math.pow(1.0D - progress, 3.0D);
        return this.angleFrom + (this.angleTo - this.angleFrom) * easeOut;
    }

    private Component statusText() {
        if (this.awaitingResult) {
            return Component.translatable("upgradermod.gui.status.spinning");
        }
        if (this.lastSuccess == null) {
            int check = this.menu.getLastCheck();
            if (check > 0) {
                return Component.translatable("upgradermod.gui.status.blocked", check);
            }
            return Component.translatable("upgradermod.gui.status.ready");
        }
        return this.lastSuccess
                ? Component.translatable("upgradermod.gui.status.success")
                : Component.translatable("upgradermod.gui.status.failure");
    }

    private int statusColor() {
        if (this.lastSuccess == null) {
            return COLOR_TEXT;
        }
        return this.lastSuccess ? COLOR_SUCCESS : COLOR_FAILURE;
    }

    /**
     * Formats a value with grouping separators.
     *
     * @param value value to format
     * @return the formatted value
     */
    public static String formatValue(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static void fillCircle(GuiGraphics guiGraphics, int centreX, int centreY, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.floor(Math.sqrt((double) (radius * radius - dy * dy)));
            guiGraphics.fill(centreX - dx, centreY + dy, centreX + dx + 1, centreY + dy + 1, colour);
        }
    }

    private static void drawRadialLine(GuiGraphics guiGraphics, int centreX, int centreY,
                                       double radians, double innerRadius, double outerRadius, int colour) {
        int startX = centreX + (int) Math.round(Math.cos(radians) * innerRadius);
        int startY = centreY + (int) Math.round(Math.sin(radians) * innerRadius);
        int endX = centreX + (int) Math.round(Math.cos(radians) * outerRadius);
        int endY = centreY + (int) Math.round(Math.sin(radians) * outerRadius);
        drawLine(guiGraphics, startX, startY, endX, endY, colour);
    }

    private static void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        int x = x0;
        int y = y0;
        while (true) {
            guiGraphics.fill(x, y, x + 1, y + 1, colour);
            if (x == x1 && y == y1) {
                break;
            }
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
        }
    }
}
