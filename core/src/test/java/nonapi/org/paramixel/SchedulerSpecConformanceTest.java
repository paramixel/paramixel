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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Conformance tests for the scheduler specification: a priority-ordered depth-first scheduler over a fixed
 * series-parallel task tree with optional per-node caps on concurrently running <em>leaf</em> tasks, an
 * optional overall cap independent of the thread pool, and no per-subtree thread budgets.
 *
 * <p>Each test names the specification clause it pins. Tests that measure concurrency either block their
 * leaves on a latch the test controls (so a positive observation is deterministic) or, for negative
 * assertions, observe a deliberately generous window.
 */
@DisplayName("Scheduler specification conformance")
class SchedulerSpecConformanceTest {

    private static final long WINDOW_MILLIS = 400;

    // ── Structure and priority ──────────────────────────────────────────

    @Test
    @Timeout(60)
    @DisplayName("a parallel node with more children than threads starts the lowest-ranked children first")
    void parallelNodeStartsLowestRankedChildrenFirst() throws Exception {
        var waves = new Waves();
        var root = Parallel.builder("root");
        for (var i = 0; i < 6; i++) {
            root.child(waves.leaf("leaf-" + i));
        }

        var future = CompletableFuture.supplyAsync(() -> run(root.build(), 2, null));

        waves.awaitStarted(2);
        assertThat(waves.startedNames())
                .as("the first wave is the two lowest-ranked leaves")
                .containsExactlyInAnyOrder("leaf-0", "leaf-1");

        waves.release(2);
        waves.awaitStarted(4);
        assertThat(waves.startedNames())
                .as("the next wave is the next two by rank")
                .containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2", "leaf-3");

        waves.release(2);
        waves.awaitStarted(6);
        assertThat(waves.startedNames())
                .containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2", "leaf-3", "leaf-4", "leaf-5");

        waves.release(16);
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
        assertThat(waves.maxActive()).as("thread count bounds concurrency").isEqualTo(2);
    }

    @Test
    @Timeout(60)
    @DisplayName("a sequential node exposes only one runnable child at a time")
    void sequentialNodeExposesOneChildAtATime() {
        var starts = new CopyOnWriteArrayList<String>();
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var root = Sequential.builder("root");
        for (var i = 0; i < 3; i++) {
            var name = "step-" + i;
            root.child(Step.of(name, context -> {
                starts.add(name);
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                sleep(20);
                active.decrementAndGet();
            }));
        }

        run(root.build(), 4, null);

        assertThat(starts).containsExactly("step-0", "step-1", "step-2");
        assertThat(maxActive.get()).as("one runnable child at a time").isEqualTo(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("a subtree with no ready leaf leaves free capacity to the next sibling")
    void subtreeWithNoReadyLeafYieldsCapacityToNextSibling() throws Exception {
        var aRunning = new CountDownLatch(1);
        var bRunning = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        var a = Sequential.builder("a")
                .child(Step.of("a0", context -> {
                    aRunning.countDown();
                    release.await();
                }))
                .child(Step.of("a1", context -> {}))
                .build();
        var b = Parallel.builder("b")
                .child(Step.of("b0", context -> {
                    bRunning.countDown();
                    release.await();
                }))
                .build();
        var root = Parallel.builder("root").child(a).child(b).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 2, null));

        assertThat(aRunning.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bRunning.await(10, TimeUnit.SECONDS))
                .as("a0 holds a thread but has no ready leaf, so the other thread must take b0")
                .isTrue();

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    // ── Per-node caps bound running leaves in the subtree ───────────────

    @Test
    @Timeout(60)
    @DisplayName("a cap of 2 with 5 ready leaves leaves the remaining threads to the next sibling")
    void capSpilloverLeavesThreadsToNextSibling() throws Exception {
        var aRunning = new CountDownLatch(2);
        var bRunning = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var aStarted = new AtomicInteger();

        var a = Parallel.builder("a").parallelism(2);
        for (var i = 0; i < 5; i++) {
            var name = "a-" + i;
            a.child(Step.of(name, context -> {
                aStarted.incrementAndGet();
                aRunning.countDown();
                release.await();
            }));
        }
        var b = Parallel.builder("b");
        for (var i = 0; i < 2; i++) {
            var name = "b-" + i;
            b.child(Step.of(name, context -> {
                bRunning.countDown();
                release.await();
            }));
        }
        var root = Parallel.builder("root").child(a.build()).child(b.build()).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 4, null));

