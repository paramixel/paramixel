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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Regression coverage for the isolation work-stealing defect.
 *
 * <p>An isolation owner that parks in {@link Scheduler#managedJoin} while holding its lock must
 * not execute unrelated coordinator work from the ready queue. Such work can schedule a
 * same-lock descendant that is deferred behind the owner's lock; the stolen coordinator cannot
 * finish until the lock is released, and the owner's stack cannot unwind to release the lock
 * until the stolen coordinator finishes.
 */
@DisplayName("Scheduler isolation work-stealing regression")
@SuppressWarnings("removal")
class SchedulerIsolationWorkStealingRegressionTest {

    @Test
    @Timeout(30)
    @DisplayName("single-worker owner parked in managedJoin does not steal an unrelated same-lock sibling")
    void singleWorkerOwnerDoesNotStealUnrelatedSameLockSibling() throws Exception {
        var events = Collections.synchronizedList(new ArrayList<String>());
        var iterationStarted = new CountDownLatch(1);
        var iterationsRun = new AtomicInteger();

        var holderAction = Isolated.builder("holder", "L")
                .body(Loop.builder("loop")
                        .body(Step.of("iteration", ctx -> {
                            events.add("iteration");
                            if (iterationsRun.incrementAndGet() == 1) {
                                iterationStarted.countDown();
                            }
                        }))
                        .maxIterations(2)
                        .delay(new Loop.DelayPolicy.Linear(Duration.ofMillis(800)))
                        .build())
                .build();
        var wrapperAction = Sequential.builder("branch")
                .child(Isolated.builder("waiter", "L")
                        .body(Step.of("body", ctx -> events.add("waiter")))
                        .build())
                .build();

        var parent = new DescriptorBuilder()
                .discover(Sequential.builder("parent")
                        .child(holderAction)
                        .child(wrapperAction)
                        .build());
        var holder = (MutableDescriptor) parent.children().get(0);
        var wrapper = (MutableDescriptor) parent.children().get(1);

        var scheduler = new Scheduler(1, 16);
        try {
            var context = newContext(scheduler, parent);

            // Start the holder. Its first loop iteration runs and it then waits (with an
            // inter-iteration delay) while the single worker is parked in managedJoin holding L.
            var holderFuture = scheduler.schedule(holder, ExecutionMode.RUN, context);
            assertThat(iterationStarted.await(10, TimeUnit.SECONDS))
                    .as("holder first iteration must start")
                    .isTrue();

            // Schedule the unrelated same-lock sibling only after the holder is parked, so the
            // only queued work available to the parked owner is the ineligible wrapper.
            var wrapperFuture = scheduler.schedule(wrapper, ExecutionMode.RUN, context);

            // Fixed code: the owner skips the wrapper, its second iteration fires on schedule,
            // the lock is released, and the wrapper then runs. Old code: the owner steals the
            // wrapper while holding L and deadlocks, leaving both futures incomplete, so these
            // bounded joins time out and fail the test.
            awaitDone(holderFuture, "holder");
            awaitDone(wrapperFuture, "wrapper");

            // Both loop iterations run exactly once and the waiter body runs only after the
            // holder has released L (lock exclusivity).
            assertThat(events).containsExactly("iteration", "iteration", "waiter");
            assertThat(iterationsRun.get()).isEqualTo(2);
        } finally {
            scheduler.close();
        }
    }

    private static void awaitDone(final CompletableFuture<?> future, final String label) throws Exception {
        try {
            future.get(25, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(label + " future failed", e.getCause());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("public runner shape with parallel-2 does not deadlock")
    void publicRunnerShapeWithParallelTwoDoesNotDeadlock() throws Exception {
        var iterationsRun = new AtomicInteger();
        var waiterRuns = new AtomicInteger();

        var holderBody = Loop.builder("loop")
                .body(Step.of("iteration", ctx -> iterationsRun.incrementAndGet()))
                .maxIterations(2)
                .delay(new Loop.DelayPolicy.Linear(Duration.ofMillis(100)))
                .build();
        var wrapper = Sequential.builder("wrapper")
                .child(Isolated.builder("waiter", "L")
                        .body(Step.of("body", ctx -> waiterRuns.incrementAndGet()))
                        .build())
                .build();
        var action = Parallel.builder("root")
                .parallelism(2)
                .child(Isolated.builder("holder", "L").body(holderBody).build())
                .child(Sequential.builder("branch")
                        .child(Delay.of("branch-delay", 10))
                        .child(wrapper)
                        .build())
                .build();

        var result = runner(2).run(action);

        assertThat(result.isPassed()).isTrue();
        assertThat(iterationsRun.get()).isEqualTo(2);
        assertThat(waiterRuns.get()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    @DisplayName("different-lock sibling is eligible and still work-stolen by the parked owner")
    void differentLockSiblingIsEligibleAndStillStolen() throws Exception {
        var events = Collections.synchronizedList(new ArrayList<String>());
        var firstIterationStarted = new CountDownLatch(1);
        var iterationsRun = new AtomicInteger();

        var holderAction = Isolated.builder("holder", "L")
                .body(Loop.builder("loop")
                        .body(Step.of("iteration", ctx -> {
                            events.add("iteration");
                            if (iterationsRun.incrementAndGet() == 1) {
                                firstIterationStarted.countDown();
                            }
                        }))
                        .maxIterations(2)
                        .delay(new Loop.DelayPolicy.Linear(Duration.ofMillis(300)))
                        .build())
                .build();
        // Sibling uses a different lock name, so it must NOT be deferred by the holder's lock
        // and its body may even run while the holder holds L.
        var siblingAction = Isolated.builder("other-lock", "M")
                .body(Step.of("body", ctx -> events.add("other-lock-body")))
                .build();

        var parent = new DescriptorBuilder()
                .discover(Sequential.builder("parent")
                        .child(holderAction)
                        .child(siblingAction)
                        .build());
        var holder = (MutableDescriptor) parent.children().get(0);
        var sibling = (MutableDescriptor) parent.children().get(1);

        var scheduler = new Scheduler(1, 16);
        try {
            var context = newContext(scheduler, parent);

            var holderFuture = scheduler.schedule(holder, ExecutionMode.RUN, context);
            assertThat(firstIterationStarted.await(10, TimeUnit.SECONDS))
                    .as("holder first iteration must start")
                    .isTrue();

            // The sibling may be executed by the parked owner (single worker) because it is not
            // gated by the held lock. It must still complete and never be dropped.
            var siblingFuture = scheduler.schedule(sibling, ExecutionMode.RUN, context);

            holderFuture.join();
            siblingFuture.join();

            assertThat(events).contains("iteration", "iteration", "other-lock-body");
            assertThat(iterationsRun.get()).isEqualTo(2);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("waiter bodies never overlap the holder body when queued behind the holder")
    void waiterBodiesNeverOverlapHolderBodyWhenQueuedBehindHolder() throws Exception {
        var concurrentUsers = new AtomicInteger();
        var maxConcurrentUsers = new AtomicInteger();
        var holderIterations = new AtomicInteger();

        var holderBody = Loop.builder("loop")
                .body(Step.of("iteration", ctx -> {
                    var current = concurrentUsers.incrementAndGet();
                    maxConcurrentUsers.accumulateAndGet(current, Math::max);
                    holderIterations.incrementAndGet();
                    concurrentUsers.decrementAndGet();
                }))
                .maxIterations(2)
                .delay(new Loop.DelayPolicy.Linear(Duration.ofMillis(50)))
                .build();
        var action = Parallel.builder("root")
                .parallelism(3)
                .child(Isolated.builder("holder", "db-lock").body(holderBody).build())
                .child(Isolated.builder("waiter-1", "db-lock")
                        .body(Step.of("waiter-1", ctx -> {
                            var current = concurrentUsers.incrementAndGet();
                            maxConcurrentUsers.accumulateAndGet(current, Math::max);
                            concurrentUsers.decrementAndGet();
                        }))
                        .build())
                .child(Isolated.builder("waiter-2", "db-lock")
                        .body(Step.of("waiter-2", ctx -> {
                            var current = concurrentUsers.incrementAndGet();
                            maxConcurrentUsers.accumulateAndGet(current, Math::max);
                            concurrentUsers.decrementAndGet();
                        }))
                        .build())
                .build();

        var result = runner(3).run(action);

        assertThat(result.isPassed()).isTrue();
        assertThat(holderIterations.get()).isEqualTo(2);
        assertThat(maxConcurrentUsers.get())
                .as("lock ownership must never overlap between holder and waiters")
                .isEqualTo(1);
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

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }
}
