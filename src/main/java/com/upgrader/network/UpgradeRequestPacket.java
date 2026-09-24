package com.upgrader.network;

import com.upgrader.upgrade.InventoryValueBank;
import com.upgrader.upgrade.UpgradeEngine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: "upgrade the stack in my main hand". Deliberately carries <strong>no</strong> item data —
 * the server re-reads the live slot, so a tampered client cannot upgrade something it does not
 * hold. Handling runs on the main thread via enqueueWork.
 */
public final class UpgradeRequestPacket {

    /** Empty payload; kept as a field for forward compatibility (future shift-click mass mode). */
    private final int requestedSlot;

    public UpgradeRequestPacket(int requestedSlot) {
        this.requestedSlot = requestedSlot;
    }

    public static void encode(final UpgradeRequestPacket pkt, final FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.requestedSlot);
    }

    public static UpgradeRequestPacket decode(final FriendlyByteBuf buf) {
        return new UpgradeRequestPacket(buf.readVarInt());
    }

    public static void handle(final UpgradeRequestPacket pkt,
                              final Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            var stack = player.getMainHandItem();
            var bank = new InventoryValueBank(player);
            UpgradeEngine.Result result = UpgradeEngine.tryUpgrade(stack, bank);
            player.displayClientMessage(Component.translatable(result.reasonKey()), true);
        });
        ctx.setPacketHandled(true);
    }
}
