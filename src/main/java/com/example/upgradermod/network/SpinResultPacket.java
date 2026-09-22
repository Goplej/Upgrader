package com.example.upgradermod.network;

import com.example.upgradermod.client.ClientSetup;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Server &rarr; client: the outcome of a spin.
 *
 * @see com.example.upgradermod.menu.UpgraderMenu#doSpin(net.minecraft.server.level.ServerPlayer)
 */
public class SpinResultPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean success;
    private final double chance;
    private final float rollAngle;

    /**
     * @param success   {@code true} when the target item was granted
     * @param chance    the chance in percent that was rolled against
     * @param rollAngle compass angle in degrees the needle has to land on
     */
    public SpinResultPacket(boolean success, double chance, float rollAngle) {
        this.success = success;
        this.chance = chance;
        this.rollAngle = rollAngle;
    }

    /** @return {@code true} when the spin succeeded */
    public boolean isSuccess() {
        return this.success;
    }

    /** @return the chance in percent */
    public double getChance() {
        return this.chance;
    }

    /** @return the compass angle in degrees */
    public float getRollAngle() {
        return this.rollAngle;
    }

    /**
     * Writes the packet.
     *
     * @param message packet instance
     * @param buffer  target buffer
     */
    public static void encode(SpinResultPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeDouble(message.chance);
        buffer.writeFloat(message.rollAngle);
    }

    /**
     * Reads the packet.
     *
     * @param buffer source buffer
     * @return a new packet instance
     */
    public static SpinResultPacket decode(FriendlyByteBuf buffer) {
        return new SpinResultPacket(buffer.readBoolean(), buffer.readDouble(), buffer.readFloat());
    }

    /**
     * Handles the packet on the client. The client class is reached through
     * {@link DistExecutor#unsafeRunWhenOn} so a dedicated server never links against it.
     *
     * @param message         packet instance
     * @param contextSupplier network context supplier
     */
    public static void handle(SpinResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.handleSpinResult(message));
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader failed to handle a spin result packet", throwable);
            }
        });
        context.setPacketHandled(true);
    }
}
