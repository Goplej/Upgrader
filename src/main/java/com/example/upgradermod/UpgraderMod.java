package com.example.upgradermod;

import com.example.upgradermod.config.UpgraderConfig;
import com.example.upgradermod.logic.ItemRegistryCache;
import com.example.upgradermod.logic.ValueCalculator;
import com.example.upgradermod.logic.ValueProviderRegistry;
import com.example.upgradermod.network.NetworkHandler;
import com.example.upgradermod.registry.ModItems;
import com.example.upgradermod.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point of the Upgrader mod.
 *
 * <p>Client only classes are never referenced from here: the client side lives in
 * {@code com.example.upgradermod.client.ClientSetup}, which is subscribed with
 * {@code Dist.CLIENT} so a dedicated server never loads it.</p>
 */
@Mod(UpgraderConstants.MOD_ID)
public class UpgraderMod {

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Forge injects the mod's own loading context into this constructor (supported since the
     * 1.20.1 backport in Forge 47.3, so it is available on the targeted 47.4.23). Using it avoids
     * the {@code FMLJavaModLoadingContext.get()} static lookup, which is deprecated for removal.
     *
     * @param context loading context of this mod
     */
    public UpgraderMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onBuildCreativeModeTabContents);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Upgrader by {} initialising for Minecraft 1.20.1 / Forge", UpgraderConstants.AUTHOR);
    }

    /**
     * Runs once both registries are frozen. Everything that touches the value engine or the network
     * layer is deferred onto the main thread through {@link FMLCommonSetupEvent#enqueueWork}.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                NetworkHandler.register();
                UpgraderConfig.reload();
                ValueProviderRegistry.bootstrap();
                ItemRegistryCache.rebuild();
                LOGGER.info("Upgrader ready: {} value providers, fallback={}, max price={}, log threshold={}",
                        ValueProviderRegistry.getProviders().size(),
                        UpgraderConstants.FALLBACK_PRICE,
                        UpgraderConstants.MAX_PRICE,
                        UpgraderConstants.LOG_THRESHOLD);
            } catch (Throwable throwable) {
                // Never let mod initialisation kill the game; the engine degrades to heuristics.
                LOGGER.error("Upgrader initialisation failed, value engine may be incomplete", throwable);
            }
        });
    }

    /** Adds the Upgrader item to the vanilla tools &amp; utilities creative tab. */
    private void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.UPGRADER, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** Tags can change on datapack reload; cached item/tag lookups must be dropped. */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        try {
            ItemRegistryCache.invalidate();
            ValueCalculator.clearCache();
            UpgraderConfig.reload();
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader failed to refresh its caches after a tag update", throwable);
        }
    }
}
