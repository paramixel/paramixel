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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.listener.Listeners;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;

@DisplayName("Scheduler algorithm")
class SchedulerAlgorithmTest {

    @Test
    @DisplayName("Parallel action enforces own limit when lower than global")
    void parallelEnforcesOwnLimit() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.parallel("root", "p2x2");
        for (var i = 0; i < 10; i++) {
            root.child(Step.step("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(8).run(root.build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("parallel action limits its own concurrent children")
    void parallelActionLimitsOwnConcurrentChildren() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.parallel("root", "p2x0");
        for (var i = 0; i < 6; i++) {
            root.child(Step.step("child-" + i, context -> recordConcurrent(active, maxActive)));
        }

        var result = runner(4).run(root.build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("nested parallel actions complete without worker starvation")
    void nestedParallelActionsCompleteWithoutWorkerStarvation() {
        var executed = new AtomicInteger();
        var left = Parallel.parallel("left", "p2x0")
                .child(Step.step("left-1", context -> executed.incrementAndGet()))
                .child(Step.step("left-2", context -> executed.incrementAndGet()));
        var right = Parallel.parallel("right", "p2x1")
                .child(Step.step("right-1", context -> executed.incrementAndGet()))
                .child(Step.step("right-2", context -> executed.incrementAndGet()));
        var root = Parallel.parallel("root", "p2x2")
                .child(left.build())
                .child(right.build())
                .build();

        var result =
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runner(2).run(root));

        assertThat(result.status().isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("nested parallel with deep hierarchy completes without deadlock")
    void nestedParallelWithDeepHierarchyCompletesWithoutDeadlock() {
        var executed = new AtomicInteger();

        var leaf1 = Parallel.parallel("leaf1", "t9")
                .child(Step.step("l1-1", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.step("l1-2", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }));

        var leaf2 = Parallel.parallel("leaf2", "t10")
                .child(Step.step("l2-1", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.step("l2-2", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }));

        var mid1 = Parallel.parallel("mid1", "p2x2").child(leaf1.build()).child(leaf2.build());

        var leaf3 = Parallel.parallel("leaf3", "t0")
                .child(Step.step("l3-1", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.step("l3-2", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }));

        var leaf4 = Parallel.parallel("leaf4", "t1")
                .child(Step.step("l4-1", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }))
                .child(Step.step("l4-2", context -> {
                    executed.incrementAndGet();
                    sleep(10);
                }));

        var mid2 = Parallel.parallel("mid2", "p2x2").child(leaf3.build()).child(leaf4.build());

        var root = Parallel.parallel("root", "p2x0")
                .child(mid1.build())
                .child(mid2.build())
                .build();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> runner(2).run(root));

        assertThat(result.status().isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(8);
    }

    @Test
    @DisplayName("document order priority runs earlier parallel work before lower priority children")
    void documentOrderPriorityRunsEarlierParallelWorkFirst() {
        var starts = new CopyOnWriteArrayList<String>();
        var high = Parallel.parallel("high", "punbounded");
        for (var i = 0; i < 4; i++) {
            var index = i;
            high.child(Step.step("high-" + index, context -> {
                starts.add("high-" + index);
                Thread.sleep(10L);
            }));
        }
        var low = Parallel.parallel("low", "p1x1").child(Step.step("low-0", context -> starts.add("low-0")));
        var root = Parallel.parallel("root", "p1x2")
                .child(high.build())
                .child(low.build())
                .build();

        var result = runner(2).run(root);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(starts).contains("low-0");
        assertThat(starts.indexOf("low-0")).isGreaterThan(lastHighIndex(starts));
    }

    @Test
    @DisplayName("lower priority work uses slots left by higher priority limits")
    void lowerPriorityWorkUsesSlotsLeftByHigherPriorityLimits() {
        var startedByGroup = new ConcurrentHashMap<String, AtomicInteger>();
        var high = Parallel.parallel("high", "p1x0")
                .child(Step.step("high-0", context -> recordGroup(startedByGroup, "high")))
                .child(Step.step("high-1", context -> recordGroup(startedByGroup, "high")));
        var low = Parallel.parallel("low", "p2x1")
                .child(Step.step("low-0", context -> recordGroup(startedByGroup, "low")))
                .child(Step.step("low-1", context -> recordGroup(startedByGroup, "low")));
        var root = Parallel.parallel("root", "p2x2")
                .child(high.build())
                .child(low.build())
                .build();

        var result = runner(3).run(root);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(startedByGroup.get("high").get()).isEqualTo(2);
        assertThat(startedByGroup.get("low").get()).isEqualTo(2);
    }

    @Test
    @DisplayName("parallel siblings continue after a child failure")
    void parallelSiblingsContinueAfterChildFailure() {
        var successfulSiblings = new AtomicInteger();
        var root = Parallel.parallel("root", "p3x0")
                .child(Step.step("fail", context -> {
                    throw FailException.fail("expected");
                }))
                .child(Step.step("success-1", context -> successfulSiblings.incrementAndGet()))
                .child(Step.step("success-2", context -> successfulSiblings.incrementAndGet()))
                .build();

        var result = runner(3).run(root);

        assertThat(successfulSiblings.get()).isEqualTo(2);
        assertThat(result.descriptor().orElseThrow().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("aborted child produces aborted parallel aggregate status")
    void abortedChildProducesAbortedParallelAggregateStatus() {
        var root = Parallel.parallel("root", "p2x1")
                .child(Step.step("abort", context -> {
                    throw AbortException.abort("expected");
                }))
                .child(Step.step("success", context -> {}))
                .build();

        var result = runner(2).run(root);

        assertThat(result.descriptor().orElseThrow().status().isAborted()).isTrue();
    }

    @Test
    @DisplayName("root local parallelism still limits root direct-child concurrency")
    void rootLocalParallelismStillLimitsRootDirectChildConcurrency() {
        var activeRootChildren = new AtomicInteger();
        var maxActiveRootChildren = new AtomicInteger();
        var listener = new RootDirectChildConcurrencyListener(activeRootChildren, maxActiveRootChildren);

        var root = Parallel.parallel("root", "p1x1");
        for (var i = 0; i < 6; i++) {
            var branch = Parallel.parallel("branch-" + i, "p3x2")
                    .child(Step.step("leaf-a", context -> sleep(20L)))
                    .child(Step.step("leaf-b", context -> sleep(20L)));
            root.child(branch.build());
        }

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> runner(4, listener).run(root.build()));

        assertThat(result.status().isPassed()).isTrue();
        assertThat(maxActiveRootChildren.get()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("failed root child releases top-level permit and does not hang siblings")
    void failedRootChildReleasesTopLevelPermitAndDoesNotHangSiblings() {
        var successfulSiblings = new AtomicInteger();
        var root = Parallel.parallel("root", "p10x0")
                .child(Step.step("fail", context -> {
                    throw FailException.fail("expected");
                }))
                .child(Step.step("success-1", context -> successfulSiblings.incrementAndGet()))
                .child(Step.step("success-2", context -> successfulSiblings.incrementAndGet()))
                .build();

        var result =
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> runner(1).run(root));

        assertThat(successfulSiblings.get()).isEqualTo(2);
        assertThat(result.descriptor().orElseThrow().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("lifecycle callbacks fire once per descriptor")
    void lifecycleCallbacksFireOncePerDescriptor() {
        var before = Collections.synchronizedList(new ArrayList<String>());
        var after = Collections.synchronizedList(new ArrayList<String>());
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                before.add(descriptor.action().displayName());
            }

            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                after.add(descriptor.action().displayName());
            }
        };
        var root = Parallel.parallel("root", "p2x1")
                .child(Step.step("one", context -> {}))
                .child(Step.step("two", context -> {}))
                .build();

        var result = runner(2, listener).run(root);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(before).containsExactlyInAnyOrder("root", "one", "two");
        assertThat(after).containsExactlyInAnyOrder("root", "one", "two");
    }

    @Test
    @DisplayName("thread name matches formatIdPath during listener callbacks")
    void threadNameMatchesFormatIdPathDuringExecution() {
        var threadNames = Collections.synchronizedList(new ArrayList<String>());
        var expectedPaths = Collections.synchronizedList(new ArrayList<String>());
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                threadNames.add(Thread.currentThread().getName());
                expectedPaths.add(Listeners.formatIdPath(descriptor));
            }

            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                threadNames.add(Thread.currentThread().getName());
                expectedPaths.add(Listeners.formatIdPath(descriptor));
            }
        };

        runner(1, listener).run(Step.step("test", ctx -> {}));

        assertThat(threadNames).isNotEmpty();
        assertThat(threadNames).containsExactlyElementsOf(expectedPaths);
    }

    @Test
    @DisplayName("thread name is restored after execution")
    void threadNameRestoredAfterExecution() {
        var originalName = Thread.currentThread().getName();

        runner(1).run(Step.step("test", ctx -> {}));

        assertThat(Thread.currentThread().getName()).isEqualTo(originalName);
    }

    @Test
    @DisplayName("thread name matches formatIdPath during nested execution")
    void threadNameMatchesFormatIdPathInNestedExecution() {
        var threadNames = Collections.synchronizedList(new ArrayList<String>());
        var expectedPaths = Collections.synchronizedList(new ArrayList<String>());
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                threadNames.add(Thread.currentThread().getName());
                expectedPaths.add(Listeners.formatIdPath(descriptor));
            }

            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                threadNames.add(Thread.currentThread().getName());
                expectedPaths.add(Listeners.formatIdPath(descriptor));
            }
        };
        var action = Sequential.sequential("parent")
                .child(Step.step("child", ctx -> {}))
                .build();

