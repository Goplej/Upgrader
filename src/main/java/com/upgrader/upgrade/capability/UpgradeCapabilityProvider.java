package com.upgrader.upgrade.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Attaches {@link IUpgradeableItem} to every ItemStack. The LazyOptional is invalidated when the
 * provider is removed so no dangling cache can outlive its stack.
 */
public final class UpgradeCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    private final UpgradeCapability capability = new UpgradeCapability();
    private final LazyOptional<IUpgradeableItem> optional = LazyOptional.of(() -> capability);

    public UpgradeCapabilityProvider(ItemStack stack) {
        // Hydrate from any pre-existing NBT (e.g. items received before this mod was installed).
        if (stack != null && stack.hasTag()) {
            CompoundTag root = stack.getTag().getCompound(UpgradeCapability.TAG_ROOT);
            if (!root.isEmpty()) {
                capability.deserializeNBT(root);
            }
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return UpgradeCapabilities.UPGRADEABLE.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return capability.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        capability.deserializeNBT(nbt);
    }

    /** Invalidates the cached LazyOptional (called by the holder on copy/unload). */
    public void invalidate() {
        optional.invalidate();
    }
}
