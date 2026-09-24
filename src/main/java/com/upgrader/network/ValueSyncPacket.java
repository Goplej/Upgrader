package com.upgrader.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C: pushes the override table + curve parameters to clients so the GUI previews match the
 * server exactly. Payload is one NBT compound (versioned inside) to keep the codec trivial and
 * debuggable.
 */
public final class ValueSyncPacket {

    /** Latest client cache of synced config; read by the GUI layer. */
    private static volatile CompoundTag latest = new CompoundTag();

    private final CompoundTag payload;

    public ValueSyncPacket(final CompoundTag payload) {
        this.payload = payload;
    }

    public static void encode(final ValueSyncPacket pkt, final FriendlyByteBuf buf) {
        buf.writeNbt(pkt.payload);
    }

    public static ValueSyncPacket decode(final FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new ValueSyncPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(final ValueSyncPacket pkt, final Supplier<NetworkEvent.Context> ctxSupplier) {
        latest = pkt.payload;
        ctxSupplier.get().setPacketHandled(true);
    }

    public static CompoundTag latest() {
        return latest;
    }

    /** Convenience snapshot builder used server-side when broadcasting on login/reload. */
    public static Map<String, Long> overridesFromTag() {
        Map<String, Long> out = new HashMap<>();
        CompoundTag ovr = latest.getCompound("overrides");
        for (String key : ovr.getAllKeys()) {
            out.put(key, ovr.getLong(key));
        }
        return out;
    }
}
