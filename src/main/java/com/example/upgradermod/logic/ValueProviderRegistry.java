package com.example.upgradermod.logic;

import com.example.upgradermod.logic.providers.AnalogyValueProvider;
import com.example.upgradermod.logic.providers.HeuristicValueProvider;
import com.example.upgradermod.logic.providers.OverrideValueProvider;
import com.example.upgradermod.logic.providers.ProjectEValueProvider;
import com.example.upgradermod.logic.providers.RecipeValueProvider;
import com.example.upgradermod.logic.providers.TagValueProvider;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Holds the value provider pipeline.
 *
 * <p>{@link #bootstrap()} installs the six providers of Section 1.1 of the specification. The list
 * handed out by {@link #getProviders()} is an immutable snapshot sorted by descending priority,
 * which is the execution order:</p>
 *
 * <pre>
 *   OverrideValueProvider   (1000)  config/upgradermod/overrides.json
 *   ProjectEValueProvider   ( 900)  reflection, only when ProjectE is loaded
 *   RecipeValueProvider     ( 700)  sum(ingredients) * depth multiplier
 *   TagValueProvider        ( 500)  MAX(config/upgradermod/tags.json)
 *   AnalogyValueProvider    ( 300)  modded id -&gt; vanilla id
 *   HeuristicValueProvider  ( 100)  stack size / damage / rarity rules
 * </pre>
 */
public final class ValueProviderRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mutable list, only touched inside {@code synchronized} blocks. */
    private static final List<ValueProvider> REGISTERED = new ArrayList<>();

    /** Immutable, priority sorted snapshot read by the hot path without locking. */
    private static volatile List<ValueProvider> pipeline = List.of();

    private static volatile boolean bootstrapped;

    /**
     * Registers the default provider set. Safe to call more than once; only the first call has an
     * effect.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(new OverrideValueProvider());
        register(new ProjectEValueProvider());
        register(new RecipeValueProvider());
        register(new TagValueProvider());
        register(new AnalogyValueProvider());
        register(new HeuristicValueProvider());

        LOGGER.info("Upgrader value pipeline active: {}", describe());
    }

    /**
     * Adds a provider and re-publishes the sorted pipeline.
     *
     * @param provider provider to add, {@code null} is ignored
     */
    public static synchronized void register(ValueProvider provider) {
        if (provider == null) {
            return;
        }
        REGISTERED.add(provider);

        List<ValueProvider> sorted = new ArrayList<>(REGISTERED);
        sorted.sort(Comparator.comparingInt(ValueProvider::getPriority).reversed());
        pipeline = List.copyOf(sorted);

        LOGGER.debug("Registered value provider {} with priority {}", provider.getName(), provider.getPriority());
    }

    /** Removes every provider and allows a fresh {@link #bootstrap()}. */
    public static synchronized void clear() {
        REGISTERED.clear();
        pipeline = List.of();
        bootstrapped = false;
    }

    /**
     * @return an immutable, priority sorted snapshot of the pipeline
     */
    public static List<ValueProvider> getProviders() {
        return pipeline;
    }

    /**
     * @param name provider name
     * @return the provider with that name, if registered
     */
    public static Optional<ValueProvider> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return pipeline.stream().filter(provider -> name.equals(provider.getName())).findFirst();
    }

    /** @return a comma separated {@code name(priority)} list, used for the startup log line */
    public static String describe() {
        StringBuilder builder = new StringBuilder();
        for (ValueProvider provider : pipeline) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(provider.getName()).append('(').append(provider.getPriority()).append(')');
        }
        return builder.toString();
    }

    private ValueProviderRegistry() {
        // Static access only.
    }
}
