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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.CompilationException;
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
        return (CompilationException) org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> runner.build().run(definition), CompilationException.class);
    }

    @Test
    @DisplayName("SCHED-05: nesting the same pool on one path is rejected before execution")
    void nestedSamePoolIsRejected() {
        final AtomicInteger factories = new AtomicInteger();
        final Task definition = parallel("outer", Pool.named("p"))
                .parallel(
                        "inner",
                        Pool.named("p"),
                        inner -> inner.child(forInstance("instance", () -> {
                            factories.incrementAndGet();
                            return new Object();
                        })))
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
    @DisplayName("typed callbacks require a lexical instance binding")
    void missingInstanceBindingIsRejected() {
        final AtomicInteger calls = new AtomicInteger();
        final Task definition = sequential("dangling")
                .step("typed", (Object value) -> calls.incrementAndGet())
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("INSTANCE_BINDING_MISSING");
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
    @DisplayName("duplicate resource keys in one task are rejected at definition time")
    void duplicateResourceKeyIsRejected() {
        final Key<String> key = Key.named("test", "value", String.class);
        assertThatThrownBy(() -> task("root").resource(key, () -> "a").resource(key, () -> "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate resource key");
    }

    @Test
    @DisplayName("ambiguous resource shadowing across scopes is rejected")
    void resourceShadowingIsRejected() {
        final Key<String> key = Key.named("test", "value", String.class);
        final Task definition = sequential("root")
                .resource(key, () -> "outer")
                .step("inner", () -> {}, children -> children.resource(key, () -> "inner"))
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("RESOURCE_SHADOWING");
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
                .child(sequential("dangling").step("typed", (Object value) -> {}))
                .child(parallel("undefined-pool", Pool.named("a")).step("work", () -> {}))
                .build();

        final var error = compileError(definition, Map.of());

        assertThat(error).isNotNull();
        assertThat(error.diagnostics()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(error.getMessage()).contains("INSTANCE_BINDING_MISSING").contains("POOL_UNDEFINED");
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
                .isInstanceOf(org.paramixel.api.exception.ConfigurationException.class)
                .hasMessageContaining(Configuration.WORKERS);
    }
}
