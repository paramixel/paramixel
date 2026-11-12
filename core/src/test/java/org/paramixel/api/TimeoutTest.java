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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.task;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cooperative timeout behavior and the honesty rules around non-quiescent work.
 */
class TimeoutTest {

    private static Runner runner(final Map<String, String> settings) {
        final var configuration = new java.util.LinkedHashMap<String, String>();
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
        final Task definition = task("group")
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
        final Task definition = forInstance("instance", Object::new)
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
        final Task definition = task("group")
                .timeout(Duration.ofMillis(150))
                .parallel(
                        "children",
                        Pool.named("p"),
                        children -> children.child(forInstance("first", () -> {
                                            created.incrementAndGet();
                                            return new Object();
                                        })
                                        .step("hold", () -> block.await(5, TimeUnit.SECONDS)))
                                .child(forInstance("second", () -> {
                                            created.incrementAndGet();
                                            return new Object();
                                        })
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
        final Task definition = forInstance("instance", Object::new)
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
    @DisplayName("TIME-06: canceling a run while a task waits for a permit acquires no resources")
    void cancelWhileWaitingForPermit() {
        final AtomicInteger created = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        // The blocker blocks until released instead of sleeping for the grace period, so cancellation settles well
        // inside the shutdown grace regardless of host core count or load.
        final CountDownLatch release = new CountDownLatch(1);
        final Task definition = parallel("root", Pool.named("p"))
                .child(forInstance("blocker", () -> {
                            created.incrementAndGet();
                            return new Object();
                        })
                        .step("hold", () -> {
                            started.countDown();
                            release.await(60, TimeUnit.SECONDS);
                        }))
                .child(forInstance("waiter", () -> {
                            created.incrementAndGet();
                            return new Object();
                        })
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
}
