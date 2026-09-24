package com.goplej.upgrader.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screenhandler.v1.HandledScreens;

import com.goplej.upgrader.UpgraderMod;

public class UpgraderClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(UpgraderMod.UPGRADER_SCREEN_HANDLER, UpgraderScreen::new);
    }
}
