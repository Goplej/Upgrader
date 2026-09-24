package com.upgrader.value;

/**
 * Lightweight immutable snapshot of cache counters, exposed through {@link ValueEngine}.
 *
 * @param entries live entries at snapshot time
 * @param hits    successful lookups since start
 * @param misses  failed lookups since start
 * @param evictions TTL/capacity removals since start
 */
public record ValueCacheStats(long entries, long hits, long misses, long evictions) {

    /** Hit ratio in 0..1 (0 when no traffic yet). */
    public double hitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
