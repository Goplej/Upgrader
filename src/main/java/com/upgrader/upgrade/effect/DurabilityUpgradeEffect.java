package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

/**
 * +12% max durability per level, implemented as an Unbreakable-free bonus stored under our own
 * root tag ({@code durability_bonus}) that vanilla-aware consumers can read without capabilities.
 * Tools with existing Unbreakable keep it untouched.
 */
public final class DurabilityUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "durability");
    public static final String TAG_BONUS = "durability_bonus";

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return stack.getMaxDamage() > 0;
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            long bonus = Math.round(stack.getMaxDamage() * 0.12D * level);
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putLong(TAG_BONUS, bonus);
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Durability effect skipped for {}", stack, t);
        }
    }
}
