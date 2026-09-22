package com.example.upgradermod.client;

import com.example.upgradermod.UpgraderConstants;
import com.example.upgradermod.menu.UpgraderMenu;
import com.example.upgradermod.network.SpinResultPacket;
import com.example.upgradermod.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * Client side entry point.
 *
 * <p>Registered with {@code Dist.CLIENT} on the mod event bus, therefore this class (and everything
 * it references) is never loaded on a dedicated server.</p>
 */
@Mod.EventBusSubscriber(modid = UpgraderConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Binds the menu type to its screen.
     *
     * @param event client setup event
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                MenuScreens.register(ModMenus.UPGRADER_MENU.get(), UpgraderScreen::new);
                LOGGER.info("Upgrader screen registered for {}", ModMenus.UPGRADER_MENU.getId());
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader could not register its screen", throwable);
            }
        });
    }

    /**
     * Registers the two key mappings.
     *
     * @param event key mapping registration event
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        try {
            event.register(KeyBindings.SPIN);
            event.register(KeyBindings.OPEN_CATALOG);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not register its key mappings", throwable);
        }
    }

    /**
     * Routes a spin result to the open screen. Called from the network thread work queue on the
     * client only.
     *
     * @param packet the received packet
     */
    public static void handleSpinResult(SpinResultPacket packet) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof UpgraderScreen screen) {
                screen.handleSpinResult(packet);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not apply a spin result to the screen", throwable);
        }
    }

    /**
     * Small helper used by the key bindings to find the open menu.
     *
     * @return the open Upgrader menu, or {@code null}
     */
    public static UpgraderMenu openMenuOrNull() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof UpgraderScreen screen) {
            return screen.getMenu();
        }
        return null;
    }

    private ClientSetup() {
        // Static access only.
    }
}
