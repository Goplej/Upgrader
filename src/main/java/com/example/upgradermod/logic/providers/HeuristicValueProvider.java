package com.example.upgradermod.logic.providers;

import com.example.upgradermod.logic.ValueContext;
import com.example.upgradermod.logic.ValueProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Priority 100 &ndash; the last resort heuristics.
 *
 * <pre>
 *   base                                 1
 *   max stack size 16                   +4
 *   max stack size 1 &amp;&amp; max damage 0    +49
 *   max damage &gt; 0                      + (maxDamage / 10)
 *   rarity UNCOMMON                     +2
 *   rarity RARE                         +9
 *   rarity EPIC                        +29
 *   rarity (default branch, e.g. COMMON) +5
 * </pre>
 */
public class HeuristicValueProvider implements ValueProvider {

    /** Execution priority. */
    public static final int PRIORITY = 100;

    /** Base value of every item. */
    public static final long BASE_VALUE = 1L;

    /** Bonus for items such as ender pearls, snowballs, and fish buckets that stack to 16. */
    public static final long STACK_16_BONUS = 4L;

    /** Bonus for single, undamageable items such as ingots, gems, and food. */
    public static final long SINGLE_UNBREAKABLE_BONUS = 49L;

    /** Divisor applied to the maximum damage of tools and armour. */
    public static final int DAMAGE_DIVISOR = 10;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return "heuristic";
    }

    @Override
    public long getValue(ItemStack stack, ValueContext context) {
        try {
            if (stack == null || stack.isEmpty()) {
                return UNKNOWN;
            }

            long value = BASE_VALUE;

            int maxStackSize = stack.getMaxStackSize();
            int maxDamage = stack.getMaxDamage();

            if (maxStackSize == 16) {
                value += STACK_16_BONUS;
            }
            if (maxStackSize == 1 && maxDamage == 0) {
                value += SINGLE_UNBREAKABLE_BONUS;
            }
            if (maxDamage > 0) {
                value += (long) (maxDamage / DAMAGE_DIVISOR);
            }

            value += rarityBonus(stack.getRarity());
            return value;
        } catch (Throwable throwable) {
            return UNKNOWN;
        }
    }

    /**
     * Rarity bonus. The {@code default} branch is mandatory and covers {@link Rarity#COMMON} as well
     * as any constant a mod might add at runtime.
     *
     * @param rarity rarity of the stack, may be {@code null}
     * @return the rarity bonus
     */
    private long rarityBonus(Rarity rarity) {
        if (rarity == null) {
            return 5L;
        }
        switch (rarity) {
            case UNCOMMON:
                return 2L;
            case RARE:
                return 9L;
            case EPIC:
                return 29L;
            default:
                return 5L;
        }
    }
}
