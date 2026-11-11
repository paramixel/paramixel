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
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Instance ownership, lifecycle order, and finalization guarantees.
 */
class LifecycleTest {

    private static Result run(final Task definition) {
        return Runner.builder().listener(Listener.NOOP).build().run(definition);
    }

    @Test
    @DisplayName("LIFE-01: one factory invocation and one finalizer per matrix item")
    void oneInstancePerItem() {
        final AtomicInteger factories = new AtomicInteger();
        final List<String> finalizers = Collections.synchronizedList(new ArrayList<>());

        final Task definition = parallel("matrix", Pool.named("p"))
                .each(
                        List.of("a", "b", "c"),
                        name -> forInstance(name, () -> {
                                    factories.incrementAndGet();
                                    return name;
                                })
                                .before(value -> assertThat(value).isEqualTo(name))
                                .step("body", value -> assertThat(value).isEqualTo(name))
                                .after(finalizers::add))
                .build();
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 3)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(factories).hasValue(3);
        assertThat(finalizers).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("LIFE-02: definition and compilation do not create instances or run callbacks")
    void definitionIsLazy() {
        final AtomicInteger calls = new AtomicInteger();
        final Task definition = forInstance("instance", () -> {
                    calls.incrementAndGet();
                    return new Object();
                })
                .before(calls::incrementAndGet)
                .step("body", calls::incrementAndGet)
                .after(calls::incrementAndGet)
                .build();

        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("LIFE-03: a skipped child never creates or finalizes its instance")
    void skippedChildHasNoLifecycle() {
        final AtomicInteger factoryCalls = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = sequential("root")
                .step("failing", () -> {
                    throw new IllegalStateException("boom");
                })
                .child(forInstance("skipped", () -> {
                            factoryCalls.incrementAndGet();
                            return new Object();
                        })
                        .after(afterCalls::incrementAndGet))
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(factoryCalls).hasValue(0);
        assertThat(afterCalls).hasValue(0);
        final var skipped = result.root().orElseThrow().children().get(1);
        assertThat(skipped.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(skipped.reason())
                .hasValueSatisfying(reason -> assertThat(reason).contains("dependency"));
    }

    @Test
    @DisplayName("LIFE-04: a throwing factory runs no before, body, or after")
    void factoryFailureSkipsLifecycle() {
        final List<String> calls = new ArrayList<>();
        final Task definition = forInstance("instance", () -> {
                    throw new IllegalStateException("factory failed");
                })
                .before(() -> calls.add("before"))
                .step("body", () -> calls.add("body"))
                .after(() -> calls.add("after"))
                .build();

        final var result = run(definition);

        assertThat(calls).isEmpty();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.root().orElseThrow().failures())
                .anySatisfy(failure -> assertThat(failure.message()).contains("factory failed"));
    }

    @Test
    @DisplayName("LIFE-05: a null instance is rejected without running after")
    void nullInstanceFails() {
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = forInstance("instance", () -> null)
                .after(afterCalls::incrementAndGet)
                .build();

        final var result = run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(afterCalls).hasValue(0);
    }

    @Test
    @DisplayName("LIFE-06: a failing before callback still runs every after callback")
    void beforeFailureStillFinalizes() {
        final List<String> calls = new ArrayList<>();
        final Task definition = forInstance("instance", Object::new)
                .before(() -> {
                    calls.add("before-1");
                    throw new IllegalStateException("before failed");
                })
                .before(() -> calls.add("before-2"))
                .step("body", () -> calls.add("body"))
                .after(() -> calls.add("after-1"))
                .after(() -> calls.add("after-2"))
                .build();

        final var result = run(definition);

        assertThat(calls).containsExactly("before-1", "after-2", "after-1");
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        final var phases = result.root().orElseThrow().phases();
        assertThat(phases).anySatisfy(phase -> {
            assertThat(phase.phase()).isEqualTo(TaskPhase.BEFORE);
            assertThat(phase.name()).isEqualTo("before[0]");
            assertThat(phase.status().kind()).isEqualTo(Status.Kind.FAILED);
        });
        assertThat(phases).anySatisfy(phase -> {
            assertThat(phase.name()).isEqualTo("before[1]");
            assertThat(phase.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        });
    }

    @Test
    @DisplayName("LIFE-08/09: a failing body skips children and the parent aggregates failure")
    void bodyFailureSkipsChildren() {
        final AtomicInteger childCalls = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();
        final Task definition = task("root")
                .step(
                        "body",
                        () -> {
                            throw new IllegalStateException("body failed");
                        },
                        children -> children.step("child", childCalls::incrementAndGet))
                .after(afterCalls::incrementAndGet)
                .build();

        final var result = run(definition);

        assertThat(childCalls).hasValue(0);
        assertThat(afterCalls).hasValue(1);
        final var root = result.root().orElseThrow();
        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(root.children()).hasSize(1);
        final var body = root.children().get(0);
        assertThat(body.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(body.children()).hasSize(1);
        assertThat(body.children().get(0).status().kind()).isEqualTo(Status.Kind.SKIPPED);
    }

    @Test
    @DisplayName("LIFE-10/11: a failing after callback keeps the original failure primary")
    void afterFailurePreservesPrimaryFailure() {
        final Task definition = forInstance("instance", Object::new)
                .step(
                        "body",
                        () -> {
                            throw new IllegalStateException("body failed");
                        },
                        children -> children.step("child", () -> {}))
                .after(() -> {
                    throw new IllegalStateException("after failed");
                })
                .build();

        final var result = run(definition);
        final var root = result.root().orElseThrow();
        final var body = root.children().get(0);

        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(body.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(body.failures()).hasSize(1);
        assertThat(body.failures().get(0).message()).contains("body failed");
        assertThat(root.failures()).hasSize(1);
        assertThat(root.failures().get(0).message()).contains("after failed");
        assertThat(root.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("LIFE-12: a no-instance task runs its after callback after its group")
    void noInstanceAfterRuns() {
        final List<String> calls = new ArrayList<>();
        final Task definition = task("group")
                .step("body", () -> calls.add("body"), children -> children.step("child", () -> calls.add("child")))
                .after(() -> calls.add("after"))
                .build();

        run(definition);

        assertThat(calls).containsExactly("body", "child", "after");
    }

    @Test
    @DisplayName("LIFE-13/14: parallel children receive their own binding, never an ancestor instance")
    void bindingsDoNotMix() {
        final List<String> seen = Collections.synchronizedList(new ArrayList<>());
        final Task definition = parallel("matrix", Pool.named("p"))
                .each(
                        List.of("a", "b", "c", "d"),
                        name -> forInstance(name, () -> name).step("verify", (String value) -> {
                            assertThat(value).isEqualTo(name);
                            seen.add(value);
                        }))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 4)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(seen).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    @DisplayName("LIFE-15: re-running a definition creates fresh instances")
    void rerunCreatesFreshInstances() {
        final AtomicInteger factories = new AtomicInteger();
        final Task definition = forInstance("instance", () -> {
                    factories.incrementAndGet();
                    return new Object();
                })
                .step("body", () -> {})
                .build();

        run(definition);
        run(definition);

        assertThat(factories).hasValue(2);
    }

    @Test
    @DisplayName("LIFE-18: an AutoCloseable instance is not closed implicitly")
    void autoCloseableIsNotClosedImplicitly() {
        final AtomicInteger closes = new AtomicInteger();
        final class Closeable implements AutoCloseable {
            @Override
            public void close() {
                closes.incrementAndGet();
            }
        }
        final Task definition = forInstance("instance", Closeable::new)
                .step("body", instance -> assertThat(instance).isNotNull())
                .build();

        run(definition);

        assertThat(closes).hasValue(0);
    }
}
