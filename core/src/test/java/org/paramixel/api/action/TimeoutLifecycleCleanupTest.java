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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.TestPools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;

/**
 * Verifies that a {@code Timeout} cancels the body of an enclosed lifecycle but still runs the
 * lifecycle's {@code after} action once its {@code before} has been attempted, and that the
 * {@code Timeout} completes only after that cleanup has finished.
 */
@DisplayName("Timeout preserves started lifecycle cleanup")
@org.junit.jupiter.api.Timeout(value = 60, unit = TimeUnit.SECONDS)
class TimeoutLifecycleCleanupTest {

    private static final Duration DEADLINE = Duration.ofMillis(300);

    private static Runner runner() {
        return Runner.builder()
                .configuration(TestPools.configuration(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
                .build();
    }

    private static void block() throws InterruptedException {
        Thread.sleep(60_000);
    }

    @Test
    @DisplayName("deadline during body: body is cancelled and after runs before the Timeout completes")
    void bodyTimeoutRunsAfter() {
        var afterCalls = new AtomicInteger();
        var scope = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("body", context -> block()))
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();
        var timeout = Timeout.timeout("timeout").body(scope).timeout(DEADLINE).build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(afterCalls.get()).as("after ran before run() returned").isEqualTo(1);
        assertThat(root.status().isFailed()).isTrue();
        var scopeDescriptor = root.children().get(0);
        assertThat(scopeDescriptor.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(scopeDescriptor.children().get(0).status().isAborted()).isTrue();
        assertThat(scopeDescriptor.after().orElseThrow().status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("deadline during before: before is interrupted and after runs")
    void beforeTimeoutRunsAfter() {
        var beforeInterrupted = new AtomicBoolean();
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();
        var scope = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    try {
                        block();
                    } catch (InterruptedException e) {
                        beforeInterrupted.set(true);
                        throw e;
                    }
                }))
                .body(Step.step("body", context -> bodyCalls.incrementAndGet()))
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();
        var timeout = Timeout.timeout("timeout").body(scope).timeout(DEADLINE).build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(beforeInterrupted.get()).as("before interrupted").isTrue();
        assertThat(bodyCalls.get()).as("body never ran").isZero();
        assertThat(afterCalls.get()).as("after ran").isEqualTo(1);
        assertThat(root.children().get(0).after().orElseThrow().status().isPassed())
                .isTrue();
    }

    @Test
    @DisplayName("deadline before a scope starts: neither before nor after runs")
    void unstartedScopeSkipsAfter() {
        var beforeCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();
        var scope = Scope.scope("scope")
                .before(Step.step("before", context -> beforeCalls.incrementAndGet()))
                .body(Step.step("body", context -> {}))
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();
        var timeout = Timeout.timeout("timeout")
                .body(Sequential.sequential("sequential")
                        .child(Step.step("blocking", context -> block()))
                        .child(scope)
                        .build())
                .timeout(DEADLINE)
                .build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(beforeCalls.get()).as("before never ran").isZero();
        assertThat(afterCalls.get()).as("after never ran").isZero();
        var scopeDescriptor = root.children().get(0).children().get(1);
        assertThat(scopeDescriptor.before().orElseThrow().status().isAborted()).isTrue();
        assertThat(scopeDescriptor.after().orElseThrow().status().isAborted()).isTrue();
    }

    @Test
    @DisplayName("deadline while after is running: after is not interrupted and completes")
    void runningAfterIsNotInterrupted() {
        var afterInterrupted = new AtomicBoolean();
        var afterCompleted = new AtomicBoolean();
        var scope = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("body", context -> {}))
                .after(Step.step("after", context -> {
                    try {
                        Thread.sleep(DEADLINE.toMillis() * 3);
                        afterCompleted.set(true);
                    } catch (InterruptedException e) {
                        afterInterrupted.set(true);
                        throw e;
                    }
                }))
                .build();
        var timeout = Timeout.timeout("timeout").body(scope).timeout(DEADLINE).build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(afterInterrupted.get()).as("after not interrupted").isFalse();
        assertThat(afterCompleted.get())
                .as("after completed before run() returned")
                .isTrue();
        assertThat(root.status().isFailed()).as("timeout still reported").isTrue();
        assertThat(root.children().get(0).after().orElseThrow().status().isPassed())
                .isTrue();
    }

