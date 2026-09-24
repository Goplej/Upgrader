package com.upgrader.value.source;

import com.upgrader.Upgrader;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Priority-50 modifier: NBT complexity is never a value <em>source</em>, it adjusts one.
 *
 * <p>Positive contributions come from enchantments (each distinct enchantment adds scarcity),
 * stored-item content (e.g. AE2 patterns, Mekanism tablets holding energy) and structural
 * custom data. A small penalty applies to damaged tools whose durability was already priced by
 * {@link ItemPropertyValueSource} at full value.</p>
 */
public final class NBTComplexityValueSource implements ValueSource {

    public static final String NAME = "upgrader:nbt";

    /** Per-enchantment anchor; scaled further by level. */
    private static final long ENCHANT_UNIT = 12L;
    /** Value per byte of meaningful custom compound payload (capped). */
    private static final int MAX_STRUCTURED_BONUS = 64;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Confidence baseConfidence() {
        return Confidence.LOW;
    }

    @Override
    public boolean isApplicable(ItemStack stack, ValueContext context) {
        // Only meaningful once some base value exists; EMPTY stacks never reach sources anyway.
        return stack.hasTag() || hasEnchantments(stack);
    }

    @Override
    public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
        long bonus = 0L;
        StringBuilder why = new StringBuilder();

        int enchantLevels = totalEnchantmentLevels(stack);
        if (enchantLevels > 0) {
            long add = Math.min(512L, ENCHANT_UNIT * enchantLevels);
            bonus += add;
            why.append(String.format("%d enchantment level(s) (+%d)", enchantLevels, add));
        }

        CompoundTag tag = stack.getTag();
        if (tag != null) {
            int payload = structuredPayloadSize(tag, 0);
            if (payload > 8) { // ignore trivial bookkeeping keys
                long add = Math.min(MAX_STRUCTURED_BONUS, payload / 8L);
                if (add > 0) {
                    bonus += add;
                    if (why.length() > 0) {
                        why.append(", ");
                    }
                    why.append(String.format("custom data %dB (+%d)", payload, add));
                }
            }
        }

        long penalty = 0L;
        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            double damaged = (double) stack.getDamageValue() / maxDamage;
            if (damaged > 0.01) {
                // Penalty is relative: expressed against the property source's typical output.
                penalty = Math.round(Math.log1p(maxDamage) * 8.0 * damaged);
                if (why.length() > 0) {
                    why.append(", ");
                }
                why.append(String.format("damage %d/%d (-%d)", stack.getDamageValue(), maxDamage, penalty));
            }
        }

        long net = bonus - penalty;
        if (net == 0L) {
            return Optional.empty();
        }
        return Optional.of(new ValueContribution(NAME, SourceType.NBT, net, why.toString(), Confidence.LOW.weight()));
    }

    private static boolean hasEnchantments(ItemStack stack) {
        // 1.20.1 stores enchantments in plain NBT under "Enchantments".
        var tag = stack.getTag();
        return tag != null && tag.contains("Enchantments");
    }

    private static int totalEnchantmentLevels(ItemStack stack) {
        int total = 0;
        try {
            ListTag list = stack.getTag().getList("Enchantments", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Tag el = list.get(i);
                if (el instanceof CompoundTag c) {
                    total += Math.max(1, c.getInt("lvl"));
                } else {
                    total += 1;
                }
            }
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Enchantment scan failed", t);
        }
        return total;
    }

    /** Recursively counts bytes of non-cosmetic payload, depth-limited to stay cheap. */
    private static int structuredPayloadSize(CompoundTag tag, int depth) {
        if (depth > 3) {
            return 0;
        }
        int size = 0;
        for (String key : tag.getAllKeys()) {
            if ("display".equals(key) || "PublicBukkitValues".equals(key)) {
                continue;
            }
            Tag child = tag.get(key);
            if (child == null) {
                continue;
            }
            if (child instanceof CompoundTag c) {
                size += 1 + structuredPayloadSize(c, depth + 1);
            } else if (child instanceof ListTag l) {
                size += 1 + l.size();
                for (Tag el : l) {
                    if (el instanceof CompoundTag c) {
                        size += structuredPayloadSize(c, depth + 1);
                    }
                }
            } else {
                size += 4;
            }
        }
        return size;
    }
}
