package com.estebanmm13.pytra_api.metadata.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Minimal in-memory cache: bounded (least recently used entries are evicted first) with a fixed
 * time-to-live per entry. Enough to stop repeated searches/details from hammering the Steam store,
 * without pulling in a cache library for two maps.
 *
 * <p>Failures are not cached: if the loader throws, the next call tries again. Two concurrent
 * misses for the same key may both load; that's harmless here.
 */
class TtlCache<K, V> {

    private record CachedValue<V>(V value, Instant expiresAt) {
    }

    private final Duration ttl;
    private final Clock clock;
    private final Map<K, CachedValue<V>> entries;

    TtlCache(int maxSize, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CachedValue<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    V get(K key, Supplier<V> loader) {
        synchronized (entries) {
            CachedValue<V> entry = entries.get(key);
            if (entry != null && clock.instant().isBefore(entry.expiresAt())) {
                return entry.value();
            }
            entries.remove(key);
        }
        // Load outside the lock: a slow Steam call must not block hits for other keys.
        V value = loader.get();
        synchronized (entries) {
            entries.put(key, new CachedValue<>(value, clock.instant().plus(ttl)));
        }
        return value;
    }
}
