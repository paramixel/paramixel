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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ResourceException;

/**
 * Keyed resources, conditional execution, exclusive isolation, and reproducible shuffling.
 */
class ResourceConditionIsolationTest {

    private static final Key<String> PRIMARY = Key.named("test", "primary", String.class);
    private static final Key<String> SECONDARY = Key.named("test", "secondary", String.class);

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    @Test
    @DisplayName("RES-01: two values of the same class resolve through distinct keys")
    void distinctKeysResolveDistinctValues() {
        final Task definition = task("root")
                .resource(PRIMARY, () -> "primary")
                .resource(SECONDARY, () -> "secondary")
                .stepWithContext("verify", (Context context) -> {
                    assertThat(context.require(PRIMARY)).isEqualTo("primary");
                    assertThat(context.require(SECONDARY)).isEqualTo("secondary");
                })
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("RES-02: a missing resource produces a precise framework exception")
    void missingResourceIsPrecise() {
        final Task definition = task("root")
                .stepWithContext("verify", (Context context) -> context.require(PRIMARY))
                .build();

        final var result = run(definition);
        final var failure =
                result.root().orElseThrow().children().get(0).failures().get(0);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(failure.message()).contains("primary");
        assertThat(failure.throwable()).hasValueSatisfying(throwable -> {
            assertThat(throwable).isInstanceOf(ResourceException.class);
            assertThat(((ResourceException) throwable).key()).isEqualTo(PRIMARY);
            assertThat(((ResourceException) throwable).path()).contains("root");
        });
    }

    @Test
    @DisplayName("RES-03: a mid-way acquisition failure releases earlier resources in reverse order")
    void partialAcquisitionReleasesInReverse() {
        final List<String> releases = Collections.synchronizedList(new ArrayList<>());
        final Task definition = task("root")
                .resource(PRIMARY, () -> "primary", value -> releases.add("primary"))
                .resource(
                        SECONDARY,
                        () -> {
                            throw new IllegalStateException("cannot acquire secondary");
                        },
                        value -> releases.add("secondary"))
                .step("body", () -> {})
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(releases).containsExactly("primary");
    }

    @Test
    @DisplayName("RES-05: a failing release is retained together with the body failure")
    void releaseFailureIsRetained() {
        final Task definition = task("root")
                .resource(PRIMARY, () -> "value", value -> {
                    throw new IllegalStateException("release failed");
                })
                .step("body", () -> {
                    throw new IllegalStateException("body failed");
                })
                .build();

        final var result = run(definition);
        final var root = result.root().orElseThrow();

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(root.children().get(0).failures())
                .anySatisfy(failure -> assertThat(failure.message()).contains("body failed"));
        assertThat(root.failures())
                .anySatisfy(failure -> assertThat(failure.message()).contains("release failed"));
    }

    @Test
    @DisplayName("RES: a resource factory may read an earlier resource in the same scope")
    void resourceFactoryCanDependOnEarlierResource() {
        final Key<String> derived = Key.named("test", "derived", String.class);
        final Task definition = task("root")
                .resource(PRIMARY, () -> "base")
                .resource(derived, context -> context.require(PRIMARY) + "-derived")
                .stepWithContext(
                        "verify",
                        (Context context) ->
                                assertThat(context.require(derived)).isEqualTo("base-derived"))
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("FAIL-05: a false condition skips the protected work but still finalizes")
    void falseConditionSkipsWork() {
        final AtomicInteger bodyCalls = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();
        final AtomicInteger factories = new AtomicInteger();
        final Task definition = forInstance("instance", () -> {
                    factories.incrementAndGet();
                    return new Object();
                })
                .onlyIf(context -> false)
                .step("body", bodyCalls::incrementAndGet)
                .after(afterCalls::incrementAndGet)
                .build();

        final var result = run(definition);
        final var node = result.root().orElseThrow();

        assertThat(factories).hasValue(1);
        assertThat(bodyCalls).hasValue(0);
        assertThat(afterCalls).hasValue(1);
        assertThat(node.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(node.reason())
                .hasValueSatisfying(reason -> assertThat(reason).contains("condition"));
    }

    @Test
    @DisplayName("a condition that throws fails the occurrence")
    void throwingConditionFails() {
        final AtomicInteger bodyCalls = new AtomicInteger();
        final Task definition = task("root")
                .when(
                        "conditional",
                        context -> {
                            throw new IllegalStateException("condition failed");
                        },
                        group -> group.step("body", bodyCalls::incrementAndGet))
                .build();

        final var result = run(definition);

        assertThat(bodyCalls).hasValue(0);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
    }

    @Test
    @DisplayName("a true condition runs the protected work")
    void trueConditionRunsWork() {
        final AtomicInteger bodyCalls = new AtomicInteger();
        final Task definition = task("root")
                .when("conditional", context -> true, group -> group.step("body", bodyCalls::incrementAndGet))
                .build();

        run(definition);

        assertThat(bodyCalls).hasValue(1);
    }

    @Test
    @DisplayName("RES-04: an exclusive lock serializes parallel groups")
    void exclusiveLockSerializes() {
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final Task group = sequential("serialized")
                .isolated("lock")
                .before(() -> {
                    final var current = inside.incrementAndGet();
                    peak.accumulateAndGet(current, Math::max);
                })
                .parallel(
                        "children",
                        Pool.named("p"),
                        children -> children.each(
                                List.of("a", "b", "c"), name -> task(name).step("work", () -> Thread.sleep(10L))))
                .after(() -> inside.decrementAndGet())
                .build();

        final Task definition =
                parallel("root", Pool.named("outer")).child(group).child(group).build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("outer"), 2)
                .pool(Pool.named("p"), 3)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(peak.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a nested acquisition of the same lock is rejected before execution")
    void nestedSameLockIsRejected() {
        final Task definition = task("outer")
                .isolated("lock")
                .step("inner", () -> {}, children -> children.isolated("lock").step("work", () -> {}))
                .build();

        final var error =
                org.assertj.core.api.Assertions.catchThrowableOfType(() -> run(definition), CompilationException.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("LOCK_NESTED_SAME");
    }

    @Test
    @DisplayName("shuffle is reproducible for a seed and reports the effective order")
    void shuffleIsReproducible() {
        final List<String> names = List.of("a", "b", "c", "d", "e", "f");
        final Task first = parallel("root", Pool.named("p"))
                .shuffle(42L)
                .each(names, name -> task(name).step("work", () -> {}))
                .build();
        final Task second = parallel("root", Pool.named("p"))
                .shuffle(42L)
                .each(names, name -> task(name).step("work", () -> {}))
                .build();

        final var firstResult = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 6)
                .build()
                .run(first);
        final var secondResult = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 6)
                .build()
                .run(second);

        final var firstOrder = firstResult.root().orElseThrow().executionOrder();
        final var secondOrder = secondResult.root().orElseThrow().executionOrder();
        assertThat(firstOrder).isEqualTo(secondOrder);
        assertThat(firstOrder).containsExactlyInAnyOrderElementsOf(names);
        assertThat(firstResult.root().orElseThrow().shuffleSeed()).hasValue(42L);
    }

    @Test
    @DisplayName("an unseeded shuffle reports the seed it chose")
    void unseededShuffleReportsSeed() {
        final Task definition = parallel("root", Pool.named("p"))
                .shuffle()
                .each(List.of("a", "b", "c"), name -> task(name).step("work", () -> {}))
                .build();
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 3)
                .build()
                .run(definition);

        assertThat(result.root().orElseThrow().shuffleSeed()).isPresent();
    }

    @Test
    @DisplayName("resource keys reject invalid tokens")
    void invalidKeyIsRejected() {
        assertThatThrownBy(() -> Key.named("", "name", String.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Key.named("ns", "bad name", String.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Key.named("ns", "name", null)).isInstanceOf(NullPointerException.class);
    }
}