        runner(1, listener).run(action);

        assertThat(threadNames).isNotEmpty();
        assertThat(threadNames).containsExactlyElementsOf(expectedPaths);
    }

    private static Runner runner(final int parallelism) {
        return runner(parallelism, new Listener() {});
    }

    private static Runner runner(final int parallelism, final Listener listener) {
        var configuration = TestPools.configuration(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                "paramixel.scheduler.queue.capacity",
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

    private static final class RootDirectChildConcurrencyListener implements Listener {

        private final AtomicInteger activeRootChildren;
        private final AtomicInteger maxActiveRootChildren;

        private RootDirectChildConcurrencyListener(
                final AtomicInteger activeRootChildren, final AtomicInteger maxActiveRootChildren) {
            this.activeRootChildren = activeRootChildren;
            this.maxActiveRootChildren = maxActiveRootChildren;
        }

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {
            if (!isRootDirectChild(descriptor)) {
                return;
            }
            var current = activeRootChildren.incrementAndGet();
            maxActiveRootChildren.accumulateAndGet(current, Math::max);
        }

        @Override
        public void onAfterExecution(final Descriptor descriptor) {
            if (isRootDirectChild(descriptor)) {
                activeRootChildren.decrementAndGet();
            }
        }

        private static boolean isRootDirectChild(final Descriptor descriptor) {
            if (descriptor.parent().isEmpty()) {
                return false;
            }
            return descriptor.parent().orElseThrow().parent().isEmpty();
        }
    }

    private static final class NamedDirectChildConcurrencyListener implements Listener {

        private final String parentName;
        private final AtomicInteger activeChildren;
        private final AtomicInteger maxActiveChildren;

        private NamedDirectChildConcurrencyListener(
                final String parentName, final AtomicInteger activeChildren, final AtomicInteger maxActiveChildren) {
            this.parentName = parentName;
            this.activeChildren = activeChildren;
            this.maxActiveChildren = maxActiveChildren;
        }

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {
            if (!isDirectChildOfNamedParent(descriptor, parentName)) {
                return;
            }
            var current = activeChildren.incrementAndGet();
            maxActiveChildren.accumulateAndGet(current, Math::max);
        }

        @Override
        public void onAfterExecution(final Descriptor descriptor) {
            if (isDirectChildOfNamedParent(descriptor, parentName)) {
                activeChildren.decrementAndGet();
            }
        }

        private static boolean isDirectChildOfNamedParent(
                final Descriptor descriptor, final String expectedParentName) {
            if (descriptor.parent().isEmpty()) {
                return false;
            }
            return descriptor.parent().orElseThrow().action().displayName().equals(expectedParentName);
        }
    }
}
