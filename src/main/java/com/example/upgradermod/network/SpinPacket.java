package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Client &rarr; server: trigger one spin.
 *
 * <p>Carries no payload at all &ndash; everything the spin needs (input, target, multiplier) already
 * lives in the server side {@link UpgraderMenu}, which is the only authority over the outcome.</p>
 */
public class SpinPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Writes the packet.
     *
     * @param message packet instance
     * @param buffer  target buffer
     */
    public static void encode(SpinPacket message, FriendlyByteBuf buffer) {
        // Payload-less packet.
    }

    /**
     * Reads the packet.
     *
     * @param buffer source buffer
     * @return a new packet instance
     */
    public static SpinPacket decode(FriendlyByteBuf buffer) {
        return new SpinPacket();
    }

    /**
     * Handles the packet on the network thread by handing the work to the main server thread.
     *
     * @param message        packet instance
     * @param contextSupplier network context supplier
     */
    public static void handle(SpinPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    return;
                }
                if (sender.containerMenu instanceof UpgraderMenu menu) {
                    menu.doSpin(sender);
                } else {
                    LOGGER.debug("Upgrader ignored a spin request from {}: the upgrader menu is not open",
                            sender.getGameProfile().getName());
                }
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader failed to handle a spin packet", throwable);
            }
        });
        context.setPacketHandled(true);
    }
}
