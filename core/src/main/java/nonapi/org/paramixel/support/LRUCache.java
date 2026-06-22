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
 * when an entry has not been accessed within {@link #ttlMilliseconds}. A daemon thread runs periodically
 * to remove expired entries.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public final class LRUCache<K, V> implements AutoCloseable {

    private final int maxSize;
    private final long ttlMilliseconds;
    private final LinkedHashMap<K, CacheEntry<V>> backingMap;
    private final ScheduledExecutorService reaper;
    private final ScheduledFuture<?> reaperTask;

    /**
     * Best-effort upper bound for waiting on the reaper thread to terminate during {@link #close()}.
     */
    private static final long REAPER_TERMINATION_TIMEOUT_MILLIS = 1_000L;

    /**
     * Creates a new LRU cache with the specified max size and TTL.
     *
     * @param maxSize the maximum number of entries before LRU eviction; must be positive
     * @param ttlMilliseconds time-to-live in milliseconds since last access; must be positive. The
     *     value {@code 1} is supported (the eviction reaper cadence is floored at {@code 1} ms).
     * @throws IllegalArgumentException if {@code maxSize} or {@code ttlMillis} is not positive
     */
    public LRUCache(final int maxSize, final long ttlMilliseconds) {
        this.maxSize = Arguments.requirePositive(maxSize, "maxSize must be positive, was: " + maxSize);
        this.ttlMilliseconds =
                Arguments.requirePositive(ttlMilliseconds, "ttlMillis must be positive, was: " + ttlMilliseconds);
        this.backingMap = new LinkedHashMap<>(maxSize, 0.75F, true) {
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
        // Floor the cadence at 1 ms so the documented-valid value ttlMillis == 1 does not produce
        // an invalid period of 0 (1 / 2 == 0 via integer division), which
        // ScheduledThreadPoolExecutor.scheduleAtFixedRate rejects.
        var reaperPeriodMillis = Math.max(1L, ttlMilliseconds / 2);
        this.reaperTask = reaper.scheduleAtFixedRate(
                this::evictExpired, reaperPeriodMillis, reaperPeriodMillis, TimeUnit.MILLISECONDS);
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
        // Serialize the structural clear with any in-flight evictExpired() iteration, which holds
        // the same monitor. Without this guard, a concurrent reaper iteration could observe a
        // ConcurrentModificationException / map corruption. cancel(false) + shutdown() do not
        // interrupt or wait on a currently-running evictExpired(), so the monitor is required.
        synchronized (this) {
            backingMap.clear();
        }
        // Best-effort: ensure the reaper thread has terminated so it cannot touch the map after
        // close() returns. The reaper task is trivial, so the bounded timeout is never hit in
        // practice; fall back to shutdownNow() (interrupt) if it is.
        try {
            if (!reaper.awaitTermination(REAPER_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                reaper.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reaper.shutdownNow();
        }
    }

    private boolean isExpired(final CacheEntry<V> entry) {
        return System.currentTimeMillis() - entry.lastAccessTime > ttlMilliseconds;
    }

    private synchronized void evictExpired() {
        long now = System.currentTimeMillis();
        backingMap.entrySet().removeIf(entry -> now - entry.getValue().lastAccessTime > ttlMilliseconds);
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
