package com.goplej.upgrader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgraderMod implements ModInitializer {
    public static final String MOD_ID = "upgrader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static final Item UPGRADER_ITEM = Registry.register(
            Registries.ITEM,
            id("upgrader"),
            new UpgraderItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final ScreenHandlerType<UpgraderScreenHandler> UPGRADER_SCREEN_HANDLER = registerScreenHandler();

    private static ScreenHandlerType<UpgraderScreenHandler> registerScreenHandler() {
        ScreenHandlerType<UpgraderScreenHandler> type = new ScreenHandlerType<UpgraderScreenHandler>(
                (syncId, inventory, buf) -> new UpgraderScreenHandler(syncId, inventory), FeatureSet.empty());
        return Registry.register(Registries.SCREEN_HANDLER, id("upgrader"), type);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(UPGRADER_ITEM));

        ServerPlayNetworking.registerGlobalReceiver(UpgraderNetworking.SELECT_UPGRADE, (server, player, handler, buf, responseSender) -> {
            int optionIndex = buf.readVarInt();
            server.execute(() -> {
                if (player.currentScreenHandler instanceof UpgraderScreenHandler screenHandler) {
                    screenHandler.tryApplyUpgrade(player, optionIndex);
                }
            });
        });

        LOGGER.info("Upgrader initialized");
    }
}
