package com.upgrader.value;

import com.google.common.collect.ImmutableList;
import com.upgrader.Upgrader;
import com.upgrader.value.source.ValueSource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ordered registry of all active {@link ValueSource}s (the ValueSourceChain of the pipeline).
 *
 * <p>Third-party mods add their own sources through
 * {@link com.upgrader.api.UpgraderAPI#registerValueSource(ValueSource)}. Registration after
 * startup is allowed and takes effect immediately; the sorted view is rebuilt lazily.</p>
 */
public final class ValueSourceRegistry {

    private static final ValueSourceRegistry INSTANCE = new ValueSourceRegistry();

    public static ValueSourceRegistry get() {
        return INSTANCE;
    }

    private final CopyOnWriteArrayList<ValueSource> sources = new CopyOnWriteArrayList<>();
    private final Map<String, ValueSource> byName = new ConcurrentHashMap<>();
    private volatile List<ValueSource> sortedCache = null;

    private ValueSourceRegistry() {
    }

    /** Registers a source; duplicate names replace the previous registration with a WARN. */
    public synchronized void register(ValueSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        ValueSource previous = byName.put(source.name(), source);
        if (previous != null) {
            Upgrader.LOGGER.warn("Value source {} re-registered; replacing {}", source.name(), previous.getClass().getName());
            sources.removeIf(s -> s.name().equals(source.name()));
        }
        sources.add(source);
        sortedCache = null;
        Upgrader.LOGGER.debug("Registered value source {} (priority {}, confidence {})",
                source.name(), source.priority(), source.baseConfidence());
    }

    /** Removes a previously registered source by name; returns true when something was removed. */
    public synchronized boolean unregister(String name) {
        boolean removed = byName.remove(name) != null;
        if (removed) {
            sources.removeIf(s -> s.name().equals(name));
            sortedCache = null;
        }
        return removed;
    }

    /** All sources in ascending priority order (immutable snapshot). */
    public List<ValueSource> ordered() {
        List<ValueSource> local = sortedCache;
        if (local == null) {
            local = ImmutableList.sortedCopyOf(Comparator.comparingInt(ValueSource::priority), sources);
            sortedCache = local;
        }
        return local;
    }

    public ValueSource findByName(String name) {
        return byName.get(name);
    }

    public int size() {
        return sources.size();
    }
}
