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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;

/**
 * Verifies that abort propagation through deeply nested synchronous coordination
 * is bounded and completes within a reasonable time.
 *
 * <p>Issue 4: {@code ConcreteDescriptor.beginAbort} interrupt targeting is leaf-only;
 * deeply nested synchronous coordination may delay abort propagation. This test class
 * proves that abort propagation is bounded regardless of nesting depth because:
 *
 * <ol>
 *   <li>The interrupt targets the leaf's executing thread.</li>
 *   <li>The leaf's exception unwinds through the call stack.</li>
 *   <li>Each coordination node's {@code managedJoin} returns (either from the interrupt
 *       waking it from park, or from the future being completed by the abort cascade).</li>
 *   <li>The parent's {@code runChild} returns, allowing the next level to observe its
 *       child's terminal state.</li>
 * </ol>
 *
 * <p>The propagation time is bounded by the nesting depth and the scheduler's backoff
 * ceiling (1 second per {@code managedJoin} park). For uncooperative leaves that ignore
 * interruption, the JVM ceiling applies (leaf permit leaks).
 *
 * <p>Every test runs via {@code CompletableFuture...get(timeout)} so a regression (hang
 * from a leaked permit or stalled propagation) fails the test fast instead of hanging CI.
 */
@DisplayName("Deep nested synchronous abort propagation (Issue 4)")
class DeepNestedAbortPropagationTest {

    private static final int PARALLELISM = 4;

