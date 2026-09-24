package com.upgrader.recipe;

import com.upgrader.Upgrader;
import com.upgrader.value.ValueContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Thin façade over {@link ValueContext#visit(ResourceLocation)} that centralises cycle logging.
 *
 * <p>Kept as a separate class so the policy ("log CYCLE_DETECTED at debug, mark context, prune
 * branch") has exactly one home and can be swapped in tests.</p>
 */
public final class CycleDetector {

    private CycleDetector() {
    }

    /**
     * Attempts to enter {@code id} on the current analysis path.
     *
     * @return true when the branch may continue; false means {@code id} is already on the path
     *         (a cycle) and the caller must prune this branch — its contribution counts as zero.
     */
    public static boolean enter(ValueContext context, ResourceLocation id) {
        if (context.visit(id)) {
            return true;
        }
        Upgrader.LOGGER.debug("CYCLE_DETECTED: {} is already on the analysis path; pruning branch", id);
        return false;
    }

    /** Leaves {@code id}; must be called exactly once per successful {@link #enter}. */
    public static void leave(ValueContext context, ResourceLocation id) {
        context.unvisit(id);
    }
}
