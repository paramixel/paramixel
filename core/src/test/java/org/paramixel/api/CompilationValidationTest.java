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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Compiler validation: every illegal plan must fail before any user code runs.
 */
class CompilationValidationTest {

    private static CompilationException compileError(final Task definition, final Map<Pool, Integer> pools) {
        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of("paramixel.ansi", "false")));
        pools.forEach(runner::pool);
        return Assertions.catchThrowableOfType(
                CompilationException.class, () -> runner.build().run(definition));
    }

    @Test
    @DisplayName("SCHED-05: nesting the same pool on one path is rejected before execution")
    void nestedSamePoolIsRejected() {
        final AtomicInteger factories = new AtomicInteger();
        final Task definition = parallel("outer", Pool.named("p"))
                .parallel("inner", Pool.named("p"), inner -> inner.child(sequential("instance")))
                .build();

        final var error = compileError(definition, Map.of(Pool.named("p"), 4));

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("POOL_NESTED_SAME");
        assertThat(factories).hasValue(0);
    }

    @Test
    @DisplayName("SCHED-06: a cyclic cross-pool acquisition order is rejected")
    void acquisitionCycleIsRejected() {
        final Task definition = task("root")
                .independent()
                .parallel(
                        "a-then-b",
                        Pool.named("a"),
                        a -> a.parallel("b", Pool.named("b"), b -> b.step("work", () -> {})))
                .parallel(
                        "b-then-a",
                        Pool.named("b"),
                        b -> b.parallel("a", Pool.named("a"), a -> a.step("work", () -> {})))
                .build();

        final var error = compileError(definition, Map.of(Pool.named("a"), 1, Pool.named("b"), 1));

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("ACQUISITION_CYCLE");
    }

    @Test
    @DisplayName("SCHED-08: a lock held around an owned pool and the reverse order elsewhere is rejected")
    void lockAroundOwnedPoolCycleIsRejected() {
        final AtomicInteger calls = new AtomicInteger();
        final Task lockThenPool = parallel("lock-then-pool", Pool.named("p"))
                .isolated("l")
                .step("child", calls::incrementAndGet)
                .build();
        final Task poolThenLock = parallel("pool-then-lock", Pool.named("p"))
                .child(task("locked").isolated("l").step("work", calls::incrementAndGet))
                .build();
        final Task definition = parallel("root", Pool.named("root"))
                .child(lockThenPool)
                .child(poolThenLock)
                .build();

        final var error = compileError(definition, Map.of(Pool.named("root"), 2, Pool.named("p"), 1));

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("ACQUISITION_CYCLE");
        assertThat(calls).hasValue(0);
    }

    @Test
    void childlessParallelTaskDoesNotCreateAnAcquisitionCycle() {
        final var pool = Pool.named("p");
        final var calls = new AtomicInteger();
        final var lockOnly = parallel("lock-only", pool)
                .isolated("l")
                .before(calls::incrementAndGet)
                .build();
        final var poolThenLock = parallel("pool-then-lock", pool)
                .child(task("locked").isolated("l").before(calls::incrementAndGet))
                .build();
        final var definition = parallel("root", Pool.named("root"))
                .child(lockOnly)
                .child(poolThenLock)
                .build();

        try (var runner = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("root"), 2)
                .pool(pool, 1)
                .build()) {
            assertThat(runner.run(definition).status().kind()).isEqualTo(Status.Kind.PASSED);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    @DisplayName("SCHED-09: a lock held around an owned pool compiles when no path inverts the order")
    void lockAroundOwnedPoolWithoutInversionRuns() {
        final AtomicInteger calls = new AtomicInteger();
        final Task definition = parallel("lock-then-pool", Pool.named("p"))
                .isolated("l")
                .step("first", calls::incrementAndGet)
                .step("second", calls::incrementAndGet)
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of("paramixel.ansi", "false")))
                .pool(Pool.named("p"), 1)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("SCHED-07: an undefined pool is rejected before any user callback")
    void undefinedPoolIsRejected() {
        final AtomicInteger calls = new AtomicInteger();
        final Task definition = parallel("root", Pool.named("missing"))
                .step("work", calls::incrementAndGet)
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("POOL_UNDEFINED").contains("paramixel.parallelism.missing");
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("retry and repeat cannot be combined")
    void retryAndRepeatAreRejected() {
        final Task definition = sequential("root")
                .repeat(
                        "both",
                        repeat -> repeat.maxIterations(2)
                                .retry(RetryPolicy.attempts(2))
                                .step("work", () -> {}))
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("RETRY_AND_REPEAT");
    }

    @Test
    @DisplayName("unbounded retry and repeat policies are rejected")
    void unboundedPoliciesAreRejected() {
        assertThatThrownBy(() -> RetryPolicy.attempts(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryPolicy.attempts(RetryPolicy.MAX_ATTEMPTS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryPolicy.builder()
                        .maxAttempts(2)
                        .retryOn(Status.Kind.CANCELED)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANCELED");
        assertThatThrownBy(() -> RepeatPolicy.builder()
                        .maxIterations(RepeatPolicy.MAX_ITERATIONS + 1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("all independent errors are aggregated into one exception")
    void diagnosticsAreAggregated() {
        final Task definition = task("root")
                .child(sequential("dangling"))
                .child(parallel("undefined-pool", Pool.named("a")).step("work", () -> {}))
                .child(parallel("undefined-pool-2", Pool.named("b")))
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.diagnostics()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(error.getMessage()).contains("POOL_UNDEFINED");
    }

    @Test
    @DisplayName("invalid timeout and unknown configuration are rejected")
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> task("root").timeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);

        final var runner = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.WORKERS, "0")))
                .build();
        assertThatThrownBy(() -> runner.run(task("root").step("work", () -> {}).build()))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining(Configuration.WORKERS);
    }
}