        assertThat(aRunning.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bRunning.await(10, TimeUnit.SECONDS))
                .as("A's cap leaves two threads for B")
                .isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(aStarted.get()).as("A never exceeds its cap of 2").isEqualTo(2);

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("completing a leaf in a capped subtree starts the next-ranked leaf of that subtree")
    void capReleaseStartsNextRankedLeafInCappedSubtree() {
        var starts = new CopyOnWriteArrayList<String>();
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var a = Parallel.builder("a").parallelism(1);
        for (var i = 0; i < 3; i++) {
            var name = "a-" + i;
            a.child(Step.of(name, context -> {
                starts.add(name);
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                sleep(20);
                active.decrementAndGet();
            }));
        }

        run(a.build(), 4, null);

        assertThat(starts).containsExactly("a-0", "a-1", "a-2");
        assertThat(maxActive.get()).isEqualTo(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("a leaf is blocked when any capped ancestor is at its limit")
    void nestedCapsBlockWhenAnyAncestorIsFull() throws Exception {
        var outerRunning = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new AtomicInteger();
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var inner = Parallel.builder("inner").parallelism(3);
        for (var i = 0; i < 3; i++) {
            var name = "inner-" + i;
            inner.child(Step.of(name, context -> {
                started.incrementAndGet();
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                outerRunning.countDown();
                release.await();
                active.decrementAndGet();
            }));
        }
        var root = Parallel.builder("outer").parallelism(1).child(inner.build()).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 4, null));

        assertThat(outerRunning.await(10, TimeUnit.SECONDS)).isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(maxActive.get())
                .as("the outer cap of 1 bounds leaves anywhere in its subtree")
                .isEqualTo(1);
        assertThat(started.get()).isEqualTo(1);

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("a cap on a node under a sequential parent binds that node's subtree")
    void capOnNodeUnderSequentialParentBinds() throws Exception {
        var running = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var started = new AtomicInteger();

        var nested = Parallel.builder("nested").parallelism(2);
        for (var i = 0; i < 4; i++) {
            var name = "leaf-" + i;
            nested.child(Step.of(name, context -> {
                started.incrementAndGet();
                running.countDown();
                release.await();
            }));
        }
        var root = Sequential.builder("root").child(nested.build()).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 4, null));

        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(started.get()).isEqualTo(2);

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("a sequential node under a capped parent is still bounded by that cap")
    void sequentialUnderCappedParentBinds() throws Exception {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var started = new AtomicInteger();

        var leaves = Parallel.builder("leaves");
        for (var i = 0; i < 4; i++) {
            var name = "leaf-" + i;
            leaves.child(Step.of(name, context -> {
                started.incrementAndGet();
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                running.countDown();
                release.await();
                active.decrementAndGet();
            }));
        }
        var chain = Sequential.builder("chain").child(leaves.build()).build();
        var root = Parallel.builder("root").parallelism(1).child(chain).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 4, null));

        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(maxActive.get())
                .as("the capped parent bounds its whole subtree")
                .isEqualTo(1);
        assertThat(started.get()).isEqualTo(1);

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    // ── Overall cap is global and independent of thread count ───────────

    @Test
    @Timeout(60)
    @DisplayName("an overall cap below the thread count binds and keeps the lowest-ranked leaves")
    void overallCapBelowThreadCountBinds() throws Exception {
        var waves = new Waves();
        var root = Parallel.builder("root");
        for (var i = 0; i < 8; i++) {
            root.child(waves.leaf("leaf-" + i));
        }

        var future = CompletableFuture.supplyAsync(() -> run(root.build(), 8, 3));

        waves.awaitStarted(3);
        assertThat(waves.startedNames())
                .as("overall cap 3 binds below 8 threads, and takes the three lowest ranks")
                .containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2");

        waves.release(3);
        waves.awaitStarted(6);
        assertThat(waves.startedNames())
                .containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2", "leaf-3", "leaf-4", "leaf-5");

        waves.release(3);
        waves.awaitStarted(8);
        waves.release(16);
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
        assertThat(waves.maxActive()).as("overall cap 3 binds below 8 threads").isEqualTo(3);
    }

    @Test
    @Timeout(60)
    @DisplayName("an overall cap above the thread count leaves the thread count binding")
    void overallCapAboveThreadCountLeavesThreadsBinding() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();

        var root = Parallel.builder("root");
        for (var i = 0; i < 8; i++) {
            root.child(Step.of("leaf-" + i, context -> {
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                sleep(20);
                active.decrementAndGet();
            }));
        }

        run(root.build(), 2, 8);

        assertThat(maxActive.get()).as("threads remain the binding limit").isEqualTo(2);
    }

    @Test
    @Timeout(60)
    @DisplayName("an overall cap releases in rank order and is never exceeded")
    void overallCapReleaseStartsNextRankedLeaf() throws Exception {
        var waves = new Waves();
        var root = Parallel.builder("root");
        for (var i = 0; i < 9; i++) {
            root.child(waves.leaf("leaf-" + i));
        }

        var future = CompletableFuture.supplyAsync(() -> run(root.build(), 8, 3));

        waves.awaitStarted(3);
        assertThat(waves.startedNames()).containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2");
        waves.release(1);
        waves.awaitStarted(4);
        assertThat(waves.startedNames())
                .as("the freed slot goes to the next lowest-ranked leaf")
                .containsExactlyInAnyOrder("leaf-0", "leaf-1", "leaf-2", "leaf-3");
        waves.release(16);

        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
        assertThat(waves.maxActive()).isEqualTo(3);
    }

    @Test
    @Timeout(60)
    @DisplayName("a per-node cap and the overall cap: whichever is tighter binds")
    void perNodeCapAndOverallCapWhicheverTighterBinds() throws Exception {
        var aRunning = new CountDownLatch(2);
        var bRunning = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var aStarted = new AtomicInteger();
        var bStarted = new AtomicInteger();
        var totalStarted = new AtomicInteger();

        var a = Parallel.builder("a").parallelism(2);
        for (var i = 0; i < 5; i++) {
            a.child(Step.of("a-" + i, context -> {
                aStarted.incrementAndGet();
                totalStarted.incrementAndGet();
                aRunning.countDown();
                release.await();
            }));
        }
        var b = Parallel.builder("b");
        for (var i = 0; i < 5; i++) {
            b.child(Step.of("b-" + i, context -> {
                bStarted.incrementAndGet();
                totalStarted.incrementAndGet();
                bRunning.countDown();
                release.await();
            }));
        }
        var root = Parallel.builder("root").child(a.build()).child(b.build()).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 8, 4));

        assertThat(aRunning.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bRunning.await(10, TimeUnit.SECONDS))
                .as("A's cap-blocked leaves must not consume overall slots; B fills them")
                .isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(aStarted.get()).isEqualTo(2);
        assertThat(totalStarted.get())
                .as("overall cap 4 binds: 2 from A plus 2 from B")
                .isEqualTo(4);

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("a subtree and its descendants get capacity before later siblings")
    void earlierSubtreeGetsCapacityBeforeLaterSibling() throws Exception {
        var running = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var aStarted = new AtomicInteger();
        var bStarted = new AtomicInteger();

        var a = Parallel.builder("a");
        for (var i = 0; i < 3; i++) {
            a.child(Step.of("a-" + i, context -> {
                aStarted.incrementAndGet();
                running.countDown();
                release.await();
            }));
        }
        var b = Parallel.builder("b");
        for (var i = 0; i < 3; i++) {
            b.child(Step.of("b-" + i, context -> {
                bStarted.incrementAndGet();
                running.countDown();
                release.await();
            }));
        }
        var root = Parallel.builder("root").child(a.build()).child(b.build()).build();

        var future = CompletableFuture.supplyAsync(() -> run(root, 2, null));

        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        sleep(WINDOW_MILLIS);
        assertThat(aStarted.get()).as("A's eligible leaves take both threads").isEqualTo(2);
        assertThat(bStarted.get())
                .as("B gets nothing while A has eligible work")
                .isZero();

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isPassed()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("no thread is idle while an eligible ready leaf exists")
    void neverIdlesWhileEligibleWorkExists() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var executions = new AtomicInteger();

        var root = Parallel.builder("root");
        for (var i = 0; i < 12; i++) {
            root.child(Step.of("leaf-" + i, context -> {
                executions.incrementAndGet();
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                sleep(10);
                active.decrementAndGet();
            }));
        }

        var result = run(root.build(), 3, null);

        assertThat(result.isPassed()).isTrue();
        assertThat(executions.get()).isEqualTo(12);
        assertThat(maxActive.get()).as("every thread stays busy").isEqualTo(3);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Result run(final Action action, final int parallelism, final Integer concurrencyCap) {
        var properties = new HashMap<String, String>();
        properties.put(Configuration.RUNNER_PARALLELISM, Integer.toString(parallelism));
        properties.put(Configuration.SCHEDULER_QUEUE_CAPACITY, "256");
        properties.put(Configuration.ANSI, "false");
        if (concurrencyCap != null) {
            properties.put(Configuration.SCHEDULER_CONCURRENCY_CAP, Integer.toString(concurrencyCap));
        }
        return Runner.builder()
                .configuration(Configuration.of(Map.copyOf(properties)))
                .listener(new Listener() {})
                .build()
                .run(action);
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    private static void awaitCondition(final java.util.function.BooleanSupplier condition, final String what) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(2);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    /**
     * Leaves that block until the test releases them, so the wave structure is deterministic: a leaf that has
     * started stays started until the test frees its slot.
     */
    private static final class Waves {

        private final CopyOnWriteArrayList<String> started = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.Semaphore release = new java.util.concurrent.Semaphore(0);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();

        Step leaf(final String name) {
            return Step.of(name, context -> {
                started.add(name);
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                release.acquire();
                active.decrementAndGet();
            });
        }

        java.util.List<String> startedNames() {
            return java.util.List.copyOf(started);
        }

        void awaitStarted(final int count) {
            awaitCondition(() -> started.size() >= count, count + " leaves to start");
        }

        void release(final int permits) {
            release.release(permits);
        }

        int maxActive() {
            return maxActive.get();
        }
    }
}
