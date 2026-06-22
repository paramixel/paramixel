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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LRUCache")
class LRUCacheTest {

    @Test
    @DisplayName("LRU eviction removes oldest entry when at capacity")
    void lruEvictionOrder() {
        var cache = new LRUCache<String, String>(2, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isEqualTo("2");
        assertThat(cache.get("c")).isEqualTo("3");

        cache.close();
    }

    @Test
    @DisplayName("accessRefreshesEntry: accessing A before inserting C evicts B, not A")
    void accessRefreshesEntry() {
        var cache = new LRUCache<String, String>(2, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a");
        cache.put("c", "3");

        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isEqualTo("3");

        cache.close();
    }

    @Test
    @DisplayName("ttlEviction: expired entry is returned as null")
    void ttlEviction() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 100);

        cache.put("key", "value");
        assertThat(cache.get("key")).isEqualTo("value");

        Thread.sleep(200);

        assertThat(cache.get("key")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("remove: removed entry returns null on get")
    void remove() {
        var cache = new LRUCache<String, String>(10, 60_000);

        cache.put("key", "value");
        assertThat(cache.remove("key")).isEqualTo("value");
        assertThat(cache.get("key")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("clear: removes all entries")
    void clear() {
        var cache = new LRUCache<String, String>(10, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();

        assertThat(cache.size()).isZero();

        cache.close();
    }

    @Test
    @DisplayName("size: returns correct count")
    void size() {
        var cache = new LRUCache<String, String>(10, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        assertThat(cache.size()).isEqualTo(3);

        cache.close();
    }

    @Test
    @DisplayName("getReturnsNullForMissingKey: returns null for absent key")
    void getReturnsNullForMissingKey() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThat(cache.get("missing")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("putUpdatesExistingKey: second put overwrites first value")
    void putUpdatesExistingKey() {
        var cache = new LRUCache<String, String>(10, 60_000);

        cache.put("key", "first");
        cache.put("key", "second");

        assertThat(cache.get("key")).isEqualTo("second");
        assertThat(cache.size()).isEqualTo(1);

        cache.close();
    }

    @Test
    @DisplayName("concurrentAccess: multiple threads get and put without corruption")
    void concurrentAccess() throws InterruptedException {
        var cache = new LRUCache<Integer, Integer>(50, 60_000);
        int threadCount = 8;
        int operationsPerThread = 500;
        var latch = new CountDownLatch(threadCount);
        var errors = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = threadId * operationsPerThread + i;
                        cache.put(key, key * 10);
                        cache.get(key);
                        cache.remove(key);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors).hasValue(0);

        cache.close();
    }

    @Test
    @DisplayName("implementsAutoCloseable: LRUCache implements AutoCloseable and close shuts down reaper")
    void implementsAutoCloseable() {
        assertThat(AutoCloseable.class.isAssignableFrom(LRUCache.class)).isTrue();

        var cache = new LRUCache<String, String>(10, 60_000);
        cache.put("key", "value");

        cache.close();

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("try-with-resources: works with AutoCloseable")
    void tryWithResources() {
        var results = new ArrayList<>();

        try (var cache = new LRUCache<String, String>(10, 60_000)) {
            cache.put("a", "1");
            results.add(cache.get("a"));
        }

        assertThat(results).containsExactly("1");
    }

    @Test
    @DisplayName("accessRefreshesTtl: accessing a key before it expires refreshes its TTL")
    void accessRefreshesTtl() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 200);

        cache.put("key", "value");

        Thread.sleep(150);
        assertThat(cache.get("key")).isEqualTo("value");

        Thread.sleep(150);
        assertThat(cache.get("key")).isEqualTo("value");

        cache.close();
    }

    @Test
    @DisplayName("remove: returns null for key not in cache")
    void removeReturnsNullForMissingKey() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThat(cache.remove("missing")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("lruEvictionWithRepeatedAccess: repeated get prevents eviction")
    void lruEvictionWithRepeatedAccess() {
        var cache = new LRUCache<String, String>(3, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.get("a");
        cache.get("b");

        cache.put("d", "4");

        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).isEqualTo("2");
        assertThat(cache.get("c")).isNull();
        assertThat(cache.get("d")).isEqualTo("4");

        cache.close();
    }

    @Test
    @DisplayName("putOverwriteUpdatesLruPosition: overwriting a key moves it to most recent")
    void putOverwriteUpdatesLruPosition() {
        var cache = new LRUCache<String, String>(3, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.put("a", "updated");

        cache.put("d", "4");

        assertThat(cache.get("a")).isEqualTo("updated");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isEqualTo("3");
        assertThat(cache.get("d")).isEqualTo("4");

        cache.close();
    }

    @Test
    @DisplayName("sizeOneCache: single-entry cache evicts on each new put")
    void sizeOneCache() {
        var cache = new LRUCache<String, String>(1, 60_000);

        cache.put("a", "1");
        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.size()).isEqualTo(1);

        cache.put("b", "2");
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isEqualTo("2");
        assertThat(cache.size()).isEqualTo(1);

        cache.close();
    }

    @Test
    @DisplayName(
            "ttlMillisOfOneConstructsAndSupportsPutGet: smallest documented-valid TTL works (reaper cadence floored at 1ms)")
    void ttlMillisOfOneConstructsAndSupportsPutGet() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 1);

        cache.put("key", "value");
        assertThat(cache.get("key")).isEqualTo("value");

        // Let the eviction reaper (clamped to a 1ms cadence) fire at least once without throwing.
        Thread.sleep(20);

        // A 1ms TTL has elapsed since last access, so the entry is now expired.
        assertThat(cache.get("key")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("putNullKeyThrowsNPE: null key throws NullPointerException")
    void putNullKeyThrowsNPE() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThatThrownBy(() -> cache.put(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");

        cache.close();
    }

    @Test
    @DisplayName("putNullValueThrowsNPE: null value throws NullPointerException")
    void putNullValueThrowsNPE() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThatThrownBy(() -> cache.put("key", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value");

        cache.close();
    }

    @Test
    @DisplayName("getNullKeyThrowsNPE: null key throws NullPointerException")
    void getNullKeyThrowsNPE() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThatThrownBy(() -> cache.get(null)).isInstanceOf(NullPointerException.class);

        cache.close();
    }

    @Test
    @DisplayName("removeNullKeyThrowsNPE: null key throws NullPointerException")
    void removeNullKeyThrowsNPE() {
        var cache = new LRUCache<String, String>(10, 60_000);

        assertThatThrownBy(() -> cache.remove(null)).isInstanceOf(NullPointerException.class);

        cache.close();
    }

    @Test
    @DisplayName("evictionAtExactBoundary: cache evicts at maxSize+1")
    void evictionAtExactBoundary() {
        var cache = new LRUCache<String, String>(3, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertThat(cache.size()).isEqualTo(3);

        cache.put("d", "4");
        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("a")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("reaperEvictsExpired: expired entries removed by reaper thread")
    void reaperEvictsExpired() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 150);

        cache.put("key", "value");
        assertThat(cache.size()).isEqualTo(1);

        Thread.sleep(300);

        assertThat(cache.size()).isZero();

        cache.close();
    }

    @Test
    @DisplayName("closeClearsCache: close removes all entries and stops reaper")
    void closeClearsCache() {
        var cache = new LRUCache<String, String>(10, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");

        cache.close();

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("getExpiredReturnsNull: accessing expired entry removes it and returns null")
    void getExpiredReturnsNull() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 100);

        cache.put("key", "value");

        Thread.sleep(200);

        assertThat(cache.get("key")).isNull();
        assertThat(cache.size()).isZero();

        cache.close();
    }

    @Test
    @DisplayName("lruOrderingChain: access pattern preserves correct eviction order")
    void lruOrderingChain() {
        var cache = new LRUCache<String, String>(3, 60_000);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        cache.get("a");
        cache.get("b");

        cache.put("d", "4");
        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).isEqualTo("2");
        assertThat(cache.get("c")).isNull();
        assertThat(cache.get("d")).isEqualTo("4");

        cache.get("a");

        cache.put("e", "5");
        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("d")).isEqualTo("4");
        assertThat(cache.get("e")).isEqualTo("5");

        cache.close();
    }

    @Test
    @DisplayName("getExpiredEntryBeforeReaper: get removes expired entry before reaper runs")
    void getExpiredEntryBeforeReaper() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 50);

        cache.put("key", "value");

        Thread.sleep(100);

        assertThat(cache.get("key")).isNull();

        cache.close();
    }

    @Test
    @DisplayName("getNonExpiredEntryReturnsValue: accessing entry before expiration returns value")
    void getNonExpiredEntryReturnsValue() throws InterruptedException {
        var cache = new LRUCache<String, String>(10, 200);

        cache.put("key", "value");

        Thread.sleep(50);

        assertThat(cache.get("key")).isEqualTo("value");

        cache.close();
    }
}
