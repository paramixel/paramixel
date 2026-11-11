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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scheduler admission, named pools, permit lifetime, and progress guarantees.
 */
class ParallelSchedulingTest {

    private static Result run(final Task definition, final Pool pool, final int capacity) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .pool(pool, capacity)
                .build()
                .run(definition);
    }

    @Test
    @DisplayName("SCHED-01: 100 children with capacity 3 never exceed 3 active subtrees")
    void poolCapacityIsEnforced() {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final Task definition = parallel("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 100).boxed().toList(),
                        index -> task("child-" + index).step("work", () -> {
                            final var current = active.incrementAndGet();
                            peak.accumulateAndGet(current, Math::max);
                            try {
                                Thread.sleep(1L);
                            } finally {
                                active.decrementAndGet();
                            }
                        }))
                .build();

        final var result = run(definition, Pool.named("p"), 3);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(peak.get()).isBetween(1, 3);
        assertThat(active.get()).isZero();
        assertThat(result.occurrenceCount()).isEqualTo(201);
    }

    @Test
    @DisplayName("SCHED-02: two groups sharing a pool share one global budget")
    void sharedPoolIsGlobal() {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final Task group = parallel("group", Pool.named("shared"))
                .each(
                        IntStream.range(0, 10).boxed().toList(),
                        index -> task("child-" + index).step("work", () -> {
                            final var current = active.incrementAndGet();
                            peak.accumulateAndGet(current, Math::max);
                            try {
                                Thread.sleep(2L);
                            } finally {
                                active.decrementAndGet();
                            }
                        }))
                .build();

        final Task definition = task("root")
                .step("first", () -> {}, children -> children.child(group))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("shared"), 2)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(peak.get()).isBetween(1, 2);
    }

    @Test
    @DisplayName("SCHED-04: nested groups with distinct pools complete and report every child")
    void nestedDistinctPoolsComplete() {
        final Task definition = parallel("outer", Pool.named("outer"))
                .parallel(
                        "middle",
                        Pool.named("middle"),
                        middle -> middle.parallel(
                                        "inner",
                                        Pool.named("inner"),
                                        inner -> inner.step("a", () -> {}).step("b", () -> {}))
                                .step("c", () -> {}))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("outer"), 2)
                .pool(Pool.named("middle"), 2)
                .pool(Pool.named("inner"), 2)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.occurrenceCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("SCHED-03: a parent waiting on children does not occupy a worker")
    void coordinatorDoesNotPinWorker() {
        // With a single worker, a parent that stayed on the worker while waiting for its child would deadlock.
        final Task definition = sequential("root")
                .step("nested", () -> {}, children -> children.step("inner", () -> Thread.sleep(5L)))
                .step("after", () -> {})
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(java.util.Map.of(
                        Configuration.WORKERS, "1",
                        Configuration.SHUTDOWN_GRACE_MILLIS, "2000")))
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.isQuiescent()).isTrue();
    }

    @Test
    @DisplayName("SCHED-08: admitted branch descendants are admitted before an unopened sibling")
    void depthFirstAdmissionPreference() {
        final List<String> order = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch firstAdmitted = new CountDownLatch(1);
        final Task definition = parallel("root", Pool.named("p"))
                .child(sequential("a")
                        .step("a1", () -> {
                            order.add("a1");
                            firstAdmitted.countDown();
                        })
                        .step("a2", () -> order.add("a2")))
                .child(task("b").step("b1", () -> order.add("b1")))
                .build();

        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(java.util.Map.of(Configuration.WORKERS, "1")))
                .pool(Pool.named("p"), 2)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(order).containsExactly("a1", "a2", "b1");
    }

    @Test
    @DisplayName("SCHED-11: a failed parallel sibling does not cancel other siblings")
    void failureDoesNotCancelSiblings() {
        final AtomicInteger passed = new AtomicInteger();
        final Task definition = parallel("root", Pool.named("p"))
                .step("fail", () -> {
                    throw new IllegalStateException("boom");
                })
                .step("pass-1", passed::incrementAndGet)
                .step("pass-2", passed::incrementAndGet)
                .build();

        final var result = run(definition, Pool.named("p"), 3);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(passed).hasValue(2);
    }

    @Test
    @DisplayName("SCHED-12: a permit is held through instance creation, body, and finalization")
    void permitSpansFinalization() {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicInteger finalized = new AtomicInteger();
        final Task definition = parallel("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 4).boxed().toList(),
                        index -> forInstance("item-" + index, () -> {
                                    final var current = active.incrementAndGet();
                                    peak.accumulateAndGet(current, Math::max);
                                    return new Object();
                                })
                                .step("body", () -> Thread.sleep(1L))
                                .after(() -> {
                                    finalized.incrementAndGet();
                                    active.decrementAndGet();
                                }))
                .build();

        final var result = run(definition, Pool.named("p"), 2);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(finalized).hasValue(4);
        assertThat(peak.get()).isBetween(1, 2);
        assertThat(active.get()).isZero();
    }

    @Test
    @DisplayName("SCHED-13: a fixture is not created while waiting for a permit")
    void noFixtureWhileWaiting() {
        final AtomicInteger created = new AtomicInteger();
        final CountDownLatch release = new CountDownLatch(1);
        final Task definition = parallel("root", Pool.named("p"))
                .child(forInstance("blocker", () -> {
                            created.incrementAndGet();
                            return new Object();
                        })
                        .step("hold", () -> release.await(5, TimeUnit.SECONDS)))
                .child(forInstance("waiter", () -> {
                            created.incrementAndGet();
                            return new Object();
                        })
                        .step("quick", () -> {}))
                .build();

        final Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        });
        releaser.start();
        final var result = run(definition, Pool.named("p"), 1);
        try {
            releaser.join(5000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(created).hasValue(2);
    }

    @Test
    @DisplayName("SCHED-15: a large finite tree terminates with a stable result cardinality")
    void largeTreeTerminates() {
        final Task definition = parallel("root", Pool.named("p"))
                .each(
                        IntStream.range(0, 500).boxed().toList(),
                        index -> task("child-" + index).step("one", () -> {}).step("two", () -> {}))
                .build();

        final var result = run(definition, Pool.named("p"), 8);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.occurrenceCount()).isEqualTo(1501);
        assertThat(result.isQuiescent()).isTrue();
    }
}
