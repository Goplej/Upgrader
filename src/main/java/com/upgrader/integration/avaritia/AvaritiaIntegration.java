package com.upgrader.integration.avaritia;

import com.upgrader.Upgrader;
import com.upgrader.integration.IntegrationProvider;
import com.upgrader.recipe.ItemIdentity;
import com.upgrader.util.ResourceLocationUtil;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Locale;
import java.util.Optional;

/**
 * Avaritia (original, mods.avaritia / "avaritia") integration — kept strictly separate from the
 * Re:Avaritia fork because the two expose different recipe APIs and item sets.
 *
 * <p>Avaritia's infinity-tier recipes are enormous (9× cosmic components each), which is exactly
 * where the generic recipe tree truncates. This source recognises the well-known material
 * taxonomy by <em>id pattern</em> (neutronium / infinity / matrix categories) and supplies a
 * structural anchor so those items never collapse to UNKNOWN. The anchors encode relative
 * scarcity of the material families, not per-item values, and every use appears in the
 * breakdown with an explanation.</p>
 */
public final class AvaritiaIntegration implements IntegrationProvider {

    public static final String MOD_ID = "avaritia";

    /** Ordered family table: first match wins (most specific paths first). */
    private record Family(String pathContains, long anchor, String label) {
    }

    private static final Family[] FAMILIES = {
            new Family("cosmic_neutronium", 1_000_000L, "cosmic neutronium tier"),
            new Family("neutronium", 500_000L, "neutronium tier"),
            new Family("infinity", 250_000L, "infinity tier"),
            new Family("catalyst", 60_000L, "catalyst tier"),
            new Family("matrix", 30_000L, "matrix tier"),
            new Family("compressed", 4_096L, "compressed storage tier"),
            new Family("fabricator", 8_192L, "fabricator device tier"),
    };

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Avaritia";
    }

    @Override
    public boolean isAvailable() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void init() {
        Upgrader.LOGGER.info("Avaritia detected - structural material anchors active");
    }

    @Override
    public Optional<ValueSource> asValueSource() {
        return Optional.of(new AvaritiaMaterialSource());
    }

    private static final class AvaritiaMaterialSource implements ValueSource {

        @Override
        public String name() {
            return "upgrader:integration:avaritia";
        }

        @Override
        public int priority() {
            return 5; // after EMC, before generic recipes
        }

        @Override
        public Confidence baseConfidence() {
            return Confidence.MEDIUM; // id-pattern inference, not an authoritative table
        }

        @Override
        public boolean isApplicable(ItemStack stack, ValueContext context) {
            ResourceLocation id = ItemIdentity.of(stack);
            return id != null && MOD_ID.equals(id.getNamespace());
        }

        @Override
        public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
            ResourceLocation id = ItemIdentity.of(stack);
            if (id == null) {
                return Optional.empty();
            }
            String path = id.getPath().toLowerCase(Locale.ROOT);
            for (Family family : FAMILIES) {
                if (path.contains(family.pathContains())) {
                    return Optional.of(new ValueContribution(name(), SourceType.INTEGRATION,
                            family.anchor(),
                            String.format("Avaritia %s (%s)", family.label(), ResourceLocationUtil.safeId(id)),
                            Confidence.MEDIUM.weight()));
                }
            }
            return Optional.empty(); // plain avaritia item: let the recipe tree price it
        }
    }
}