    private static Runner runner(final int parallelism) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism))))
                .build();
    }

    @Test
    @DisplayName("5-level nested Sequential with blocking leaf: abort propagates within timeout")
    void deeplyNestedSequentialAbortPropagatesWithinTimeout() throws Exception {
        var blockingStarted = new CountDownLatch(1);
        var blockingInterrupted = new AtomicBoolean(false);

        // Build 5 levels of nested Sequential, each containing one child.
        // The innermost child is a blocking Step.
        var innermost = Sequential.builder("level-5")
                .child(Step.of("blocking-leaf", context -> {
                    blockingStarted.countDown();
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        blockingInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                }))
                .build();

        var level4 = Sequential.builder("level-4").child(innermost).build();
        var level3 = Sequential.builder("level-3").child(level4).build();
        var level2 = Sequential.builder("level-2").child(level3).build();
        var level1 = Sequential.builder("level-1").child(level2).build();

        var timeout = Timeout.builder("outer-timeout")
                .body(level1)
                .timeout(Duration.ofMillis(500))
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

        assertThat(blockingStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaf started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(blockingInterrupted.get())
                .as("blocking leaf was interrupted")
                .isTrue();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();

        // Verify all levels are terminal
        Descriptor current = root;
        while (!current.children().isEmpty()) {
            assertThat(current.isCompleted())
                    .as("descriptor %s is completed", current.action().displayName())
                    .isTrue();
            current = current.children().get(0);
        }
        assertThat(current.isCompleted())
                .as("innermost descriptor is completed")
                .isTrue();
    }

    @Test
    @DisplayName("5-level nested Parallel with blocking leaves: abort propagates within timeout")
    void deeplyNestedParallelAbortPropagatesWithinTimeout() throws Exception {
        var leavesStarted = new CountDownLatch(PARALLELISM);
        var leavesInterrupted = new AtomicInteger(0);

        // Build 5 levels of nested Parallel.
        // The innermost Parallel has blocking Step leaves.
        var innermost = Parallel.builder("level-5").parallelism(PARALLELISM);
        for (var i = 0; i < PARALLELISM; i++) {
            final var index = i;
            innermost.child(Step.of("leaf-" + index, context -> {
                leavesStarted.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    leavesInterrupted.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var level4 = Parallel.builder("level-4")
                .parallelism(1)
                .child(innermost.build())
                .build();
        var level3 = Parallel.builder("level-3").parallelism(1).child(level4).build();
        var level2 = Parallel.builder("level-2").parallelism(1).child(level3).build();
        var level1 = Parallel.builder("level-1").parallelism(1).child(level2).build();

        var timeout = Timeout.builder("outer-timeout")
                .body(level1)
                .timeout(Duration.ofMillis(500))
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

        assertThat(leavesStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaves started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(leavesInterrupted.get())
                .as("all blocking leaves were interrupted")
                .isEqualTo(PARALLELISM);

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Mixed Sequential/Parallel nesting: abort propagates through all layers")
    void mixedNestingAbortPropagatesThroughAllLayers() throws Exception {
        var leavesStarted = new CountDownLatch(PARALLELISM);
        var leavesInterrupted = new AtomicInteger(0);

        // Build a complex nested structure:
        // Sequential -> Parallel -> Sequential -> Parallel -> Step (blocking leaves)
        var innerParallel = Parallel.builder("inner-parallel").parallelism(PARALLELISM);
        for (var i = 0; i < PARALLELISM; i++) {
            final var index = i;
            innerParallel.child(Step.of("leaf-" + index, context -> {
                leavesStarted.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    leavesInterrupted.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var innerSequential = Sequential.builder("inner-sequential")
                .child(innerParallel.build())
                .build();

        var outerParallel = Parallel.builder("outer-parallel")
                .parallelism(1)
                .child(innerSequential)
                .build();

        var outerSequential =
                Sequential.builder("outer-sequential").child(outerParallel).build();

        var timeout = Timeout.builder("outer-timeout")
                .body(outerSequential)
                .timeout(Duration.ofMillis(500))
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

        assertThat(leavesStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaves started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(leavesInterrupted.get())
                .as("all blocking leaves were interrupted")
                .isEqualTo(PARALLELISM);

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Sequential with multiple children: blocking first child interrupted, " + "remaining children aborted")
    void sequentialMultipleChildrenBlockingFirstInterruptedRemainingAborted() throws Exception {
        var blockingStarted = new CountDownLatch(1);
        var blockingInterrupted = new AtomicBoolean(false);
        var secondRan = new AtomicBoolean(false);
        var thirdRan = new AtomicBoolean(false);

        // Sequential with 3 children. First blocks, second and third should be aborted.
        var sequential = Sequential.builder("sequential").dependent();
        sequential.child(Step.of("blocking", context -> {
            blockingStarted.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                blockingInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }));
        sequential.child(Step.of("second", context -> secondRan.set(true)));
        sequential.child(Step.of("third", context -> thirdRan.set(true)));

        var timeout = Timeout.builder("outer-timeout")
                .body(sequential.build())
                .timeout(Duration.ofMillis(200))
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

        assertThat(blockingStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaf started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(blockingInterrupted.get())
                .as("blocking leaf was interrupted")
                .isTrue();
        assertThat(secondRan.get()).as("second child did not run").isFalse();
        assertThat(thirdRan.get()).as("third child did not run").isFalse();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Deeply nested Parallel with work-stealing: abort propagates within timeout")
    void deeplyNestedParallelWithWorkStealingAbortPropagates() throws Exception {
        var leavesStarted = new CountDownLatch(PARALLELISM);
        var leavesInterrupted = new AtomicInteger(0);
        var permitTesterRan = new AtomicBoolean(false);

        // Build a deeply nested Parallel structure that forces work-stealing
        // through managedJoin at multiple levels.
        var innerParallel = Parallel.builder("inner-parallel").parallelism(PARALLELISM);
        for (var i = 0; i < PARALLELISM; i++) {
            final var index = i;
            innerParallel.child(Step.of("leaf-" + index, context -> {
                leavesStarted.countDown();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    leavesInterrupted.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Wrap in multiple levels to force nested managedJoin calls
        var level4 = Parallel.builder("level-4")
                .parallelism(1)
                .child(innerParallel.build())
                .build();
        var level3 = Parallel.builder("level-3").parallelism(1).child(level4).build();
        var level2 = Parallel.builder("level-2").parallelism(1).child(level3).build();

        var timeout = Timeout.builder("outer-timeout")
                .body(level2)
                .timeout(Duration.ofMillis(500))
                .build();

        // Permit tester to verify permits are reclaimed after abort
        var permitTester = Step.of("permit-ok", context -> permitTesterRan.set(true));

        var sequence = Sequential.builder("final-sequence")
                .independent()
                .child(timeout)
                .child(permitTester)
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(sequence));

        assertThat(leavesStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaves started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(leavesInterrupted.get())
                .as("all blocking leaves were interrupted")
                .isEqualTo(PARALLELISM);
        assertThat(permitTesterRan.get())
                .as("permit-demanding step executed (permits reclaimed)")
                .isTrue();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Abort propagation time is bounded regardless of nesting depth")
    void abortPropagationTimeIsBoundedRegardlessOfNestingDepth() throws Exception {
        var maxNestingDepth = 10;
        var timeoutMs = 200;

        for (var depth = 1; depth <= maxNestingDepth; depth++) {
            var leavesStarted = new CountDownLatch(1);
            var leavesInterrupted = new AtomicBoolean(false);

            // Build N levels of nested Sequential
            var innermost = (Object) Sequential.builder("level-" + depth)
                    .child(Step.of("blocking-leaf-" + depth, context -> {
                        leavesStarted.countDown();
                        try {
                            Thread.sleep(60_000);
                        } catch (InterruptedException e) {
                            leavesInterrupted.set(true);
                            Thread.currentThread().interrupt();
                        }
                    }))
                    .build();

            for (var i = 0; i < depth - 1; i++) {
                innermost = Sequential.builder("level-" + (depth - i - 1))
                        .child((org.paramixel.api.action.Action) innermost)
                        .build();
            }

            var timeout = Timeout.builder("timeout-depth-" + depth)
                    .body((org.paramixel.api.action.Action) innermost)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();

            var startTime = System.nanoTime();
            var runnerFuture =
                    CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

            assertThat(leavesStarted.await(10, TimeUnit.SECONDS))
                    .as("depth %d: blocking leaf started", depth)
                    .isTrue();

            var result = runnerFuture.get(30, TimeUnit.SECONDS);
            var elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            assertThat(leavesInterrupted.get())
                    .as("depth %d: blocking leaf was interrupted", depth)
                    .isTrue();

            // Abort propagation should complete within a bounded time.
            // Allow generous overhead for thread scheduling and test infrastructure.
            var boundedTime = timeoutMs + 5_000; // 5 second overhead allowance
            assertThat(elapsed)
                    .as("depth %d: abort propagation time bounded", depth)
                    .isLessThan(boundedTime);

            var root = result.descriptor().orElseThrow();
            assertThat(root.isFailed()).as("depth %d: root is failed", depth).isTrue();
        }
    }

    /**
     * Documents the JVM ceiling: a leaf whose body ignores {@link Thread#interrupt()}
     * (e.g. a CPU-bound loop) cannot be forcibly cancelled by any safe JVM mechanism,
     * even with deeply nested coordination. The leaf permit leaks for the lifetime of
     * the scheduler.
     *
     * <p>Disabled by default. If enabled, it asserts the run does <em>not</em> complete
     * within a short window (demonstrating the leak), rather than hanging CI.
     */
    @Test
    @org.junit.jupiter.api.Disabled("Documents JVM ceiling: uncooperative leaf leaks permits; "
            + "cannot be forcibly cancelled even with deep nesting. "
            + "Re-enable to confirm the ceiling still holds.")
    @DisplayName("documents JVM ceiling: deeply nested uncooperative leaf leaks permits")
    void deeplyNestedUncooperativeLeafLeaksPermitsDocumentsCeiling() throws Exception {
        var secondRan = new AtomicInteger(0);

        // Build deeply nested structure with uncooperative leaf
        var innermost = Sequential.builder("level-5")
                .child(Step.of("cpu-bound-loop", context -> {
                    // Ignores interruption entirely — the JVM cannot safely stop this.
                    while (true) {
                        // busy spin
                    }
                }))
                .build();

        var level4 = Sequential.builder("level-4").child(innermost).build();
        var level3 = Sequential.builder("level-3").child(level4).build();
        var level2 = Sequential.builder("level-2").child(level3).build();
        var level1 = Sequential.builder("level-1").child(level2).build();

        var timeout = Timeout.builder("outer-timeout")
                .body(level1)
                .timeout(Duration.ofMillis(100))
                .build();

        var permitTester = Step.of("needs-permit", context -> secondRan.incrementAndGet());

        var sequence = Sequential.builder("ceiling-test")
                .independent()
                .child(timeout)
                .child(permitTester)
                .build();

        var runnerFuture = CompletableFuture.supplyAsync(() -> runner(1).run(sequence));

        // The run cannot complete: the uncooperative leaf holds the only permit forever.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runnerFuture.get(3, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(secondRan.get()).isZero();
    }
}
