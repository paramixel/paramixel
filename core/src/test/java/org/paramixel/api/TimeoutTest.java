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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.compiler.PlanCompiler;
import nonapi.org.paramixel.execution.EventPublisher;
import nonapi.org.paramixel.execution.ExecutionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.Backoff;
import org.paramixel.api.support.RetryPolicy;

/**
 * Cooperative timeout behavior and the honesty rules around non-quiescent work.
 */
class TimeoutTest {

    private static Runner runner(final Map<String, String> settings) {
        final var configuration = new LinkedHashMap<String, String>();
        configuration.put(Configuration.ANSI, "false");
        configuration.putAll(settings);
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(configuration))
                .build();
    }

    @Test
    @DisplayName("TIME-01: a cooperative body is interrupted, finalizers run, and the task fails")
    void cooperativeTimeout() {
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = Sequential.builder("group")
                .timeout(Duration.ofMillis(100))
                .step("body", () -> Thread.sleep(5000L), children -> children.step("child", () -> {}))
                .after(afterCalls::incrementAndGet)
                .build();

        final var result = runner(Map.of()).run(definition);
        final var node = result.root().orElseThrow();

        assertThat(node.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(node.timedOut()).isTrue();
        assertThat(afterCalls).hasValue(1);
        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.timedOutCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TIME-02: an uncooperative callback yields a non-quiescent result and withheld cleanup")
    void uncooperativeTimeout() {
        final CountDownLatch stuckStarted = new CountDownLatch(1);
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = Sequential.builder("instance")
                .timeout(Duration.ofMillis(100))
                .step("stuck", () -> {
                    stuckStarted.countDown();
                    final var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2000);
                    while (System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(20L);
                        } catch (InterruptedException e) {
                            // Deliberately ignore interruption: this callback is uncooperative.
                        }
                    }
                })
                .after(afterCalls::incrementAndGet)
                .build();

        final var result =
                runner(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "100")).run(definition);

        assertThat(result.isQuiescent()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(afterCalls).hasValue(0);
        assertThat(result.root().orElseThrow().status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("NON_QUIESCENT"));
    }

    @Test
    @DisplayName("TIME-03: a timed-out group never creates fixtures for children that were still queued")
    void queuedChildrenNeverCreateFixtures() {
        final AtomicInteger created = new AtomicInteger();
        final CountDownLatch block = new CountDownLatch(1);
        final Task definition = Sequential.builder("group")
                .timeout(Duration.ofMillis(150))
                .parallel(
                        "children",
                        Pool.named("p"),
                        children -> children.step(Sequential.builder("first")
                                        .before(created::incrementAndGet)
                                        .step("hold", () -> block.await(5, TimeUnit.SECONDS)))
                                .step(Sequential.builder("second")
                                        .before(created::incrementAndGet)
                                        .step("quick", () -> {})))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "5000")))
                .pool(Pool.named("p"), 1)
                .build()
                .run(definition);

        block.countDown();
        assertThat(created).hasValue(1);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.root().orElseThrow().timedOut()).isTrue();
    }

    @Test
    @DisplayName("TIME-04: a timeout during before runs no body and still finalizes")
    void timeoutDuringBefore() {
        final AtomicInteger bodyCalls = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = Sequential.builder("instance")
                .timeout(Duration.ofMillis(100))
                .before(() -> Thread.sleep(5000L))
                .step("body", bodyCalls::incrementAndGet)
                .after(afterCalls::incrementAndGet)
                .build();

        final var result = runner(Map.of()).run(definition);

        assertThat(bodyCalls).hasValue(0);
        assertThat(afterCalls).hasValue(1);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.isQuiescent()).isTrue();
    }

    @Test
    @DisplayName("TIME-05: the engine's own result carries its infrastructure diagnostics, independent of the runner")
    void engineResultCarriesInfrastructureDiagnostics() {
        final Task definition = Sequential.builder("root")
                .timeout(Duration.ofMillis(80))
                .step("stuck", () -> {
                    final var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
                    while (System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException e) {
                            // Deliberately ignore interruption: this callback is uncooperative.
                        }
                    }
                })
                .build();
        final var configuration =
                Configuration.of(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "80", Configuration.ANSI, "false"));
        final EventPublisher publisher = new EventPublisher() {
            @Override
            public void publish(final TaskEvent event) {
                // Events are irrelevant here.
            }

            @Override
            public void warning(final String message) {
                // Warnings are irrelevant here.
            }
        };
        final var plan = new PlanCompiler(configuration, Map.of()).compile(definition);

        final var result = new ExecutionEngine("engine-diagnostics", plan, configuration, publisher).run();

        assertThat(result.isQuiescent()).isFalse();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("NON_QUIESCENT"));
        assertThat(result.failures())
                .anySatisfy(failure -> assertThat(failure.phase()).isEqualTo("NON_QUIESCENT"));
    }

    @Test
    @DisplayName("TIME-06: canceling a run while a task waits for a permit acquires no resources")
    void cancelWhileWaitingForPermit() {
        final AtomicInteger created = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        // The blocker blocks until released instead of sleeping for the grace period, so cancellation settles well
        // inside the shutdown grace regardless of host core count or load.
        final CountDownLatch release = new CountDownLatch(1);
        final Task definition = Parallel.builder("root", Pool.named("p"))
                .step(Sequential.builder("blocker")
                        .before(created::incrementAndGet)
                        .step("hold", () -> {
                            started.countDown();
                            release.await(60, TimeUnit.SECONDS);
                        }))
                .step(Sequential.builder("waiter")
                        .before(created::incrementAndGet)
                        .step("quick", () -> {}))
                .build();

        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "5000")))
                .pool(Pool.named("p"), 1)
                .build();
        final var holder = new Thread(() -> runner.run(definition));
        holder.start();
        try {
            assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
            runner.close();
            release.countDown();
            holder.join(10_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            runner.close();
        }

        assertThat(holder.isAlive()).isFalse();
        assertThat(created).hasValue(1);
    }

    @Test
    @DisplayName("TIME-06: grace covers only the interrupted callback, not finalizers that start after it quiesced")
    void graceDoesNotCoverFinalizersStartedAfterQuiescence() {
        final AtomicInteger afterCompleted = new AtomicInteger();
        final Task definition = Sequential.builder("group")
                .timeout(Duration.ofMillis(100))
                .step("body", () -> Thread.sleep(5000L))
                .after(() -> {
                    Thread.sleep(1500L);
                    afterCompleted.incrementAndGet();
                })
                .build();

        final var result =
                runner(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "500")).run(definition);
        final var node = result.root().orElseThrow();

        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.contains("NON_QUIESCENT"));
        assertThat(node.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(node.timedOut()).isTrue();
        assertThat(afterCompleted).hasValue(1);
    }

    @Test
    @DisplayName("TIME-07: a callback that polls Context.isCanceled() observes the timeout and quiesces")
    void pollingCallbackObservesTimeout() {
        final AtomicInteger observed = new AtomicInteger();
        final Task definition = Sequential.builder("group")
                .timeout(Duration.ofMillis(100))
                .stepWithContext("spin", (Context context) -> {
                    // Busy-polls without blocking, so only the cancellation flag can end the loop in time; the safety
                    // deadline keeps a broken flag from leaving a spinning daemon thread behind.
                    final var safetyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    var spins = 0L;
                    while (!context.isCanceled()) {
                        spins++;
                        if ((spins & 0xFFFFFL) == 0 && System.nanoTime() > safetyDeadline) {
                            return;
                        }
                    }
                    observed.incrementAndGet();
                })
                .build();

        final var result =
                runner(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "2000")).run(definition);

        assertThat(result.isQuiescent()).isTrue();
        assertThat(observed).hasValue(1);
        assertThat(result.root().orElseThrow().timedOut()).isTrue();
    }

    @Test
    @DisplayName("TIME-08: a timeout while the subtree is idle still finalizes every started descendant")
    void timeoutWhileSubtreeIdleFinalizesStartedDescendants() {
        final AtomicInteger afterCalls = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();
        final CountDownLatch firstAttemptFailed = new CountDownLatch(1);
        final Task definition = Sequential.builder("root")
                .timeout(Duration.ofMillis(300))
                .step(Sequential.builder("fixture")
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
                                    firstAttemptFailed.countDown();
                                    throw new IllegalStateException("flaky");
                                })))
                .build();

        final var result =
                runner(Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "5000")).run(definition);

        assertThat(firstAttemptFailed.getCount()).isZero();
        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.root().orElseThrow().timedOut()).isTrue();
        assertThat(afterCalls).as("fixture after callbacks").hasValue(1);
        assertThat(released).as("fixture finalizers").hasValue(1);
    }

    @Test
    @DisplayName("TIME-09: zero-delay retries keep the engine's pending-timer count balanced")
    void zeroDelayRetriesKeepTimerAccountingBalanced() throws Exception {
        // A negative pending-timer count disables the stall detection, which only fires when the count is zero.
        final Task definition = Sequential.builder("root")
                .step(Sequential.builder("retried")
                        .retry(RetryPolicy.attempts(3))
                        .step("probe", () -> {
                            throw new IllegalStateException("always fails");
                        }))
                .build();
        final var configuration = Configuration.of(Map.of(Configuration.ANSI, "false"));
        final EventPublisher publisher = new EventPublisher() {
            @Override
            public void publish(final TaskEvent event) {
                // Events are irrelevant here.
            }

            @Override
            public void warning(final String message) {
                // Warnings are irrelevant here.
            }
        };
        final var plan = new PlanCompiler(configuration, Map.of()).compile(definition);
        final var engine = new ExecutionEngine("timer-accounting", plan, configuration, publisher);

        final var result = engine.run();

        assertThat(result.attemptCount()).isEqualTo(3);
        final Field field = ExecutionEngine.class.getDeclaredField("pendingTimers");
        field.setAccessible(true);
        assertThat(((AtomicInteger) field.get(engine)).get()).isZero();
    }

    @Test
    @DisplayName("TIME-10: a finished attempt's timeout does not expire during the retry delay")
    void timeoutDoesNotSpanRetryDelay() {
        final AtomicInteger attempts = new AtomicInteger();
        // Each attempt restarts the deadline, so the delay between attempts must not be charged to either attempt.
        final Task definition = Sequential.builder("flaky")
                .timeout(Duration.ofMillis(200))
                .retry(RetryPolicy.builder()
                        .maxAttempts(2)
                        .backoff(Backoff.fixed(Duration.ofMillis(600)))
                        .build())
                .step("attempt", () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("first attempt fails");
                    }
                })
                .build();

        final var result = runner(Map.of()).run(definition);
        final var node = result.root().orElseThrow();

        assertThat(attempts).hasValue(2);
        assertThat(node.timedOut()).isFalse();
        assertThat(node.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.timedOutCount()).isZero();
    }

    @Test
    @DisplayName("TIME-11: work still queued for a worker at timeout is not non-quiescent and never starts")
    void queuedWorkAtTimeoutIsNotNonQuiescent() {
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger quickCalls = new AtomicInteger();
        // One worker: the busy step occupies it, so the timed-out task's first job waits in the ready queue and has
        // never started when the timeout and the shutdown grace expire.
        final Task definition = Parallel.builder("root", Pool.named("p"))
                .step("busy", () -> Thread.sleep(1500L))
                .step(Sequential.builder("queued")
                        .before(created::incrementAndGet)
                        .timeout(Duration.ofMillis(100))
                        .step("quick", quickCalls::incrementAndGet))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(
                        Configuration.ANSI, "false",
                        Configuration.WORKERS, "1",
                        Configuration.SHUTDOWN_GRACE_MILLIS, "300")))
                .pool(Pool.named("p"), 2)
                .build()
                .run(definition);
        final var root = result.root().orElseThrow();
        final var busy = root.children().get(0);
        final var queued = root.children().get(1);

        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.contains("NON_QUIESCENT"));
        assertThat(busy.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(queued.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(queued.timedOut()).isTrue();
        assertThat(created).as("fixture factory of the timed-out task").hasValue(0);
        assertThat(quickCalls).hasValue(0);
    }
}
