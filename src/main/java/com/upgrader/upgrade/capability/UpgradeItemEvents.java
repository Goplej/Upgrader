package com.upgrader.upgrade.capability;

import com.upgrader.Upgrader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires {@link UpgradeCapabilityProvider} onto every ItemStack via the Forge bus.
 *
 * <p>Attaching to all stacks is intentional: the provider costs one small object per stack and
 * any mod's item can legitimately be upgraded (the engine prices whatever it is given). The
 * capability state round-trips through the stack's own NBT under the {@code upgrader} root key,
 * so persistence works without a custom serializer on foreign items.</p>
 */
@Mod.EventBusSubscriber(modid = Upgrader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgradeItemEvents {

    private UpgradeItemEvents() {
    }

    @SubscribeEvent
    public static void onAttachItemCaps(final AttachCapabilitiesEvent<ItemStack> event) {
        event.addCapability(UpgradeCapabilities.UPGRADEABLE_ID,
                new UpgradeCapabilityProvider(event.getObject()));
    }

    /** Helper used by the upgrade engine to persist level changes onto the stack NBT directly. */
    public static void writeLevelToStack(ItemStack stack, int level, long baseSnapshot) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
        root.putInt(UpgradeCapability.TAG_LEVEL, Math.max(0, level));
        if (baseSnapshot >= 0) {
            root.putLong(UpgradeCapability.TAG_BASE_SNAPSHOT, baseSnapshot);
        }
        tag.put(UpgradeCapability.TAG_ROOT, root);
    }
}
