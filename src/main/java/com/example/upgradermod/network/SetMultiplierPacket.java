package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Client &rarr; server: the bet multiplier.
 *
 * <p>The value is clamped server side, so a modified client cannot request an out of range
 * multiplier.</p>
 */
public class SetMultiplierPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final int multiplier;

    /**
     * @param multiplier requested bet multiplier
     */
    public SetMultiplierPacket(int multiplier) {
        this.multiplier = multiplier;
    }

    /** @return the requested multiplier */
    public int getMultiplier() {
        return this.multiplier;
    }

    /**
     * Writes the packet.
     *
     * @param message packet instance
     * @param buffer  target buffer
     */
    public static void encode(SetMultiplierPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.multiplier);
    }

    /**
     * Reads the packet.
     *
     * @param buffer source buffer
     * @return a new packet instance
     */
    public static SetMultiplierPacket decode(FriendlyByteBuf buffer) {
        return new SetMultiplierPacket(buffer.readInt());
    }

    /**
     * Handles the packet on the main server thread.
     *
     * @param message         packet instance
     * @param contextSupplier network context supplier
     */
    public static void handle(SetMultiplierPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    return;
                }
                if (sender.containerMenu instanceof UpgraderMenu menu) {
                    menu.setMultiplier(message.multiplier);
                } else {
                    LOGGER.debug("Upgrader ignored a multiplier update from {}: the upgrader menu is not open",
                            sender.getGameProfile().getName());
                }
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader failed to handle a set multiplier packet", throwable);
            }
        });
        context.setPacketHandled(true);
    }
}
