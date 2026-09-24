package com.upgrader;

import com.mojang.logging.LogUtils;
import com.upgrader.api.UpgraderAPI;
import com.upgrader.config.UpgraderConfig;
import com.upgrader.integration.IntegrationManager;
import com.upgrader.network.UpgraderPayloadHandler;
import com.upgrader.recipe.RecipeIndex;
import com.upgrader.registry.UpgraderItems;
import com.upgrader.upgrade.UpgradeEffectRegistry;
import com.upgrader.upgrade.capability.UpgradeCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Upgrader — a universal item-value engine and upgrade system for Forge 1.20.1.
 *
 * <p>The class is intentionally thin: it only wires lifecycle events to the subsystems
 * (value engine, recipe index, integrations, network, registries). All gameplay logic lives in
 * those packages so each piece can be tested and replaced independently.</p>
 */
@Mod(Upgrader.MODID)
public final class Upgrader {

    public static final String MODID = "upgrader";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Incremented on every datapack reload; lets lazily-built indexes detect staleness. */
    public static volatile long reloadGeneration = 0L;

    public Upgrader() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                UpgraderConfig.Common.SPEC, "upgrader-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                UpgraderConfig.Client.SPEC, "upgrader-client.toml");
        UpgraderItems.register(modBus);
        UpgradeCapabilities.init();
        UpgraderAPI.markLoaded();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onDataReload);
        MinecraftForge.EVENT_BUS.register(com.upgrader.command.UpgraderCommand.class);
        MinecraftForge.EVENT_BUS.register(com.upgrader.cache.CacheInvalidationListener.class);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            UpgraderAPI.bootstrapDefaults();   // priority chain: integration > recipe > ... > fallback
            IntegrationManager.registerBuiltins();
            IntegrationManager.initAvailable(); // safe: catches every Throwable per provider
            UpgradeEffectRegistry.registerBuiltins();
            UpgraderPayloadHandler.register();
            LOGGER.info("Upgrader {} common setup complete", MODID);
        });
    }

    /** `/reload` and datapack sync bump the generation so the recipe index rebuilds lazily. */
    private void onDataReload(final AddReloadListenerEvent event) {
        RecipeIndex.currentGeneration = ++reloadGeneration;
        Upgrader.LOGGER.debug("Datapack reload detected, recipe index generation -> {}", reloadGeneration);
    }
}
