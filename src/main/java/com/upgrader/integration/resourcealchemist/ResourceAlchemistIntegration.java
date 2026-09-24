package com.upgrader.integration.resourcealchemist;

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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Re:Avaritia ("resourcealchemist" / "avaritia_reborn" depending on build) — deliberately a
 * SEPARATE provider from original Avaritia: the fork renamed namespaces and rebalanced its
 * material ladder, so sharing one adapter would silently misprice items across builds.
 */
public final class ResourceAlchemistIntegration implements IntegrationProvider {

    /** Namespaces the fork is known to ship under. */
    private static final List<String> KNOWN_IDS = List.of("resourcealchemist", "avaritia_reborn");

    private volatile String boundId = KNOWN_IDS.get(0);

    @Override
    public String modId() {
        return boundId;
    }

    @Override
    public String displayName() {
        return "Re:Avaritia";
    }

    @Override
    public boolean isAvailable() {
        try {
            for (String id : KNOWN_IDS) {
                if (ModList.get().isLoaded(id)) {
                    boundId = id;
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void init() {
        Upgrader.LOGGER.info("Re:Avaritia ({}) detected - fork material anchors active", boundId);
    }

    @Override
    public Optional<ValueSource> asValueSource() {
        return Optional.of(new ForkMaterialSource());
    }

    private final class ForkMaterialSource implements ValueSource {

        @Override
        public String name() {
            return "upgrader:integration:reavaritia";
        }

        @Override
        public int priority() {
            return 6; // just behind original-Avaritia matching, ahead of recipes
        }

        @Override
        public Confidence baseConfidence() {
            return Confidence.MEDIUM;
        }

        @Override
        public boolean isApplicable(ItemStack stack, ValueContext context) {
            ResourceLocation id = ItemIdentity.of(stack);
            return id != null && boundId.equals(id.getNamespace());
        }

        @Override
        public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
            ResourceLocation id = ItemIdentity.of(stack);
            if (id == null) {
                return Optional.empty();
            }
            String path = id.getPath().toLowerCase(Locale.ROOT);
            // The fork keeps the neutronium/infinity vocabulary but compresses the ladder;
            // anchors below mirror that flatter curve rather than the original's numbers.
            long anchor;
            String label;
            if (path.contains("cosmic_neutronium") || path.contains("neutronium")) {
                anchor = 750_000L;
                label = "neutronium tier (fork)";
            } else if (path.contains("infinity")) {
                anchor = 180_000L;
                label = "infinity tier (fork)";
            } else if (path.contains("endest")) {
                anchor = 90_000L;
                label = "endest tier (fork)";
            } else if (path.contains("catalyst") || path.contains("matrix")) {
                anchor = 40_000L;
                label = "catalyst/matrix tier (fork)";
            } else {
                return Optional.empty();
            }
            return Optional.of(new ValueContribution(name(), SourceType.INTEGRATION, anchor,
                    String.format("Re:Avaritia %s (%s)", label, ResourceLocationUtil.safeId(id)),
                    Confidence.MEDIUM.weight()));
        }
    }
}
