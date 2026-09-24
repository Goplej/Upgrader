package com.upgrader.network;

import com.upgrader.Upgrader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * SimpleChannel bootstrap ("upgrader:main", single protocol version bumped manually alongside
 * packet layout changes). Registration happens from enqueueWork in common setup per Forge docs.
 */
public final class UpgraderPayloadHandler {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Upgrader.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private UpgraderPayloadHandler() {
    }

    public static void register() {
        INSTANCE.messageBuilder(UpgradeRequestPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpgradeRequestPacket::encode)
                .decoder(UpgradeRequestPacket::decode)
                .consumerMainThread(UpgradeRequestPacket::handle)
                .add();
        INSTANCE.messageBuilder(ValueSyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ValueSyncPacket::encode)
                .decoder(ValueSyncPacket::decode)
                .consumerMainThread(ValueSyncPacket::handle)
                .add();
        Upgrader.LOGGER.debug("Network channel upgrader:main ready (2 messages)");
    }
}
