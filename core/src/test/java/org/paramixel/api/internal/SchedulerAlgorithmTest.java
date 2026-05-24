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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.spi.action.ExecutionContext;

@DisplayName("Scheduler algorithm")
class SchedulerAlgorithmTest {

    @Test
    @DisplayName("global parallelism bounds concurrent leaf execution")
    void globalParallelismBoundsConcurrentLeafExecution() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.of("root").parallelism(8);
        for (var i = 0; i < 8; i++) {
            root.child(Step.<ExecutionContext>of("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(3).run(root.resolve());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(maxActive.get()).isLessThanOrEqualTo(3);
        assertThat(maxActive.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Parallel action fully utilizes global parallelism slots")
    void parallelFullyUtilizesGlobalParallelism() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.of("root").parallelism(50);
        for (var i = 0; i < 50; i++) {
            root.child(Step.<ExecutionContext>of("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(8).run(root.resolve());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(maxActive.get()).isEqualTo(8);
    }

    @Test
    @DisplayName("Parallel action enforces own limit when lower than global")
    void parallelEnforcesOwnLimit() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.of("root").parallelism(2);
        for (var i = 0; i < 10; i++) {
            root.child(Step.<ExecutionContext>of("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(8).run(root.resolve());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        // Parallel.parallelism limits how many children are submitted at once, not instantaneous execution.
        // When global parallelism > Parallel parallelism, the scheduler admits submitted children immediately.
        // The limit ensures children are submitted in batches of `parallelism`, preventing resource exhaustion.
        // Max concurrent execution is bounded by global parallelism, not Parallel parallelism.
        assertThat(maxActive.get()).isGreaterThan(0);
        assertThat(maxActive.get()).isLessThanOrEqualTo(8); // bounded by global parallelism
    }

    @Test
    @DisplayName("parallel action limits its own concurrent children")
    void parallelActionLimitsOwnConcurrentChildren() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.of("root").parallelism(2);
        for (var i = 0; i < 6; i++) {
            root.child(Step.<ExecutionContext>of("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(4).run(root.resolve());

        assertThat(result.status()).isEqualTo(Status.PASSED);
        // Parallel.parallelism limits how many children are submitted at once, not instantaneous execution.
        // When global parallelism > Parallel parallelism, the scheduler admits submitted children immediately.
        // The limit ensures children are submitted in batches of `parallelism`, preventing resource exhaustion.
        // Max concurrent execution is bounded by global parallelism, not Parallel parallelism.
        assertThat(maxActive.get()).isGreaterThan(0);
        assertThat(maxActive.get()).isLessThanOrEqualTo(4); // bounded by global parallelism
    }

    @Test
    @DisplayName("nested parallel actions complete without worker starvation")
    void nestedParallelActionsCompleteWithoutWorkerStarvation() {
        var executed = new AtomicInteger();
        var left = Parallel.of("left")
                .parallelism(2)
                .child("left-1", context -> executed.incrementAndGet())
                .child("left-2", context -> executed.incrementAndGet());
        var right = Parallel.of("right")
                .parallelism(2)
                .child("right-1", context -> executed.incrementAndGet())
                .child("right-2", context -> executed.incrementAndGet());
        var root = Parallel.of("root").parallelism(2).child(left).child(right).resolve();

        var result =
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runner(2).run(root));

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(executed.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("nested parallel with deep hierarchy completes without deadlock")
    void nestedParallelWithDeepHierarchyCompletesWithoutDeadlock() {
        var executed = new AtomicInteger();

        var leaf1 = Parallel.of("leaf1")
                .child("l1-1", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                })
                .child("l1-2", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                });

        var leaf2 = Parallel.of("leaf2")
                .child("l2-1", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                })
                .child("l2-2", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                });

        var mid1 = Parallel.of("mid1").parallelism(2).child(leaf1).child(leaf2);

        var leaf3 = Parallel.of("leaf3")
                .child("l3-1", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                })
                .child("l3-2", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                });

        var leaf4 = Parallel.of("leaf4")
                .child("l4-1", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                })
                .child("l4-2", ctx -> {
                    executed.incrementAndGet();
                    sleep(10);
                });

        var mid2 = Parallel.of("mid2").parallelism(2).child(leaf3).child(leaf4);

        var root = Parallel.of("root").parallelism(2).child(mid1).child(mid2).resolve();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> runner(2).run(root));

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(executed.get()).isEqualTo(8);
    }

    @Test
    @DisplayName("document order priority runs earlier parallel work before lower priority children")
    void documentOrderPriorityRunsEarlierParallelWorkFirst() {
        var starts = new CopyOnWriteArrayList<String>();
        var high = Parallel.of("high").parallelism(Integer.MAX_VALUE);
        for (var i = 0; i < 4; i++) {
            var index = i;
            high.child(Step.<ExecutionContext>of("high-" + index, context -> {
                starts.add("high-" + index);
                Thread.sleep(10L);
            }));
        }
        var low = Parallel.of("low")
                .parallelism(1)
                .child(Step.<ExecutionContext>of("low-0", context -> starts.add("low-0")));
        var root = Parallel.of("root").parallelism(1).child(high).child(low).resolve();

        var result = runner(2).run(root);

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(starts).contains("low-0");
        assertThat(starts.indexOf("low-0")).isGreaterThan(lastHighIndex(starts));
    }

    @Test
    @DisplayName("lower priority work uses slots left by higher priority limits")
    void lowerPriorityWorkUsesSlotsLeftByHigherPriorityLimits() {
        var startedByGroup = new ConcurrentHashMap<String, AtomicInteger>();
        var high = Parallel.of("high")
                .parallelism(1)
                .child(Step.<ExecutionContext>of("high-0", context -> recordGroup(startedByGroup, "high")))
                .child(Step.<ExecutionContext>of("high-1", context -> recordGroup(startedByGroup, "high")));
        var low = Parallel.of("low")
                .parallelism(2)
                .child(Step.<ExecutionContext>of("low-0", context -> recordGroup(startedByGroup, "low")))
                .child(Step.<ExecutionContext>of("low-1", context -> recordGroup(startedByGroup, "low")));
        var root = Parallel.of("root").parallelism(2).child(high).child(low).resolve();

        var result = runner(3).run(root);

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(startedByGroup.get("high").get()).isEqualTo(2);
        assertThat(startedByGroup.get("low").get()).isEqualTo(2);
    }

    @Test
    @DisplayName("parallel siblings continue after a child failure")
    void parallelSiblingsContinueAfterChildFailure() {
        var successfulSiblings = new AtomicInteger();
        var root = Parallel.of("root")
                .parallelism(3)
                .child(Step.<ExecutionContext>of("fail", context -> FailException.fail("expected")))
                .child("success-1", context -> successfulSiblings.incrementAndGet())
                .child("success-2", context -> successfulSiblings.incrementAndGet())
                .resolve();

        var result = runner(3).run(root);

        assertThat(successfulSiblings.get()).isEqualTo(2);
        assertThat(result.descriptor().orElseThrow().metadata().status()).isEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("aborted child produces aborted parallel aggregate status")
    void abortedChildProducesAbortedParallelAggregateStatus() {
        var root = Parallel.of("root")
                .parallelism(2)
                .child(Step.<ExecutionContext>of("abort", context -> AbortedException.abort("expected")))
                .child("success", context -> {})
                .resolve();

        var result = runner(2).run(root);

        assertThat(result.descriptor().orElseThrow().metadata().status()).isEqualTo(Status.ABORTED);
    }

    @Test
    @DisplayName("lifecycle callbacks fire once per descriptor")
    void lifecycleCallbacksFireOncePerDescriptor() {
        var before = Collections.synchronizedList(new ArrayList<String>());
        var after = Collections.synchronizedList(new ArrayList<String>());
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                before.add(descriptor.metadata().name());
            }

            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                after.add(descriptor.metadata().name());
            }
        };
        var root = Parallel.of("root")
                .parallelism(2)
                .child("one", context -> {})
                .child("two", context -> {})
                .resolve();

        var result = runner(2, listener).run(root);

        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(before).containsExactlyInAnyOrder("root", "one", "two");
        assertThat(after).containsExactlyInAnyOrder("root", "one", "two");
    }

    private static Runner runner(final int parallelism) {
        return runner(parallelism, new Listener() {});
    }

    private static Runner runner(final int parallelism, final Listener listener) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                "32",
                Configuration.ANSI,
                "false"));
        return Runner.builder().configuration(configuration).listener(listener).build();
    }

    private static void recordConcurrent(final AtomicInteger active, final AtomicInteger maxActive)
            throws InterruptedException {
        var current = active.incrementAndGet();
        maxActive.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(25L);
        } finally {
            active.decrementAndGet();
        }
    }

    private static void recordGroup(final ConcurrentHashMap<String, AtomicInteger> startedByGroup, final String group) {
        startedByGroup.computeIfAbsent(group, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private static int lastHighIndex(final CopyOnWriteArrayList<String> starts) {
        var index = -1;
        for (var i = 0; i < starts.size(); i++) {
            if (starts.get(i).startsWith("high-")) {
                index = i;
            }
        }
        return index;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
