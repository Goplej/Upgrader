package com.upgrader.integration.projecte;

import com.upgrader.Upgrader;
import com.upgrader.integration.IntegrationProvider;
import com.upgrader.value.Confidence;
import com.upgrader.value.SourceType;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import com.upgrader.value.source.ValueSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

/**
 * Priority-0 integration: authoritative EMC values straight from ProjectE, when present.
 *
 * <p>Class-loading safety: nothing in this file references a ProjectE type. The reflective
 * {@link ProjectEMcBridge} does, and it is only touched after {@code ModList.isLoaded("projecte")}
 * returned true during {@link #init()}.</p>
 */
public final class ProjectEIntegration implements IntegrationProvider {

    public static final String MOD_ID = "projecte";

    private final ProjectEMcBridge bridge = new ProjectEMcBridge();
    private volatile boolean usable;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "ProjectE (EMC)";
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
        usable = bridge.resolve();
        if (!usable) {
            Upgrader.LOGGER.warn("ProjectE is loaded but no compatible EMC API was found; "
                    + "EMC integration disabled (engine continues without it)");
        }
    }

    @Override
    public Optional<ValueSource> asValueSource() {
        return usable ? Optional.of(new EmcValueSource()) : Optional.empty();
    }

    /** Thin source wrapper so the provider keeps its own lifecycle state out of the registry. */
    private final class EmcValueSource implements ValueSource {

        @Override
        public String name() {
            return "upgrader:integration:projecte";
        }

        @Override
        public int priority() {
            return 0; // highest precedence evidence
        }

        @Override
        public Confidence baseConfidence() {
            return Confidence.HIGH; // EMC is an explicit, curated table by the mod author
        }

        @Override
        public boolean isApplicable(ItemStack stack, ValueContext context) {
            return usable && !stack.isEmpty();
        }

        @Override
        public Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context) {
            return bridge.getEmc(stack).map(emc -> new ValueContribution(name(), SourceType.INTEGRATION,
                    emc, String.format("ProjectE EMC table value %d", emc), Confidence.HIGH.weight()));
        }
    }
}
