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
import org.paramixel.api.Runner;

/**
 * Regression coverage for NC-2: {@code Timeout} over a coordination subtree must cancel the
 * subtree best-effort so that cooperative (interruptible) leaves release their leaf permits and
 * the run terminates promptly. Without cascade cancellation, leaf permits leak (held by leaves
 * that are never interrupted) and the run hangs because later work cannot acquire permits.
 *
 * <p>Every test runs via {@code CompletableFuture...get(timeout)} so a regression (hang from a
 * leaked permit) fails the test fast instead of hanging CI.
 */
@DisplayName("Timeout subtree cancellation (NC-2)")
class TimeoutSubtreeCancellationTest {

    private static final int PARALLELISM = 4;

    private static Runner runner(final int parallelism) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism))))
                .build();
    }

    @Test
    @DisplayName("Timeout over Parallel of blocking leaves releases leaf permits")
    void timeoutOverParallelOfBlockingLeavesReleasesLeafPermits() throws Exception {
        var leavesStarted = new CountDownLatch(PARALLELISM);
        var mayProceed = new CountDownLatch(1);
        var interruptedCount = new AtomicInteger(0);
        var permitTesterRan = new AtomicBoolean(false);

        // First child: a Timeout wrapping a Parallel of N blocking leaves that all acquire a
        // leaf permit and then block. This times out.
        var timedParallel = Parallel.builder("blocking-parallel").parallelism(PARALLELISM);
        for (var i = 0; i < PARALLELISM; i++) {
            final var index = i;
            timedParallel.child(Step.of("blocking-" + index, context -> {
                leavesStarted.countDown();
                try {
                    mayProceed.await();
                } catch (InterruptedException e) {
                    interruptedCount.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var timeout = Timeout.builder("timed")
                .body(timedParallel.build())
                .timeout(Duration.ofMillis(200))
                .build();

        // Second child: a single Step that needs a leaf permit. If the timed-out subtree leaked
        // its permits, this step could never be admitted. The Sequential (independent) runs both
        // children regardless of the timeout's outcome.
        var permitTester = Step.of("permit-ok", context -> permitTesterRan.set(true));

        var sequence = Sequential.builder("nc2-permit-leak")
                .independent()
                .child(timeout)
                .child(permitTester)
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(sequence));

        // Sanity: the blocking leaves did acquire permits and start.
        assertThat(leavesStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaves started")
                .isTrue();

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        // With the fix, all blocking leaves were interrupted and released their permits.
        assertThat(interruptedCount.get()).as("interrupted blocking leaves").isEqualTo(PARALLELISM);
        // The permit-demanding step ran, proving permits were reclaimed.
        assertThat(permitTesterRan.get()).as("permit-demanding step executed").isTrue();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
        var timeoutNode = root.children().get(0);
        assertThat(timeoutNode.isFailed()).isTrue();
        var parallelNode = timeoutNode.children().get(0);
        assertThat(parallelNode.isFailed()).isTrue();
        for (var leaf : parallelNode.children()) {
            assertThat(leaf.isAborted())
                    .as("leaf aborted: %s", leaf.action().displayName())
                    .isTrue();
        }
        var permitTesterNode = root.children().get(1);
        assertThat(permitTesterNode.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Timeout over nested Parallel of blocking leaves releases leaf permits")
    void timeoutOverNestedParallelReleasesLeafPermits() throws Exception {
        var interruptedCount = new AtomicInteger(0);
        var permitTesterRan = new AtomicBoolean(false);

        var inner = Parallel.builder("inner-blocking").parallelism(PARALLELISM);
        for (var i = 0; i < PARALLELISM; i++) {
            final var index = i;
            inner.child(Step.of("inner-" + index, context -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    interruptedCount.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }));
        }

        var outer = Parallel.builder("outer-blocking").parallelism(2).child(inner.build());

        var timeout = Timeout.builder("nested-timed")
                .body(outer.build())
                .timeout(Duration.ofMillis(200))
                .build();

        var permitTester = Step.of("permit-ok", context -> permitTesterRan.set(true));

        var sequence = Sequential.builder("nc2-nested")
                .independent()
                .child(timeout)
                .child(permitTester)
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(sequence));

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(interruptedCount.get()).as("interrupted inner leaves").isEqualTo(PARALLELISM);
        assertThat(permitTesterRan.get()).as("permit-demanding step executed").isTrue();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Timeout over Sequence with a blocking leaf interrupts the leaf and aborts the rest")
    void timeoutOverSequenceInterruptsLeafAndAbortsRest() throws Exception {
        var blockingStarted = new CountDownLatch(1);
        var secondRan = new AtomicBoolean(false);
        var blockingInterrupted = new AtomicBoolean(false);

        var timedSequence = Sequential.builder("timed-sequence").dependent();
        timedSequence.child(Step.of("blocking", context -> {
            blockingStarted.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                blockingInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }));
        timedSequence.child(Step.of("never-reached", context -> secondRan.set(true)));

        var timeout = Timeout.builder("seq-timed")
                .body(timedSequence.build())
                .timeout(Duration.ofMillis(200))
                .build();

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(timeout));

        assertThat(blockingStarted.await(10, TimeUnit.SECONDS))
                .as("blocking leaf started")
                .isTrue();
        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(blockingInterrupted.get()).as("blocking leaf interrupted").isTrue();
        assertThat(secondRan.get()).as("never-reached leaf did not run").isFalse();

        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
        var timedSeqNode = root.children().get(0);
        assertThat(timedSeqNode.isFailed()).isTrue();
        var blockingLeaf = timedSeqNode.children().get(0);
        assertThat(blockingLeaf.isAborted()).as("blocking leaf aborted").isTrue();
        var neverReachedLeaf = timedSeqNode.children().get(1);
        assertThat(neverReachedLeaf.isAborted())
                .as("never-reached leaf aborted")
                .isTrue();
    }

    @Test
    @DisplayName("Timeout over a single blocking leaf still interrupts (regression)")
    void timeoutOverSingleBlockingLeafStillInterrupts() throws Exception {
        var interrupted = new AtomicBoolean(false);
        var action = Timeout.builder("single-leaf")
                .body(Step.of("blocking", context -> {
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                }))
                .timeout(Duration.ofMillis(100))
                .build();

        var runnerFuture = CompletableFuture.supplyAsync(() -> runner(2).run(action));

        var result = runnerFuture.get(30, TimeUnit.SECONDS);

        assertThat(interrupted.get()).as("leaf interrupted").isTrue();
        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
        assertThat(root.children().get(0).isFailed()).isTrue();
    }

    @Test
    @DisplayName("Timeout leaf that completes within timeout — PASSED propagates (idempotency)")
    void timeoutLeafCompletingWithinTimeoutPasses() {
        var action = Timeout.builder("fast-leaf")
                .body(Step.of("quick", context -> {}))
                .timeout(Duration.ofSeconds(5))
                .build();

        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children().get(0).isPassed()).isTrue();
    }

    @Test
    @DisplayName("repeated Timeout over blocking Parallel does not exhaust permits across iterations")
    void repeatedTimeoutDoesNotExhaustPermits() throws Exception {
        var permitTestRan = new AtomicInteger(0);
        var pairCount = 4;

        // A Sequential of several (Timeout(blocking Parallel), permit-demanding Step) pairs. If
        // permits leak, an early pair starves all later permit-demanding steps and the run hangs.
        var sequence = Sequential.builder("repeated").independent();
        for (var pair = 0; pair < pairCount; pair++) {
            var blockingParallel = Parallel.builder("blocking-" + pair).parallelism(PARALLELISM);
            for (var i = 0; i < PARALLELISM; i++) {
                final var index = i;
                blockingParallel.child(Step.of("block-" + pair + "-" + index, context -> {
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            var timeout = Timeout.builder("timed-" + pair)
                    .body(blockingParallel.build())
                    .timeout(Duration.ofMillis(50))
                    .build();
            sequence.child(timeout);

            final var p = pair;
            sequence.child(Step.of("pass-" + p, context -> permitTestRan.incrementAndGet()));
        }

        var runnerFuture =
                CompletableFuture.supplyAsync(() -> runner(PARALLELISM).run(sequence.build()));

        var result = runnerFuture.get(30, TimeUnit.SECONDS);
        // If permits leaked, the permit-demanding steps could not be admitted and the run hangs.
        assertThat(permitTestRan.get())
                .as("all permit-demanding steps executed")
                .isEqualTo(pairCount);
        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
    }

    /**
     * Documents the unfixed JVM ceiling (NC-2 plan section 2): a leaf whose body ignores
     * {@link Thread#interrupt()} (e.g. a CPU-bound loop) cannot be forcibly cancelled by any safe
     * JVM mechanism, so its leaf permit leaks for the lifetime of the scheduler. Because scheduler
     * worker threads are daemon threads, the leak cannot prevent JVM shutdown.
     *
     * <p>Disabled by default. If enabled, it asserts the run does <em>not</em> complete within a
     * short window (demonstrating the leak), rather than hanging CI. Re-enable only to confirm the
     * ceiling still holds; it must be re-evaluated if a bounded-escalation mechanism is added.
     */
    @Test
    @org.junit.jupiter.api.Disabled("Documents JVM ceiling: uncooperative leaf leaks permits; "
            + "cannot be forcibly cancelled. Re-enable to confirm the ceiling still holds.")
    @DisplayName("documents JVM ceiling: uncooperative leaf leaks permits (not fixable)")
    void uncooperativeLeafLeaksPermitsDocumentsCeiling() throws Exception {
        var secondRan = new AtomicInteger(0);
        var timeout = Timeout.builder("uncooperative-timed")
                .body(Step.of("cpu-bound-loop", context -> {
                    // Ignores interruption entirely — the JVM cannot safely stop this.
                    while (true) {
                        // busy spin
                    }
                }))
                .timeout(Duration.ofMillis(100))
                .build();
        var permitTester = Step.of("needs-permit", context -> secondRan.incrementAndGet());
        var sequence = Sequential.builder("ceiling")
                .independent()
                .child(timeout)
                .child(permitTester)
                .build();

        var runnerFuture = CompletableFuture.supplyAsync(() -> runner(1).run(sequence));
        // The run cannot complete: the uncooperative leaf holds the only permit forever, so the
        // quick Parallel is never admitted. We assert the hang rather than blocking CI.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runnerFuture.get(3, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        // secondRan stays 0: the permit was never released. (Daemon thread is left running; it
        // cannot prevent JVM exit.)
        assertThat(secondRan.get()).isZero();
    }

    @Test
    @DisplayName("Timeout over Parallel aborts every leaf with ABORTED status")
    void timeoutOverParallelAbortsEveryLeaf() throws Exception {
        var action = Timeout.builder("abort-status")
                .body(Parallel.builder("p")
                        .parallelism(2)
                        .child(Step.of("a", context -> {
                            try {
                                Thread.sleep(60_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .child(Step.of("b", context -> {
                            try {
                                Thread.sleep(60_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .build())
                .timeout(Duration.ofMillis(100))
                .build();

        var runnerFuture = CompletableFuture.supplyAsync(() -> runner(2).run(action));

        var result = runnerFuture.get(30, TimeUnit.SECONDS);
        var root = result.descriptor().orElseThrow();
        assertThat(root.isFailed()).isTrue();
        var parallel = root.children().get(0);
        assertThat(parallel.isFailed()).isTrue();
        for (var leaf : parallel.children()) {
            assertThat(leaf.isAborted()).as("leaf aborted").isTrue();
        }
    }
}
