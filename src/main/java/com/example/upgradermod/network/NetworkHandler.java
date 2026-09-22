package com.example.upgradermod.network;

import com.example.upgradermod.UpgraderConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * Network layer (Section 3.2).
 *
 * <p>Channel {@code upgradermod:main}, protocol version {@code "1"}. Four packets are registered:</p>
 * <ul>
 *     <li>{@link SpinPacket} (C&rarr;S) &ndash; trigger a spin;</li>
 *     <li>{@link SpinResultPacket} (S&rarr;C) &ndash; outcome, chance and needle angle;</li>
 *     <li>{@link SetTargetPacket} (C&rarr;S) &ndash; chosen target stack;</li>
 *     <li>{@link SetMultiplierPacket} (C&rarr;S) &ndash; bet multiplier.</li>
 * </ul>
 */
public final class NetworkHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static SimpleChannel channel;

    /**
     * Creates the channel and registers every packet. Idempotent.
     */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }

        SimpleChannel instance = NetworkRegistry.newSimpleChannel(
                UpgraderConstants.CHANNEL,
                () -> UpgraderConstants.PROTOCOL_VERSION,
                UpgraderConstants.PROTOCOL_VERSION::equals,
                UpgraderConstants.PROTOCOL_VERSION::equals);

        int index = 0;
        instance.registerMessage(index++, SpinPacket.class,
                SpinPacket::encode, SpinPacket::decode, SpinPacket::handle);
        instance.registerMessage(index++, SpinResultPacket.class,
                SpinResultPacket::encode, SpinResultPacket::decode, SpinResultPacket::handle);
        instance.registerMessage(index++, SetTargetPacket.class,
                SetTargetPacket::encode, SetTargetPacket::decode, SetTargetPacket::handle);
        instance.registerMessage(index++, SetMultiplierPacket.class,
                SetMultiplierPacket::encode, SetMultiplierPacket::decode, SetMultiplierPacket::handle);

        channel = instance;
        LOGGER.info("Upgrader network channel {} registered with protocol '{}' and {} messages",
                UpgraderConstants.CHANNEL, UpgraderConstants.PROTOCOL_VERSION, index);
    }

    /**
     * Sends a packet to the server. Client side.
     *
     * @param message packet to send
     */
    public static void sendToServer(Object message) {
        if (!ensureRegistered() || message == null) {
            return;
        }
        try {
            channel.sendToServer(message);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not send {} to the server", message.getClass().getSimpleName(), throwable);
        }
    }

    /**
     * Sends a packet to a single player. Server side.
     *
     * @param message packet to send
     * @param player  recipient
     */
    public static void sendToPlayer(Object message, ServerPlayer player) {
        if (!ensureRegistered() || message == null || player == null) {
            return;
        }
        try {
            channel.send(PacketDistributor.PLAYER.with(() -> player), message);
        } catch (Throwable throwable) {
            LOGGER.error("Upgrader could not send {} to {}", message.getClass().getSimpleName(),
                    player.getGameProfile().getName(), throwable);
        }
    }

    private static boolean ensureRegistered() {
        if (channel == null) {
            register();
        }
        return channel != null;
    }

    private NetworkHandler() {
        // Static access only.
    }
}
