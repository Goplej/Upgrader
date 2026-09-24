package com.upgrader.value.source;

import com.upgrader.value.Confidence;
import com.upgrader.value.ValueContribution;
import com.upgrader.value.ValueContext;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One strategy in the value chain (Chain-of-Responsibility + Strategy pattern).
 *
 * <p>Implementations must be stateless and thread-safe: a single instance serves every
 * calculation. Expensive shared data (recipe indexes, tag caches) lives in the sources that own
 * it and is refreshed on reload events.</p>
 */
public interface ValueSource {

    /** Stable unique name, e.g. {@code "upgrader:recipe"}; appears verbatim in breakdowns. */
    String name();

    /** Lower priority runs first; integration sources use 0, fallbacks 60+. */
    int priority();

    /** Confidence this source claims when it produces an answer. */
    Confidence baseConfidence();

    /**
     * Cheap O(1) gate checked before {@link #evaluate}. Return false whenever the source cannot
     * possibly contribute (e.g. no recipe manager available), so evaluation stays rare and fast.
     */
    boolean isApplicable(ItemStack stack, ValueContext context);

    /** Produces this source's opinion, or empty when it has nothing to say. */
    Optional<ValueContribution> evaluate(ItemStack stack, ValueContext context);
}
