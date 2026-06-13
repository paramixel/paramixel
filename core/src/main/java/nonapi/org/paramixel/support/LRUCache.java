/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nonapi.org.paramixel.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A bounded LRU cache with TTL eviction.
 *
 * <p>Entries are evicted when either the cache size exceeds {@link #maxSize} (LRU ordering) or
 * when an entry has not been accessed within {@link #ttlMillis}. A daemon thread runs periodically
 * to remove expired entries.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public final class LRUCache<K, V> implements AutoCloseable {

    private final int maxSize;
    private final long ttlMillis;
    private final LinkedHashMap<K, CacheEntry<V>> backingMap;
    private final ScheduledExecutorService reaper;
    private final ScheduledFuture<?> reaperTask;

    /**
     * Creates a new LRU cache with the specified max size and TTL.
     *
     * @param maxSize the maximum number of entries before LRU eviction; must be positive
     * @param ttlMillis time-to-live in milliseconds since last access; must be positive
     * @throws IllegalArgumentException if {@code maxSize} or {@code ttlMillis} is not positive
     */
    public LRUCache(final int maxSize, final long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.backingMap = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > LRUCache.this.maxSize;
            }
        };
        this.reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "paramixel-lrucache");
            t.setDaemon(true);
            return t;
        });
        this.reaperTask =
                reaper.scheduleAtFixedRate(this::evictExpired, ttlMillis / 2, ttlMillis / 2, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the value associated with the given key, or {@code null} if not found or expired.
     * Access time is updated on successful lookup.
     *
     * @param key the key; must not be {@code null}
     * @return the value, or {@code null} if not found or expired
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public synchronized V get(final K key) {
        Objects.requireNonNull(key, "key cannot be null");
        var entry = backingMap.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            backingMap.remove(key);
            return null;
        }
        entry.lastAccessTime = System.currentTimeMillis();
        return entry.value;
    }

    /**
     * Associates the given value with the key. If the cache exceeds max size, the least recently
     * used entry is evicted.
     *
     * @param key the key; must not be {@code null}
     * @param value the value; must not be {@code null}
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    @SuppressWarnings("AvoidThrowingNullPointerException")
    public synchronized void put(final K key, final V value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        backingMap.put(key, new CacheEntry<>(value));
    }

    /**
     * Removes the entry for the given key.
     *
     * @param key the key; must not be {@code null}
     * @return the removed value, or {@code null} if not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public synchronized V remove(final K key) {
        Objects.requireNonNull(key, "key cannot be null");
        var entry = backingMap.remove(key);
        return entry != null ? entry.value : null;
    }

    /**
     * Removes all entries from the cache.
     */
    public synchronized void clear() {
        backingMap.clear();
    }

    /**
     * Returns the number of entries in the cache.
     *
     * @return the size
     */
    public synchronized int size() {
        return backingMap.size();
    }

    @Override
    public void close() {
        reaperTask.cancel(false);
        reaper.shutdown();
        backingMap.clear();
    }

    private boolean isExpired(final CacheEntry<V> entry) {
        return System.currentTimeMillis() - entry.lastAccessTime > ttlMillis;
    }

    private synchronized void evictExpired() {
        long now = System.currentTimeMillis();
        backingMap.entrySet().removeIf(entry -> now - entry.getValue().lastAccessTime > ttlMillis);
    }

    private static final class CacheEntry<V> {
        final V value;
        long lastAccessTime;

        CacheEntry(final V value) {
            this.value = value;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
