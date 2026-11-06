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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Pins the scheduler's ordering contract: under contention earlier siblings are served first,
 * the per-node parallelism value is a share rather than a preference, the global parallelism
 * value is the ceiling that both are expressed against, and a slot is never left idle while
 * ready work exists.
 *
 * <p>The contract under test:
 *
 * <ul>
 *   <li>the global {@code paramixel.parallelism} value is an upper bound on concurrently
 *       executing leaf actions;
 *   <li>a {@code Parallel} node's own {@code parallelism} value caps how many of its direct
 *       children may be in flight, and is computed by ordinary Java code when the action is
 *       built;
 *   <li>when ready work exceeds the global bound, the earlier sibling's ready descendants are
 *       dequeued before the later sibling's, regardless of submission order;
 *   <li>work is conserved: an earlier sibling whose ready work (or whose own parallelism value)
 *       does not need the whole bound leaves the remaining slots to later siblings, and a task
 *       that is already running is never preempted.
 * </ul>
 *
 * <p>These tests are deterministic by construction: the only blocking is on latches the test
 * controls, so no assertion depends on wall-clock scheduling races.
 */
@DisplayName("Scheduler priority contract")
class SchedulerPriorityContractTest {

    @Test
    @Timeout(30)
    @DisplayName("when the bound is contended, an earlier sibling's descendants win over a later sibling")
    void earlierSubtreeWinsWhenGlobalBoundIsContended() throws Exception {
        var starts = new CopyOnWriteArrayList<String>();
        var ballastRunning = new CountDownLatch(2);
        var releaseBallast = new CountDownLatch(1);

        var a0 = Sequential.builder("a0")
                .child(Step.of("a0-step", context -> starts.add("a0")))
                .build();
        var a1 = Sequential.builder("a1")
                .child(Step.of("a1-step", context -> starts.add("a1")))
                .build();
        var earlier = Parallel.builder("earlier")
                .parallelism(Integer.MAX_VALUE)
                .child(a0)
                .child(a1);
        var b0 = Sequential.builder("b0")
                .child(Step.of("b0-step", context -> starts.add("b0")))
                .build();
        var b1 = Sequential.builder("b1")
                .child(Step.of("b1-step", context -> starts.add("b1")))
                .build();
        var later = Parallel.builder("later")
                .parallelism(Integer.MAX_VALUE)
                .child(b0)
                .child(b1);
        var ballast0 = Sequential.builder("ballast-0")
                .child(Step.of("ballast-0-step", context -> {
                    ballastRunning.countDown();
                    releaseBallast.await();
                }))
                .build();
        var ballast1 = Sequential.builder("ballast-1")
                .child(Step.of("ballast-1-step", context -> {
                    ballastRunning.countDown();
                    releaseBallast.await();
                }))
                .build();

        var root = new DescriptorBuilder()
                .discover(Parallel.builder("root")
                        .parallelism(Integer.MAX_VALUE)
                        .child(earlier.build())
                        .child(later.build())
                        .child(ballast0)
                        .child(ballast1)
                        .build());
        var earlierDescriptor = (MutableDescriptor) root.children().get(0);
        var laterDescriptor = (MutableDescriptor) root.children().get(1);
        var ballast0Descriptor = (MutableDescriptor) root.children().get(2);
        var ballast1Descriptor = (MutableDescriptor) root.children().get(3);

        var scheduler = new Scheduler(2, 16);
        try {
            var context = newContext(scheduler, root);

            // Occupy both workers so everything submitted below is queued before anything runs.
            var ballast0Future = scheduler.schedule(ballast0Descriptor, ExecutionMode.RUN, context);
            var ballast1Future = scheduler.schedule(ballast1Descriptor, ExecutionMode.RUN, context);
            assertThat(ballastRunning.await(10, TimeUnit.SECONDS)).isTrue();

            // Submit in reverse key order: the later sibling's descendants first, then the
            // earlier sibling's. Submission order must not decide execution order.
            var laterFuture = scheduler.schedule(laterDescriptor, ExecutionMode.RUN, context);
            var earlierFuture = scheduler.schedule(earlierDescriptor, ExecutionMode.RUN, context);

            releaseBallast.countDown();
            earlierFuture.get(10, TimeUnit.SECONDS);
            laterFuture.get(10, TimeUnit.SECONDS);
            ballast0Future.get(10, TimeUnit.SECONDS);
            ballast1Future.get(10, TimeUnit.SECONDS);
        } finally {
            scheduler.close();
        }

        assertThat(starts).hasSize(4);
        assertThat(starts.subList(0, 2))
                .as("the earlier sibling's descendants must be dequeued before the later sibling's")
                .containsExactlyInAnyOrder("a0", "a1");
        assertThat(starts.subList(2, 4)).containsExactlyInAnyOrder("b0", "b1");
    }

