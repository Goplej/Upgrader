package com.upgrader.value.source;

import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Priority-30: infers value from well-known Forge tag families (ingots/gems/storage blocks...).
 *
 * <p>The base unit prices below are NOT per-item values — they are the economic constants of the
 * material taxonomy itself ("an ingot-shaped thing is worth roughly one ingot"), applied to
 * whole tag classes. This is what lets an unregistered mod's {@code somemod:ruby} land near ruby
 * expectations without any hardcoded item table. Only fires while no recipe evidence exists.</p>
 */
public final class TagAndMaterialValueSource implements ValueSource {

    public static final String NAME = "upgrader:tags";

    /** One recognised material family: tag path fragment -> per-unit anchor value. */
    private record Family(String pathFragment, long unitValue) {
    }

    private static final List<Family> FAMILIES = new ArrayList<>(List.of(
            new Family("ingots", 6L),
            new Family("raw_materials", 4L),
            new Family("nuggets", 1L),
            new Family("dusts", 3L),
            new Family("gems", 24L),
            new Family("storage_blocks/raw_", 36L),
            new Family("storage_blocks", 54L),
            new Family("ores", 32L),
            new Family("plates", 8L),
            new Family("rods", 7L),
            new Family("gears", 18L),
            new Family("circuits", 64L),
            new Family("shards", 12L),
            new Family("clumps", 16L),
            new Family("crystals", 48L),
            new Family("pearls", 96L),
            new Family("enderpearls", 96L)
    ));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 30;
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
        List<TagKey<Item>> matched = new ArrayList<>();
        try {
            stack.getTags().forEach(matched::add);
        } catch (Throwable t) {
            return Optional.empty(); // registry not ready (unit tests) - simply no tag evidence
        }
        Family best = null;
        TagKey<Item> bestTag = null;
        for (TagKey<Item> tag : matched) {
            String path = tag.location().getPath().toLowerCase(java.util.Locale.ROOT);
            for (Family family : FAMILIES) {
                if (path.contains(family.pathFragment())
                        && (best == null || family.unitValue() > best.unitValue())) {
                    best = family;
                    bestTag = tag;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new ValueContribution(NAME, SourceType.TAG, best.unitValue(),
                String.format("member of %s (%s family)", bestTag.location(), best.pathFragment()),
                Confidence.MEDIUM.weight()));
    }
}
