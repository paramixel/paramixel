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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;

/**
 * Regression coverage for shared ready-queue admission contention.
 *
 * <p>Each {@code Parallel} caps its own admission window against {@code queueCapacity}, but all
 * branches consume the same global semaphore. Two individually valid windows can exhaust global
 * capacity and the framework's own child admissions used to be marked failed with
 * {@code RejectedExecutionException}. Framework child admission must instead wait for capacity.
 */
@DisplayName("Scheduler admission contention regression")
@SuppressWarnings("removal")
class SchedulerAdmissionContentionRegressionTest {

    @Test
    @Timeout(30)
    @DisplayName("parallel child admission defers under capacity pressure instead of failing children")
    void parallelChildAdmissionDefersUnderCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var executed = new AtomicInteger();
            var action = Parallel.builder("parallel-under-pressure")
                    .child(Step.of("child-1", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-2", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-3", ctx -> executed.incrementAndGet()))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(executed.get())
                    .as("every child must execute exactly once")
                    .isEqualTo(3);
            for (var child : root.children()) {
                assertThat(child.isPassed())
                        .as(child.action().displayName() + " must be PASSED")
                        .isTrue();
            }
            assertThat(root.isPassed())
                    .as("parallel must not report capacity failures")
                    .isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("nested parallel child admission defers under capacity pressure")
    void nestedParallelChildAdmissionDefersUnderCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var executed = new AtomicInteger();
            var action = Parallel.builder("outer")
                    .parallelism(2)
                    .child(Parallel.builder("inner-a")
                            .child(Step.of("a-1", ctx -> executed.incrementAndGet()))
                            .child(Step.of("a-2", ctx -> executed.incrementAndGet()))
                            .build())
                    .child(Parallel.builder("inner-b")
                            .child(Step.of("b-1", ctx -> executed.incrementAndGet()))
                            .child(Step.of("b-2", ctx -> executed.incrementAndGet()))
                            .build())
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(executed.get())
                    .as("every child must execute exactly once")
                    .isEqualTo(4);
            assertThat(root.isPassed()).isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("sequential child admission defers under capacity pressure")
    void sequentialChildAdmissionDefersUnderCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var executed = new AtomicInteger();
            var action = Sequential.builder("sequential-under-pressure")
                    .child(Step.of("child-1", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-2", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-3", ctx -> executed.incrementAndGet()))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(executed.get())
                    .as("every child must execute exactly once")
                    .isEqualTo(3);
            assertThat(root.isPassed()).isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("lifecycle body admission defers under capacity pressure")
    void lifecycleBodyAdmissionDefersUnderCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var order = new java.util.ArrayList<String>();
            var body = org.paramixel.api.action.Sequential.builder("body-seq")
                    .child(Step.of("body-1", ctx -> order.add("body-1")))
                    .child(Step.of("body-2", ctx -> order.add("body-2")))
                    .child(Step.of("body-3", ctx -> order.add("body-3")))
                    .build();
            var action = Scope.builder("scope-under-pressure")
                    .before(Step.of("before", ctx -> order.add("before")))
                    .body(body)
                    .after(Step.of("after", ctx -> order.add("after")))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(order).containsExactly("before", "body-1", "body-2", "body-3", "after");
            assertThat(root.isPassed()).isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("loop iterations defer under capacity pressure and all execute")
    void loopIterationsDeferUnderCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var executed = new AtomicInteger();
            var action = Loop.builder("loop-under-pressure")
                    .body(Step.of("iteration", ctx -> executed.incrementAndGet()))
                    .maxIterations(4)
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(executed.get())
                    .as("every loop iteration must execute exactly once")
                    .isEqualTo(4);
            assertThat(root.isPassed()).isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("genuine user failure is not mistaken for capacity pressure")
    void genuineUserFailureIsNotMistakenForCapacityPressure() throws Exception {
        runUnderPressure(scheduler -> {
            var passExecuted = new AtomicInteger();
            var action = Parallel.builder("parallel-with-user-failure")
                    .child(Step.of("passing", ctx -> passExecuted.incrementAndGet()))
                    .child(Step.of("failing", ctx -> FailException.fail("expected user failure")))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(root.isFailed())
                    .as("root must reflect the genuine user failure")
                    .isTrue();
            assertThat(passExecuted.get())
                    .as("passing sibling must still execute exactly once")
                    .isEqualTo(1);
            assertThat(((MutableDescriptor) root.children().get(1)).isFailed()).isTrue();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("timeout expires while its child awaits admission and body never starts")
    void timeoutExpiresWhileChildAwaitsAdmission() throws Exception {
        runUnderPressure(
                scheduler -> {
                    var bodyRuns = new AtomicInteger();
                    var action = org.paramixel.api.action.Timeout.builder("timeout-while-waiting")
                            .body(Step.of("body", ctx -> bodyRuns.incrementAndGet()))
                            .timeout(Duration.ofMillis(150))
                            .build();
                    var root = new DescriptorBuilder().discover(action);
                    scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

                    assertThat(root.isFailed())
                            .as("timeout root must be terminal (failed) after the deadline")
                            .isTrue();
                    assertThat(bodyRuns.get())
                            .as("body must never start after the timeout fired")
                            .isZero();
                },
                // Keep capacity blocked long enough for the short timeout to fire.
                1_500L);
    }

    @Test
    @Timeout(30)
    @DisplayName("timeout body admitted after capacity frees still completes within its deadline")
    void timeoutBodyAdmittedAfterCapacityFreesCompletesWithinDeadline() throws Exception {
        runUnderPressure(scheduler -> {
            var bodyRuns = new AtomicInteger();
            var action = org.paramixel.api.action.Timeout.builder("timeout-deferred-admission")
                    .body(Step.of("body", ctx -> bodyRuns.incrementAndGet()))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            scheduler.executeDescriptor(root, newContext(scheduler, root), ExecutionMode.RUN);

            assertThat(root.isPassed())
                    .as("timeout child must run after capacity frees")
                    .isTrue();
            assertThat(bodyRuns.get()).isEqualTo(1);
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("isolated holder can admit its next body child while an unrelated sibling occupies capacity")
    void isolatedHolderAdmitsNextBodyChildWhileUnrelatedSiblingOccupiesCapacity() throws Exception {
        var scheduler = new Scheduler(1, 1);
        try {
            var wrapperAction = Sequential.builder("wrapper")
                    .child(Isolated.builder("waiter", "L")
                            .body(Step.of("waiter-body", ctx -> {}))
                            .build())
                    .build();

            // The holder body is a parallel whose second child must be admitted only after the
            // first body child completes. The first body child blocks until the test has queued
            // the unrelated wrapper, so the second admission deterministically observes the
            // single ready slot occupied by the wrapper.
            var holderBodyStep = new AtomicInteger();
            var firstBodyStarted = new CountDownLatch(1);
            var releaseFirstBody = new CountDownLatch(1);
            var holderAction = Isolated.builder("holder", "L")
                    .body(Parallel.builder("holder-body")
                            .child(Step.of("body-1", ctx -> {
                                holderBodyStep.incrementAndGet();
                                firstBodyStarted.countDown();
                                try {
                                    releaseFirstBody.await(10, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }))
                            .child(Step.of("body-2", ctx -> holderBodyStep.incrementAndGet()))
                            .build())
                    .build();
            var parent = new DescriptorBuilder()
                    .discover(Sequential.builder("parent")
                            .child(holderAction)
                            .child(wrapperAction)
                            .build());
            var holder = (MutableDescriptor) parent.children().get(0);
            var wrapper = (MutableDescriptor) parent.children().get(1);

            var holderFuture = scheduler.schedule(holder, ExecutionMode.RUN, newContext(scheduler, parent));
            assertThat(firstBodyStarted.await(10, TimeUnit.SECONDS))
                    .as("holder first body child must start")
                    .isTrue();

            // The unrelated wrapper now occupies the only ready slot while the holder (the only
            // worker) waits for capacity to admit its second body child.
            var wrapperFuture = scheduler.schedule(wrapper, ExecutionMode.RUN, newContext(scheduler, parent));
            releaseFirstBody.countDown();

            try {
                holderFuture.get(15, TimeUnit.SECONDS);
                wrapperFuture.get(15, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new AssertionError("scheduled future failed", e.getCause());
            }

            assertThat(holderBodyStep.get())
                    .as("both holder body children must execute exactly once")
                    .isEqualTo(2);
            assertThat(holder.isPassed()).isTrue();
            assertThat(wrapper.isPassed()).isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("review nested-parallel shape passes under capacity two with parallelism four")
    void reviewNestedParallelShapePassesUnderCapacityTwoParallelismFour() {
        for (var run = 0; run < 3; run++) {
            var executed = new ConcurrentHashMap<String, AtomicInteger>();
            var root = Parallel.builder("root").parallelism(4);
            for (var branch = 0; branch < 4; branch++) {
                var branchAction = Parallel.builder("branch-" + branch).parallelism(2);
                for (var step = 0; step < 10; step++) {
                    var name = "branch-" + branch + "-step-" + step;
                    branchAction.child(Step.of(name, ctx -> {
                        executed.computeIfAbsent(name, ignored -> new AtomicInteger())
                                .incrementAndGet();
                        sleepQuietly(1);
                    }));
                }
                root.child(branchAction.build());
            }
            var action = root.build();
            var result = runner(4, 2).run(action);

            assertThat(result.isPassed())
                    .as("run " + run + " must pass without capacity rejections")
                    .isTrue();
            for (var branch = 0; branch < 4; branch++) {
                for (var step = 0; step < 10; step++) {
                    var name = "branch-" + branch + "-step-" + step;
                    assertThat(executed.getOrDefault(name, new AtomicInteger()).get())
                            .as("run " + run + " " + name + " must execute exactly once")
                            .isEqualTo(1);
                }
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("capacity one and two with one and two workers admit all nested children")
    void capacityAndWorkerMatrixAdmitsAllNestedChildren() throws Exception {
        for (var capacity : new int[] {1, 2}) {
            for (var workers : new int[] {1, 2}) {
                var executed = new AtomicInteger();
                var wrapper = Parallel.builder("branch-inner");
                for (var step = 0; step < 6; step++) {
                    wrapper.child(Step.of("step-" + step, ctx -> executed.incrementAndGet()));
                }
                var action = Scope.builder("scope")
                        .before(Step.of("before", ctx -> {}))
                        .body(Sequential.builder("seq")
                                .child(Parallel.builder("outer")
                                        .child(wrapper.build())
                                        .child(Isolated.builder("iso", "L")
                                                .body(Step.of("iso-body", ctx -> executed.incrementAndGet()))
                                                .build())
                                        .build())
                                .build())
                        .after(Step.of("after", ctx -> {}))
                        .build();
                var result = runner(workers, capacity).run(action);

                assertThat(result.isPassed())
                        .as("capacity " + capacity + " workers " + workers + " must pass")
                        .isTrue();
                assertThat(executed.get())
                        .as("capacity " + capacity + " workers " + workers + " children must all run")
                        .isEqualTo(7);
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("aborted and passing leaves under capacity pressure preserve statuses")
    void abortedAndPassingLeavesUnderCapacityPressurePreserveStatuses() throws Exception {
        var action = Parallel.builder("root")
                .parallelism(4)
                .child(Parallel.builder("branch-a")
                        .child(Step.of("pass-a", ctx -> {}))
                        .child(Step.of("abort-a", ctx -> AbortedException.abort("expected abort")))
                        .build())
                .child(Parallel.builder("branch-b")
                        .child(Step.of("pass-b", ctx -> {}))
                        .child(Step.of("pass-b2", ctx -> {}))
                        .build())
                .build();
        var result = runner(4, 2).run(action);

        assertThat(result.descriptor().orElseThrow().isAborted())
                .as("aborted leaf must drive the aggregate to ABORTED")
                .isTrue();
        assertThat(result.descriptor().orElseThrow().isFailed())
                .as("abort is not a capacity failure")
                .isFalse();
    }

    /**
     * Runs a coordination tree while the single scheduler worker is blocked and the single queue
     * permit is occupied, so every framework child admission deterministically observes an
     * exhausted ready queue until the blocker is released from a helper thread.
     */
    private static void runUnderPressure(final TreeVerifier verifier) throws Exception {
        runUnderPressure(verifier, 500L);
    }

    private static void runUnderPressure(final TreeVerifier verifier, final long releaseDelayMillis) throws Exception {
        var scheduler = new Scheduler(1, 1);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);
            var blockerStarted = new CountDownLatch(1);
            var blockerRelease = new CountDownLatch(1);
            var blocker = newChildDescriptor(root, Step.of("blocker", ctx -> {
                blockerStarted.countDown();
                blockerRelease.await();
            }));
            var fillerRan = new AtomicInteger();
            var filler = newChildDescriptor(root, Step.of("filler", ctx -> fillerRan.incrementAndGet()));

            var blockerFuture = scheduler.schedule(blocker, ExecutionMode.RUN, context);
            assertThat(blockerStarted.await(10, TimeUnit.SECONDS))
                    .as("blocker must start")
                    .isTrue();
            var fillerFuture = scheduler.schedule(filler, ExecutionMode.RUN, context);

            // Release the blocker from a helper thread so the calling thread can park inside
            // executeDescriptor while capacity is still exhausted.
            var releaseThread = new Thread(() -> {
                sleepQuietly(releaseDelayMillis);
                blockerRelease.countDown();
            });
            releaseThread.start();
            try {
                verifier.verify(scheduler);
            } finally {
                blockerRelease.countDown();
                releaseThread.join(10_000);
                blockerFuture.join();
                fillerFuture.join();
            }
        } finally {
            scheduler.close();
        }
    }

    private interface TreeVerifier {
        void verify(Scheduler scheduler) throws Exception;
    }

    private static Runner runner(final int parallelism, final int queueCapacity) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                String.valueOf(queueCapacity),
                Configuration.ANSI,
                "false"));
        return Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build();
    }

    private static void sleepQuietly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler) {
        return newContext(scheduler, contextRootOf());
    }

    private static MutableDescriptor contextRootOf() {
        var action = Step.of("root", ctx -> {});
        var root = new DescriptorBuilder().discover(action);
        return root;
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static MutableDescriptor contextRoot(final ConcreteContext context) {
        return ConcreteContext.require(context).descriptor();
    }

    private static MutableDescriptor newChildDescriptor(
            final MutableDescriptor parent, final org.paramixel.api.action.Action action) {
        var child = new nonapi.org.paramixel.action.ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }
}
