package com.example.upgradermod.network;

import com.example.upgradermod.menu.UpgraderMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Client &rarr; server: the target item chosen in the catalogue.
 *
 * <p>The server keeps its own copy (count normalised to one) inside the container, so a modified
 * client can never inject a stack that is not written into the menu.</p>
 */
public class SetTargetPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ItemStack target;

    /**
     * @param target the wanted stack, an empty stack clears the target
     */
    public SetTargetPacket(ItemStack target) {
        this.target = target == null ? ItemStack.EMPTY : target;
    }

    /** @return the requested target stack */
    public ItemStack getTarget() {
        return this.target;
    }

    /**
     * Writes the packet.
     *
     * @param message packet instance
     * @param buffer  target buffer
     */
    public static void encode(SetTargetPacket message, FriendlyByteBuf buffer) {
        buffer.writeItem(message.target);
    }

    /**
     * Reads the packet.
     *
     * @param buffer source buffer
     * @return a new packet instance
     */
    public static SetTargetPacket decode(FriendlyByteBuf buffer) {
        return new SetTargetPacket(buffer.readItem());
    }

    /**
     * Handles the packet on the main server thread.
     *
     * @param message         packet instance
     * @param contextSupplier network context supplier
     */
    public static void handle(SetTargetPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer sender = context.getSender();
                if (sender == null) {
                    return;
                }
                if (sender.containerMenu instanceof UpgraderMenu menu) {
                    menu.setTarget(message.target);
                } else {
                    LOGGER.debug("Upgrader ignored a target update from {}: the upgrader menu is not open",
                            sender.getGameProfile().getName());
                }
            } catch (Throwable throwable) {
                LOGGER.error("Upgrader failed to handle a set target packet", throwable);
            }
        });
        context.setPacketHandled(true);
    }
}
