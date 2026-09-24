package com.upgrader.value.source;

import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;

import java.util.Optional;

/**
 * Priority-40: prices items from intrinsic properties — durability, armor defense, food value,
 * tool tier enchantability. These are structural facts of the item itself, not a lookup table.
 */
public final class ItemPropertyValueSource implements ValueSource {

    public static final String NAME = "upgrader:properties";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public Confidence baseConfidence() {
        return Confidence.MEDIUM;
    }

    @Override
    public boolean isApplicable(ItemStack stack, ValueContext context) {
        return !context.isSourceUsed(SourceType.RECIPE) && !context.isSourceUsed(SourceType.COMPONENT);
    }

    @Override
    public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
        long value = 0L;
        StringBuilder why = new StringBuilder();

        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            // Durability is the dominant property signal: log-scaled so 1561 (netherite) doesn't
            // dwarf 64 (iron) linearly.
            long dur = Math.round(Math.log1p(maxDamage) * 8.0);
            value += dur;
            why.append(String.format("durability %d (+%d)", maxDamage, dur));
        }
        if (stack.getItem() instanceof TieredItem tiered) {
            try {
                Tier tier = tiered.getTier();
                long tierBonus = 16L * (tier.getLevel() + 1L);
                value += tierBonus;
                if (why.length() > 0) why.append(", ");
                why.append(String.format("tool tier %d (+%d)", tier.getLevel(), tierBonus));
            } catch (Throwable ignored) {
            }
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            try {
                ArmorMaterial mat = armor.getMaterial();
                long def = 24L * armorDefense(mat, armor);
                value += def;
                if (why.length() > 0) why.append(", ");
                why.append(String.format("armor defense (+%d)", def));
            } catch (Throwable ignored) {
            }
        }
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food != null) {
            long nutrition = 3L * Math.max(1, food.getNutrition());
            value += nutrition;
            if (why.length() > 0) why.append(", ");
            why.append(String.format("food nutrition %d (+%d)", food.getNutrition(), nutrition));
        }

        if (value == 0L) {
            return Optional.empty();
        }
        return Optional.of(new ValueContribution(NAME, SourceType.PROPERTY, value, why.toString(),
                Confidence.MEDIUM.weight()));
    }

    /** Reads {@code ArmorMaterial#getDefenseForType(EquipmentSlotType)} reflectively so this
     * compiles and works across mappings that rename or hide the accessor. */
    private static int armorDefense(net.minecraft.world.item.ArmorMaterial mat,
                                    net.minecraft.world.item.ArmorItem armor) {
        try {
            for (java.lang.reflect.Method m : mat.getClass().getMethods()) {
                if (m.getName().startsWith("getDefenseForType") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isEnum()) {
                    Enum<?> slot = (Enum<?>) m.getParameterTypes()[0]
                            .getMethod("valueOf", String.class)
                            .invoke(null, armor.getSlot().getName().toUpperCase(java.util.Locale.ROOT));
                    Object r = m.invoke(mat, slot);
                    return r instanceof Number n ? n.intValue() : 0;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
