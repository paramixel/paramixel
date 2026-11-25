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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Cancellation, shutdown, and high-contention interleavings.
 */
class CancellationStressTest {

    @Test
    void nonQuiescentRetryStillReportsTheActiveAttemptAndItsChild() throws Exception {
        final var calls = new AtomicInteger();
        final var secondAttemptRunning = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var definition = Sequential.builder("retried")
                .retry(RetryPolicy.attempts(2))
                .step("work", () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("first attempt failed");
                    }
                    secondAttemptRunning.countDown();
                    for (; ; ) {
                        try {
                            release.await();
                            return;
                        } catch (InterruptedException ignored) {
                            // Keep this attempt active until the test releases it.
                        }
                    }
                })
                .build();

        try (var runner = runner(1, 50)) {
            final var run = new FutureTask<>(() -> runner.run(definition));
            final var thread = new Thread(run);
            thread.setDaemon(true);
            thread.start();
            try {
                assertThat(secondAttemptRunning.await(5, TimeUnit.SECONDS)).isTrue();
                runner.close();
                final var result = run.get(5, TimeUnit.SECONDS);
                final var attempts = result.root().orElseThrow().children();

                assertThat(result.isQuiescent()).isFalse();
                assertThat(result.attemptCount()).isEqualTo(2);
                assertThat(attempts)
                        .extracting(attempt -> attempt.status().kind())
                        .containsExactly(Status.Kind.FAILED, Status.Kind.CANCELED);
                assertThat(attempts.get(1).completedAt()).isEmpty();
                assertThat(attempts.get(1).children()).singleElement().satisfies(child -> {
                    assertThat(child.name()).isEqualTo("work");
                    assertThat(child.status().kind()).isEqualTo(Status.Kind.CANCELED);
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cancellationDuringRepeatPredicateMarksTheCurrentAttemptCanceled() throws Exception {
        final var checking = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var definition = Sequential.builder("root")
                .repeat(
                        "repeated",
                        repeat -> repeat.maxIterations(2)
                                .until((context, status) -> {
                                    checking.countDown();
                                    try {
                                        release.await();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return false;
                                })
                                .step("work", () -> {}))
                .build();

        try (var runner = runner(1, 5_000)) {
            final var run = new FutureTask<>(() -> runner.run(definition));
            final var thread = new Thread(run);
            thread.setDaemon(true);
            thread.start();
            try {
                assertThat(checking.await(5, TimeUnit.SECONDS)).isTrue();
                runner.close();
                final var result = run.get(10, TimeUnit.SECONDS);
                final var repeated = result.root().orElseThrow().children().get(0);

                assertThat(result.isQuiescent()).isTrue();
                assertThat(repeated.status().kind()).isEqualTo(Status.Kind.CANCELED);
                assertThat(repeated.reason()).hasValue("runner closed");
                assertThat(repeated.children()).singleElement().satisfies(attempt -> {
                    assertThat(attempt.status().kind()).isEqualTo(Status.Kind.CANCELED);
                    assertThat(attempt.reason()).hasValue("runner closed");
                    assertThat(attempt.children())
                            .singleElement()
                            .satisfies(
                                    child -> assertThat(child.status().kind()).isEqualTo(Status.Kind.PASSED));
                });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cancellationInterruptingBeforeCancelsChildrenAndStillRunsAfter() throws Exception {
        final var starting = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var childCalls = new AtomicInteger();
        final var afterCalls = new AtomicInteger();
        final var definition = Sequential.builder("root")
                .before(() -> {
                    starting.countDown();
                    release.await();
                })
                .step("child", childCalls::incrementAndGet)
                .after(afterCalls::incrementAndGet)
                .build();

        try (var runner = runner(1, 5_000)) {
            final var run = new FutureTask<>(() -> runner.run(definition));
            final var thread = new Thread(run);
            thread.setDaemon(true);
            thread.start();
            try {
                assertThat(starting.await(5, TimeUnit.SECONDS)).isTrue();
                runner.close();
                final var result = run.get(10, TimeUnit.SECONDS);

                assertThat(result.isQuiescent()).isTrue();
                assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
                assertThat(afterCalls).hasValue(1);
                assertThat(childCalls).hasValue(0);
                assertThat(result.root().orElseThrow().children())
                        .singleElement()
                        .satisfies(child -> {
                            assertThat(child.status().kind()).isEqualTo(Status.Kind.CANCELED);
                            assertThat(child.reason()).hasValue("runner closed");
                        });
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void cleanupFailuresRetainTheirSeverityAfterCancellation() throws Exception {
        final var bodyStarted = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var afterFailure = new IllegalStateException("after failed");
        final var definition = Sequential.builder("root")
                .step("work", () -> {
                    bodyStarted.countDown();
                    release.await();
                })
                .after(() -> {
                    throw afterFailure;
                })
                .build();

        try (var runner = runner(1, 5_000)) {
            final var run = new FutureTask<>(() -> runner.run(definition));
            final var thread = new Thread(run);
            thread.setDaemon(true);
            thread.start();
            try {
                assertThat(bodyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                runner.close();
                final var result = run.get(10, TimeUnit.SECONDS);
                final var root = result.root().orElseThrow();

                assertThat(result.isQuiescent()).isTrue();
                assertThat(root.children()).singleElement().satisfies(child -> {
                    assertThat(child.bodyStatus())
                            .hasValueSatisfying(
                                    status -> assertThat(status.isCanceled()).isTrue());
                });
                assertThat(root.phases())
                        .filteredOn(phase -> phase.phase() == TaskPhase.AFTER)
                        .extracting(phase -> phase.status().kind())
                        .containsExactly(Status.Kind.FAILED);
                assertThat(root.failures())
                        .extracting(failure -> failure.throwable().orElseThrow())
                        .containsExactly(afterFailure);
                assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
            } finally {
                release.countDown();
            }
        }
    }

    private static Runner runner(final int workers, final long graceMillis) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        Configuration.WORKERS, Integer.toString(workers),
                        Configuration.SHUTDOWN_GRACE_MILLIS, Long.toString(graceMillis))))
                .build();
    }

    @Test
    @DisplayName("SCHED-16: closing the runner cancels parallel work and yields a canceled result")
    void shutdownCancelsRun() throws InterruptedException {
        // In-flight steps block until released instead of sleeping for a fixed duration: a step admitted while the
        // cancellation interrupt sweep is in flight must still finish well inside the shutdown grace, so the result
        // is quiescent regardless of host core count or load.
        final var started = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final Task definition = Parallel.builder("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 4).boxed().toList(),
                        index -> Sequential.builder("child-" + index).step("wait", () -> {
                            started.countDown();
                            release.await(60, TimeUnit.SECONDS);
                        }))
                .build();
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "5000")))
                .pool(Pool.named("p"), 2)
                .build();
        final List<Result> results = new ArrayList<>();
        final var thread = new Thread(() -> results.add(runner.run(definition)));
        thread.start();

        assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
        runner.close();
        release.countDown();
        thread.join(10_000L);
        runner.close();

        assertThat(thread.isAlive()).isFalse();
        assertThat(results).hasSize(1);
        final var result = results.get(0);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.canceledCount()).isPositive();
    }

    @Test
    @DisplayName("repeated cancellation requests stay idempotent")
    void repeatedCloseIsIdempotent() {
        final var started = new CountDownLatch(1);
        final Task definition = Sequential.builder("root")
                .step("wait", () -> {
                    started.countDown();
                    Thread.sleep(5000L);
                })
                .build();
        final var runner = runner(1, 5000);
        final List<Result> results = new ArrayList<>();
        final var thread = new Thread(() -> results.add(runner.run(definition)));
        thread.start();
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (var i = 0; i < 5; i++) {
                runner.close();
            }
            thread.join(10_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status().kind()).isEqualTo(Status.Kind.CANCELED);
    }

    @Test
    @DisplayName("SCHED-14: high-contention interleavings never double release or lose children")
    void highContentionInterleavings() {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final Task definition = Parallel.builder("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 200).boxed().toList(),
                        index -> Sequential.builder("child-" + index)
                                .independent()
                                .step("one", () -> {
                                    final var current = active.incrementAndGet();
                                    peak.accumulateAndGet(current, Math::max);
                                    try {
                                        Thread.yield();
                                    } finally {
                                        active.decrementAndGet();
                                    }
                                })
                                .step("two", () -> {}))
                .build();
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.WORKERS, "4")))
                .pool(Pool.named("p"), 3)
                .build();

        for (var iteration = 0; iteration < 10; iteration++) {
            final var result = runner.run(definition);
            assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
            assertThat(result.occurrenceCount()).isEqualTo(601);
            assertThat(peak.get()).isBetween(1, 3);
            assertThat(active.get()).isZero();
            peak.set(0);
        }
    }

    @Test
    @DisplayName("retry and cancellation interacting repeatedly stays consistent")
    void retryAndCancellation() {
        final AtomicInteger attempts = new AtomicInteger();
        final Task definition = Sequential.builder("root")
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.attempts(50))
                        .step("probe", () -> {
                            attempts.incrementAndGet();
                            Thread.sleep(5L);
                            throw new IllegalStateException("always fails");
                        }))
                .build();
        final var runner = runner(2, 2000);
        final List<Result> results = new ArrayList<>();
        final var thread = new Thread(() -> results.add(runner.run(definition)));
        thread.start();
        try {
            Thread.sleep(50L);
            runner.close();
            thread.join(10_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(results).hasSize(1);
        final var result = results.get(0);
        assertThat(result.status().kind()).isIn(Status.Kind.CANCELED, Status.Kind.FAILED);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("closing the runner while a parallel subtree is idle still finalizes every started fixture")
    void closeWhileSubtreeIdleFinalizesStartedFixtures() throws InterruptedException {
        final var afterCalls = new AtomicInteger();
        final var released = new AtomicInteger();
        final var firstAttemptsFailed = new CountDownLatch(2);
        final Task definition = Parallel.builder("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 2).boxed().toList(),
                        index -> Sequential.builder("fixture-" + index)
                                .after(Sequential.builder("cleanup")
                                        .independent()
                                        .step("release", () -> released.incrementAndGet())
                                        .step("count", afterCalls::incrementAndGet))
                                .step(Sequential.builder("flaky")
                                        .retry(RetryPolicy.builder()
                                                .maxAttempts(3)
                                                .backoff(Backoff.fixed(Duration.ofSeconds(30)))
                                                .build())
                                        .step("attempt", () -> {
                                            firstAttemptsFailed.countDown();
                                            throw new IllegalStateException("flaky");
                                        })))
                .build();
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "5000")))
                .pool(Pool.named("p"), 2)
                .build();
        final List<Result> results = new ArrayList<>();
        final var thread = new Thread(() -> results.add(runner.run(definition)));
        thread.start();
        try {
            assertThat(firstAttemptsFailed.await(10, TimeUnit.SECONDS)).isTrue();
            // Let both failed attempts finish their finalizers so the whole tree is idle in its retry backoff.
            Thread.sleep(200L);
            runner.close();
            thread.join(20_000L);
        } finally {
            runner.close();
        }

        assertThat(thread.isAlive()).isFalse();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isQuiescent()).isTrue();
        assertThat(results.get(0).status().kind()).isIn(Status.Kind.CANCELED, Status.Kind.FAILED);
        assertThat(afterCalls).as("fixture after callbacks").hasValue(2);
        assertThat(released).as("fixture resource releases").hasValue(2);
    }

    @Test
    @DisplayName("closing the runner during a retry backoff ends the run without waiting out the delay")
    void closeDuringRetryBackoffEndsPromptly() throws InterruptedException {
        final var attempts = new AtomicInteger();
        final var firstAttemptFailed = new CountDownLatch(1);
        final Task definition = Sequential.builder("root")
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.builder()
                                .maxAttempts(3)
                                .backoff(Backoff.fixed(Duration.ofSeconds(30)))
                                .build())
                        .step("probe", () -> {
                            attempts.incrementAndGet();
                            firstAttemptFailed.countDown();
                            throw new IllegalStateException("always fails");
                        }))
                .build();
        final var runner = runner(2, 2000);
        final List<Result> results = new ArrayList<>();
        final var thread = new Thread(() -> results.add(runner.run(definition)));
        thread.start();
        final long elapsedMillis;
        try {
            assertThat(firstAttemptFailed.await(10, TimeUnit.SECONDS)).isTrue();
            final var closedAt = System.nanoTime();
            runner.close();
            thread.join(20_000L);
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closedAt);
        } finally {
            runner.close();
        }

        assertThat(thread.isAlive()).isFalse();
        assertThat(elapsedMillis).isLessThan(10_000L);
        assertThat(attempts).hasValue(1);
        assertThat(results).hasSize(1);
        final var result = results.get(0);
        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.root().orElseThrow().children().get(0).status().kind())
                .isIn(Status.Kind.CANCELED, Status.Kind.FAILED);
        assertThat(result.exitCode()).isEqualTo(1);
    }
}