    @Test
    @DisplayName("Instance fixture is closed when the body times out")
    void instanceIsClosedOnTimeout() {
        var closed = new AtomicBoolean();
        AutoCloseable fixture = () -> closed.set(true);
        var instance = Instance.of("instance", AutoCloseable.class, () -> fixture)
                .body(Step.step("body", context -> block()))
                .build();
        var timeout =
                Timeout.timeout("timeout").body(instance).timeout(DEADLINE).build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(closed.get()).as("fixture closed").isTrue();
        assertThat(root.status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("Instance not yet instantiated: neither factory nor destroy runs")
    void unstartedInstanceSkipsDestroy() {
        var factoryCalls = new AtomicInteger();
        var closed = new AtomicBoolean();
        var instance = Instance.of("instance", AutoCloseable.class, () -> {
                    factoryCalls.incrementAndGet();
                    return (AutoCloseable) () -> closed.set(true);
                })
                .body(Step.step("body", context -> {}))
                .build();
        var timeout = Timeout.timeout("timeout")
                .body(Sequential.sequential("sequential")
                        .child(Step.step("blocking", context -> block()))
                        .child(instance)
                        .build())
                .timeout(DEADLINE)
                .build();

        var root = runner().run(timeout).descriptor().orElseThrow();

        assertThat(factoryCalls.get()).as("factory never ran").isZero();
        assertThat(closed.get()).as("destroy never ran").isFalse();
        var instanceDescriptor = root.children().get(0).children().get(1);
        assertThat(instanceDescriptor.before().orElseThrow().status().isAborted())
                .isTrue();
        assertThat(instanceDescriptor.after().orElseThrow().status().isAborted())
                .isTrue();
    }

    @Test
    @DisplayName("parallel scopes: every started scope runs its after")
    void parallelScopesAllRunAfter() {
        var scopes = 3;
        var started = new AtomicInteger();
        var afterCalls = new AtomicInteger();
        var parallel = Parallel.parallel("parallel", "pscopes");
        for (var i = 0; i < scopes; i++) {
            parallel.child(Scope.scope("scope-" + i)
                    .before(Step.step("before", context -> started.incrementAndGet()))
                    .body(Step.step("body", context -> block()))
                    .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                    .build());
        }
        var timeout = Timeout.timeout("timeout")
                .body(parallel.build())
                .timeout(Duration.ofSeconds(1))
                .build();

        runner().run(timeout);

        assertThat(started.get()).as("all scopes started").isEqualTo(scopes);
        assertThat(afterCalls.get()).as("every started scope ran after").isEqualTo(scopes);
    }

    @Test
    @DisplayName("leaf ignoring interruption: Timeout abandons after the grace period with a terminal tree")
    void uncooperativeLeafAbandonsWithTerminalTree() {
        var stop = new AtomicBoolean();
        var scope = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("stuck", context -> {
                    while (!stop.get()) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            // deliberately non-cooperative
                        }
                    }
                }))
                .after(Step.step("after", context -> {}))
                .build();
        var timeout = Timeout.timeout("timeout").body(scope).timeout(DEADLINE).build();

        try {
            var start = System.nanoTime();
            var root = runner().run(timeout).descriptor().orElseThrow();
            var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(elapsedMillis).as("bounded by deadline plus grace").isLessThan(15_000L);
            assertThat(root.status().isFailed()).isTrue();
            for (var descriptor : flatten(root)) {
                assertThat(descriptor.isCompleted())
                        .as("descriptor %s is terminal", descriptor.action().displayName())
                        .isTrue();
            }
        } finally {
            stop.set(true);
        }
    }

    private static List<Descriptor> flatten(final Descriptor root) {
        var result = new ArrayList<Descriptor>();
        collect(root, result);
        return result;
    }

    private static void collect(final Descriptor descriptor, final List<Descriptor> result) {
        result.add(descriptor);
        descriptor.before().ifPresent(before -> collect(before, result));
        for (var child : descriptor.children()) {
            collect(child, result);
        }
        descriptor.after().ifPresent(after -> collect(after, result));
    }
}
