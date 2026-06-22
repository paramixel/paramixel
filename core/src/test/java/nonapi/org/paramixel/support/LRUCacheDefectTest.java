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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Regression tests for the {@link LRUCache} defects recorded in ISSUES.md (L1 and L2).
 *
 * <p>L1 and L2 are now fixed; these tests lock in the corrected behavior. The L2 test is
 * deterministic in both directions: it fails on the unfixed code (where {@code close()} mutates
 * the backing map without the monitor) and passes on the fixed code (where {@code close()}
 * acquires the monitor and serializes with an in-flight reaper-style iteration).
 */
@DisplayName("LRUCache defect regression (ISSUES.md L1, L2)")
class LRUCacheDefectTest {

    // ----- L1: constructor precondition validation / reaper period == 0 -----

    @Test
    @DisplayName("L1: ttlMillis=1 is a documented-valid positive value and must not crash the constructor")
    void ttlMillisOneIsDocumentedValidAndMustNotThrow() {
        // The class Javadoc states ttlMillis "must be positive"; 1 is positive and therefore legal.
        // Bug: the constructor passes ttlMillis / 2 (== 0 via integer division) as the reaper period
        // to ScheduledThreadPoolExecutor.scheduleAtFixedRate, which rejects period <= 0.
        assertThatCode(() -> {
                    var cache = new LRUCache<String, String>(10, 1);
                    cache.close();
                })
                .as("new LRUCache(10, 1) must construct since ttlMillis=1 is a documented-valid positive value")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("L1: maxSize=0 violates the documented \"must be positive\" precondition and must be rejected")
    void maxSizeZeroMustBeRejectedPerDocumentedPrecondition() {
        // The constructor Javadoc states maxSize "must be positive" and documents
        // @throws IllegalArgumentException if maxSize or ttlMillis is not positive.
        // Bug: maxSize == 0 is not validated; it constructs silently and evicts on every put.
        assertThatThrownBy(() -> {
                    var cache = new LRUCache<String, String>(0, 1_000);
                    cache.close();
                })
                .as("new LRUCache(0, 1000) must reject maxSize=0 per the documented \"must be positive\" precondition")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- L2: close() must synchronize the backing-map clear against an in-flight reaper -----

    @Test
    @Timeout(10)
    @DisplayName("L2: close() acquires the monitor and serializes with an in-flight reaper iteration")
    void closeAcquiresMonitorAndSerializesWithConcurrentIteration() throws Exception {
        var cache = new LRUCache<String, String>(1_000, 60_000);
        for (int i = 0; i < 500; i++) {
            cache.put("k" + i, "v" + i);
        }
        // TTL is 60s, so the real reaper never fires during this sub-second test; the racer below
        // is the only thing that holds the cache monitor, deterministically reproducing a reaper
        // that is mid-eviction when close() runs.

        var backingMapField = LRUCache.class.getDeclaredField("backingMap");
        backingMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> backingMap = (Map<String, ?>) backingMapField.get(cache);

        var monitorHeld = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        var fullyIterated = new AtomicBoolean(false);
        var racerError = new AtomicReference<Throwable>();

        // Simulate a reaper holding the cache monitor mid-eviction, with a live iterator open.
        var racer = new Thread(() -> {
            synchronized (cache) {
                var it = backingMap.entrySet().iterator();
                monitorHeld.countDown();
                try {
                    releaseMonitor.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    racerError.set(e);
                    return;
                }
                // Finish the iteration while still holding the monitor. On the fixed code,
                // close()'s clear() is blocked behind this monitor, so it cannot corrupt this
                // iterator; on the unfixed code, clear() ignores the monitor and the live iterator
                // observes the structural change (ConcurrentModificationException).
                try {
                    while (it.hasNext()) {
                        it.next();
                    }
                    fullyIterated.set(true);
                } catch (Throwable t) {
                    racerError.set(t);
                }
            }
        });
        racer.setDaemon(true);
        racer.start();
        assertThat(monitorHeld.await(5, TimeUnit.SECONDS))
                .as("racer should acquire the cache monitor and a live iterator")
                .isTrue();

        var closeDone = new CountDownLatch(1);
        var closeError = new AtomicReference<Throwable>();
        var closer = new Thread(() -> {
            try {
                cache.close();
                closeDone.countDown();
            } catch (Throwable t) {
                closeError.set(t);
            }
        });
        closer.setDaemon(true);
        closer.start();

        // On the fixed code, close() runs cancel()+shutdown() then blocks on the cache monitor
        // held by the racer, so it must NOT complete here. On the unfixed code, close() ignores the
        // monitor and completes (including the unsynchronized clear) almost immediately, which this
        // assertion catches. Give the closer a moment to reach the synchronized block.
        Thread.sleep(150);
        assertThat(closeDone.getCount())
                .as("close() must block on the cache monitor while a reaper-style iteration holds it (ISSUES.md L2)")
                .isNotZero();

        // Release the monitor: the racer finishes its iteration cleanly (clear() is serialized
        // behind it), then close() acquires the monitor and clears.
        releaseMonitor.countDown();
        racer.join(5_000);
        assertThat(racer.isAlive()).as("racer thread should have terminated").isFalse();
        assertThat(racerError.get())
                .as("an in-flight backing-map iteration must not be corrupted by close() (ISSUES.md L2)")
                .isNull();
        assertThat(fullyIterated.get())
                .as("the in-flight iterator should complete normally once close() respects the monitor")
                .isTrue();

        assertThat(closeDone.await(2, TimeUnit.SECONDS))
                .as("close() should complete once the monitor is released")
                .isTrue();
        assertThat(closeError.get()).isNull();
    }
}
