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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.RetryPolicy;

/**
 * Cancellation, shutdown, and high-contention interleavings.
 */
class CancellationStressTest {

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
        final var started = new CountDownLatch(1);
        final Task definition = parallel("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 4).boxed().toList(),
                        index -> task("child-" + index).step("wait", () -> {
                            started.countDown();
                            Thread.sleep(5000L);
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

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        runner.close();
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
        final Task definition = task("root")
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
        final Task definition = parallel("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 200).boxed().toList(),
                        index -> sequential("child-" + index)
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
        final Task definition = sequential("root")
                .child(sequential("retried").retry(RetryPolicy.attempts(50)).step("probe", () -> {
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
}