    @Test
    @Timeout(30)
    @DisplayName("parallelism is a share: a capped sibling leaves slots for later siblings")
    void cappedSiblingLeavesSlotsForLaterSiblings() throws Exception {
        var highStarted = new CountDownLatch(1);
        var releaseHigh = new CountDownLatch(1);
        var lowStarted = new CountDownLatch(1);

        var high = Parallel.builder("high")
                .parallelism(1)
                .child(Step.of("high-0", context -> {
                    highStarted.countDown();
                    releaseHigh.await();
                }))
                .child(Step.of("high-1", context -> {}));

        var low = Parallel.builder("low")
                .parallelism(Integer.MAX_VALUE)
                .child(Step.of("low-0", context -> lowStarted.countDown()));

        var root = Parallel.builder("root")
                .parallelism(Integer.MAX_VALUE)
                .child(high.build())
                .child(low.build())
                .build();

        var resultFuture = CompletableFuture.supplyAsync(() -> runner(2).run(root));

        assertThat(highStarted.await(10, TimeUnit.SECONDS)).isTrue();
        // high is capped at one in-flight child, so the second global slot must be offered to
        // low even though high still has ready work.
        assertThat(lowStarted.await(10, TimeUnit.SECONDS))
                .as("later sibling must use the slot the earlier sibling's cap leaves idle")
                .isTrue();

        releaseHigh.countDown();
        var result = resultFuture.get(10, TimeUnit.SECONDS);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("a single worker executes depth-first across sibling subtrees")
    void singleWorkerExecutesDepthFirst() {
        var order = new CopyOnWriteArrayList<String>();

        var leftFirst = Parallel.builder("left-first")
                .child(Step.of("a", context -> order.add("a")))
                .child(Step.of("b", context -> order.add("b")));
        var leftSecond = Parallel.builder("left-second").child(Step.of("c", context -> order.add("c")));
        var left = Parallel.builder("left").child(leftFirst.build()).child(leftSecond.build());
        var right = Parallel.builder("right").child(Step.of("d", context -> order.add("d")));
        var root = Parallel.builder("root")
                .child(left.build())
                .child(right.build())
                .build();

        var result = runner(1).run(root);

        assertThat(result.isPassed()).isTrue();
        assertThat(order)
                .as("every descendant of the earlier sibling runs before the later sibling starts")
                .containsExactlyElementsOf(List.of("a", "b", "c", "d"));
    }

    @Test
    @Timeout(30)
    @DisplayName("dequeue order follows the structural key, not the submission order")
    void dequeueOrderFollowsStructuralKeyNotSubmissionOrder() throws Exception {
        var order = new CopyOnWriteArrayList<String>();
        var ballastStarted = new CountDownLatch(1);
        var releaseBallast = new CountDownLatch(1);

        var first = Sequential.builder("first")
                .child(Step.of("first-step", context -> order.add("first")))
                .build();
        var second = Sequential.builder("second")
                .child(Step.of("second-step", context -> order.add("second")))
                .build();
        var ballast = Sequential.builder("ballast")
                .child(Step.of("ballast-step", context -> {
                    ballastStarted.countDown();
                    releaseBallast.await();
                    order.add("ballast");
                }))
                .build();

        var root = new DescriptorBuilder()
                .discover(Parallel.builder("root")
                        .parallelism(3)
                        .child(first)
                        .child(second)
                        .child(ballast)
                        .build());
        var firstDescriptor = (MutableDescriptor) root.children().get(0);
        var secondDescriptor = (MutableDescriptor) root.children().get(1);
        var ballastDescriptor = (MutableDescriptor) root.children().get(2);

        var scheduler = new Scheduler(1, 16);
        try {
            var context = newContext(scheduler, root);

            // Occupy the single worker so the next two submissions queue up before either runs.
            var ballastFuture = scheduler.schedule(ballastDescriptor, ExecutionMode.RUN, context);
            assertThat(ballastStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // Submit in reverse key order: "second" (key [2]) before "first" (key [1]).
            var secondFuture = scheduler.schedule(secondDescriptor, ExecutionMode.RUN, context);
            var firstFuture = scheduler.schedule(firstDescriptor, ExecutionMode.RUN, context);

            releaseBallast.countDown();
            firstFuture.get(10, TimeUnit.SECONDS);
            secondFuture.get(10, TimeUnit.SECONDS);
            ballastFuture.get(10, TimeUnit.SECONDS);
        } finally {
            scheduler.close();
        }

        assertThat(order)
                .as("the lower structural key must be dequeued first even when submitted last")
                .containsExactly("ballast", "first", "second");
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static Runner runner(final int parallelism) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                "32",
                Configuration.ANSI,
                "false"));
        return Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build();
    }
}
