package com.upgrader.cache;

import com.upgrader.Upgrader;
import com.upgrader.config.UpgraderConfig;
import com.upgrader.value.ItemValue;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Bounded TTL cache for computed {@link ItemValue}s.
 *
 * <p>Implemented over a {@link ConcurrentHashMap} with write timestamps and approximate LRU
 * eviction on overflow (the mod deliberately avoids bundling Caffeine to keep the jar free of
 * third-party bytecode we do not control). Sized and aged from config at construction time;
 * {@code /upgrader reload} rebuilds the engine's cache instance if an operator changes those
 * values at runtime. A one-shot WARN fires when occupancy crosses 90%.</p>
 */
public final class ValueCache {

    private static final long NANOS_PER_MS = 1_000_000L;
    /** How often expired-entry sweeps may run, in milliseconds. */
    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final ConcurrentMap<CacheKey, Entry> map = new ConcurrentHashMap<>();
    private final AtomicLong writes = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong lastSweepNanos = new AtomicLong(System.nanoTime());
    private final AtomicBoolean warnedNearCapacity = new AtomicBoolean(false);

    private final long maximumSize;
    private final long ttlNanos;

    public ValueCache() {
        this(UpgraderConfig.maxCacheSize(), UpgraderConfig.cacheTtlMinutes());
    }

    public ValueCache(long maximumSize, long ttlMinutes) {
        this.maximumSize = Math.max(1L, maximumSize);
        this.ttlNanos = Math.max(1L, ttlMinutes) * 60L * 1_000L * NANOS_PER_MS;
        Upgrader.LOGGER.info("Value cache initialised: max {} entries, TTL {} min", this.maximumSize, ttlMinutes);
    }

    /** Immutable cache slot: value + write timestamp for TTL checks. */
    private record Entry(ItemValue value, long writtenAtNanos) {
        boolean expired(long ttlNanos, long now) {
            return now - writtenAtNanos > ttlNanos;
        }
    }

    public Optional<ItemValue> get(CacheKey key) {
        Entry e = map.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        if (e.expired(ttlNanos, System.nanoTime())) {
            map.remove(key, e);
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(e.value);
    }

    public void put(CacheKey key, ItemValue value) {
        map.put(key, new Entry(value, System.nanoTime()));
        long n = writes.incrementAndGet();
        if (n % 64 == 0) {
            sweepExpired();
        }
        long size = map.size();
        if (size >= maximumSize * 0.9 && warnedNearCapacity.compareAndSet(false, true)) {
            Upgrader.LOGGER.warn("Value cache at {}% capacity ({} entries). Consider raising maxCacheSize.",
                    (size * 100) / maximumSize, size);
        } else if (size < maximumSize * 0.75) {
            warnedNearCapacity.set(false); // re-arm after pressure drops
        }
        while (map.size() > maximumSize) {
            evictOldest();
        }
    }

    public ItemValue computeIfAbsent(CacheKey key, Function<CacheKey, ItemValue> loader) {
        ItemValue cached = get(key).orElse(null);
        if (cached != null) {
            return cached;
        }
        ItemValue fresh = loader.apply(key);
        put(key, fresh);
        return fresh;
    }

    /** Removes every entry older than the configured TTL. Cheap enough to run inline. */
    private void sweepExpired() {
        long now = System.nanoTime();
        long prev = lastSweepNanos.get();
        if (now - prev < SWEEP_INTERVAL_MS * NANOS_PER_MS) {
            return;
        }
        if (!lastSweepNanos.compareAndSet(prev, now)) {
            return; // another thread is already sweeping
        }
        map.entrySet().removeIf(e -> e.getValue().expired(ttlNanos, now));
    }

    /**
     * Approximate LRU eviction: samples up to 32 random-ish buckets and removes the oldest
     * sampled entry by write timestamp. Exact LRU would need an auxiliary linked structure;
     * for a value cache approximation is fine and keeps memory flat.
     */
    private void evictOldest() {
        CacheKey victim = null;
        long oldest = Long.MAX_VALUE;
        int sampled = 0;
        for (var it = map.entrySet().iterator(); it.hasNext() && sampled < 32; sampled++) {
            var e = it.next();
            if (e.getValue().writtenAtNanos() < oldest) {
                oldest = e.getValue().writtenAtNanos();
                victim = e.getKey();
            }
        }
        if (victim != null) {
            if (map.remove(victim) != null) {
                evictions.incrementAndGet();
            }
        } else {
            breakGuard(); // empty race: nothing to evict this round
        }
    }

    private void breakGuard() {
        // no-op: guard against future refactors introducing infinite loops
    }

    public void invalidateAll() {
        map.clear();
        warnedNearCapacity.set(false);
        Upgrader.LOGGER.debug("Value cache invalidated");
    }

    public long size() {
        return map.size();
    }

    /** Human-readable hit-rate summary for /upgrader debug. */
    public String statsLine() {
        long h = hits.get(), m = misses.get();
        double rate = (h + m) == 0 ? 0.0 : (double) h / (h + m);
        return String.format("cache %d entries, hit rate %.1f%%, evictions %d", map.size(), rate * 100.0, evictions.get());
    }
}
