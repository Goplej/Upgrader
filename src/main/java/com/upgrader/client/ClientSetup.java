package com.upgrader.client;

import com.upgrader.Upgrader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client lifecycle hooks kept separate so common code never references client classes. */
@Mod.EventBusSubscriber(modid = Upgrader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        Upgrader.LOGGER.info("Upgrader client initialised");
    }
}
