package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import com.upgrader.upgrade.capability.UpgradeCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;

/**
 * Mining-speed tier: records {@code efficiency_level = level/2} in our root tag; a Forge event
 * handler (BreakEvent consumer in the runtime package) reads it to boost dig speed without
 * touching real enchantments, so it never conflicts with Efficiency V from other mods.
 */
public final class EfficiencyUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "efficiency");
    public static final String TAG_EFFICIENCY = "efficiency_level";

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag root = tag.getCompound(UpgradeCapability.TAG_ROOT);
            root.putInt(TAG_EFFICIENCY, level / 2);
            tag.put(UpgradeCapability.TAG_ROOT, root);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Efficiency effect skipped for {}", stack, t);
        }
    }
}
