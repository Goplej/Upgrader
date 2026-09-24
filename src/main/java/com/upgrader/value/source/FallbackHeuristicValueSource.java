package com.upgrader.value.source;

import com.upgrader.mod.ModOriginDetector;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Optional;

/**
 * Priority-60 last resort: structural heuristics that need neither recipes nor tags.
 *
 * <p>Rarity, stack-size behaviour and fire resistance are weak but honest signals — a
 * {@code RARE} item that stacks to 1 and survives lava is probably worth more than dirt. The
 * contribution is capped low and always LOW confidence so it can never masquerade as real
 * pricing evidence. When even these signals say nothing, the engine reports UNKNOWN rather than
 * inventing a number.</p>
 */
public final class FallbackHeuristicValueSource implements ValueSource {

    public static final String NAME = "upgrader:fallback";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public Confidence baseConfidence() {
        return Confidence.LOW;
    }

    @Override
    public boolean isApplicable(ItemStack stack, ValueContext context) {
        // Runs for every item; anti-double-counting happens via the type switch in ValueCalculator
        // only for RECIPE/TAG, and HEURISTIC rows add at most a small capped anchor.
        return true;
    }

    @Override
    public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
        long value = 0L;
        StringBuilder why = new StringBuilder();
        ResourceLocation id = com.upgrader.recipe.ItemIdentity.of(stack);

        Rarity rarity = stack.getRarity();
        if (rarity != null && rarity != Rarity.COMMON) {
            long add = switch (rarity) {
                case UNCOMMON -> 8L;
                case RARE -> 32L;
                case EPIC -> 128L;
                default -> 0L;
            };
            value += add;
            why.append(String.format("rarity %s (+%d)", rarity.name().toLowerCase(java.util.Locale.ROOT), add));
        }

        if (stack.getMaxStackSize() == 1 && !stack.isDamaged()) {
            value += 4L;
            if (why.length() > 0) {
                why.append(", ");
            }
            why.append("unstackable (+4)");
        }

        try {
            if (stack.getItem().isFireResistant()) {
                value += 16L;
                if (why.length() > 0) {
                    why.append(", ");
                }
                why.append("fire resistant (+16)");
            }
        } catch (Throwable ignored) {
            // very old/odd item implementations - skip this signal
        }

        if (value == 0L) {
            // Absolutely no signal: contribute nothing so the engine can honestly report UNKNOWN.
            return Optional.empty();
        }

        String origin = ModOriginDetector.isVanilla(id) ? "vanilla" : ModOriginDetector.displayNameOf(id);
        if (why.length() > 0) {
            why.append("; ");
        }
        why.append("origin ").append(origin);
        return Optional.of(new ValueContribution(NAME, SourceType.HEURISTIC, Math.min(value, 256L),
                why.toString(), Confidence.LOW.weight()));
    }
}
