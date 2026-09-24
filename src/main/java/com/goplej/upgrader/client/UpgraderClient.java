package com.goplej.upgrader.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

import com.goplej.upgrader.UpgraderMod;

public class UpgraderClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Public in vanilla thanks to Fabric's transitive access wideners.
        HandledScreens.register(UpgraderMod.UPGRADER_SCREEN_HANDLER, UpgraderScreen::new);
    }
}
