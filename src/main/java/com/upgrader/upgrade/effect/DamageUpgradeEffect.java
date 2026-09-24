package com.upgrader.upgrade.effect;

import com.upgrader.Upgrader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

import java.util.UUID;

/**
 * +15% base attack damage per upgrade level, expressed as one permanent item attribute modifier
 * that is rewritten (not stacked) on every level change — keeping NBT bounded at any level.
 */
public final class DamageUpgradeEffect implements UpgradeEffect {

    private static final ResourceLocation ID = new ResourceLocation(Upgrader.MODID, "damage");
    /** Fixed UUID so re-applying replaces the old modifier instead of duplicating it. */
    private static final UUID MODIFIER_ID = UUID.fromString("3f2a9c6e-1d4b-5c8a-9e0f-1234567890ab");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean isApplicableTo(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    @Override
    public void apply(ItemStack stack, int level, UpgradeContext ctx) {
        try {
            double amount = 0.15D * level;
            stack.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(MODIFIER_ID, "upgrader_damage", amount,
                            AttributeModifier.Operation.MULTIPLY_BASE),
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Damage effect skipped for {}", stack, t);
        }
    }
}
