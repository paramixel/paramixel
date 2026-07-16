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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.support.Throwables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.FailException;

@DisplayName("Scheduler bounded semantics")
class SchedulerQueueBoundsTest {

    @Test
    @Timeout(5)
    @DisplayName("scheduler key ordering prefers earlier subtree even when submitted later")
    void schedulerKeyOrderingPrefersEarlierSubtreeEvenWhenSubmittedLater() throws InterruptedException {
        var scheduler = new Scheduler(1, 8);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var leftBranch = newChildDescriptor(root, Step.of("left-branch", innerContext -> {}));
            var rightBranch = newChildDescriptor(root, Step.of("right-branch", innerContext -> {}));
            var blockerBranch = newChildDescriptor(root, Step.of("blocker-branch", innerContext -> {}));

            var executionOrder = Collections.synchronizedList(new ArrayList<String>());
            var blockerStarted = new CountDownLatch(1);
            var blockerRelease = new CountDownLatch(1);

            var leftLeaf =
                    newChildDescriptor(leftBranch, Step.of("left-leaf", innerContext -> executionOrder.add("left")));
            var rightLeaf =
                    newChildDescriptor(rightBranch, Step.of("right-leaf", innerContext -> executionOrder.add("right")));
            var blockerLeaf = newChildDescriptor(blockerBranch, Step.of("blocker-leaf", innerContext -> {
                blockerStarted.countDown();
                blockerRelease.await();
            }));

            var blockerFuture = scheduler.schedule(blockerLeaf, ExecutionMode.RUN, context);
            assertThat(blockerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            var rightFuture = scheduler.schedule(rightLeaf, ExecutionMode.RUN, context);
            var leftFuture = scheduler.schedule(leftLeaf, ExecutionMode.RUN, context);

            blockerRelease.countDown();

            blockerFuture.join();
            leftFuture.join();
            rightFuture.join();

            assertThat(executionOrder).containsExactly("left", "right");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("scheduler key ordering works for deeply nested sibling subtrees")
    void schedulerKeyOrderingWorksForDeeplyNestedSiblingSubtrees() throws InterruptedException {
        var scheduler = new Scheduler(1, 8);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var leftBranch = newChildDescriptor(root, Step.of("left-branch", innerContext -> {}));
            var rightBranch = newChildDescriptor(root, Step.of("right-branch", innerContext -> {}));
            var blockerBranch = newChildDescriptor(root, Step.of("blocker-branch", innerContext -> {}));

            var leftNested = newChildDescriptor(leftBranch, Step.of("left-nested", innerContext -> {}));
            var rightNested = newChildDescriptor(rightBranch, Step.of("right-nested", innerContext -> {}));
            var blockerNested = newChildDescriptor(blockerBranch, Step.of("blocker-nested", innerContext -> {}));

            var executionOrder = Collections.synchronizedList(new ArrayList<String>());
            var blockerStarted = new CountDownLatch(1);
            var blockerRelease = new CountDownLatch(1);

            var leftLeaf =
                    newChildDescriptor(leftNested, Step.of("left-leaf", innerContext -> executionOrder.add("left")));
            var rightLeaf =
                    newChildDescriptor(rightNested, Step.of("right-leaf", innerContext -> executionOrder.add("right")));
            var blockerLeaf = newChildDescriptor(blockerNested, Step.of("blocker-leaf", innerContext -> {
                blockerStarted.countDown();
                blockerRelease.await();
            }));

            var blockerFuture = scheduler.schedule(blockerLeaf, ExecutionMode.RUN, context);
            assertThat(blockerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            var rightFuture = scheduler.schedule(rightLeaf, ExecutionMode.RUN, context);
            var leftFuture = scheduler.schedule(leftLeaf, ExecutionMode.RUN, context);

            blockerRelease.countDown();

            blockerFuture.join();
            leftFuture.join();
            rightFuture.join();

            assertThat(executionOrder).containsExactly("left", "right");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("queue metrics report bounded scheduler values")
    void queueMetricsReportBoundedValues() {
        var scheduler = new Scheduler(2, 3);
        try {
            assertThat(scheduler.queueCapacity()).isEqualTo(3);
            assertThat(scheduler.readyQueueSize()).isZero();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("queue capacity rejects scheduling beyond configured bound")
    void queueCapacityRejectsSchedulingBeyondConfiguredBound() throws InterruptedException {
        var scheduler = new Scheduler(1, 1);
        try {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var first = newChildDescriptor(root, Step.of("first", innerContext -> {
                started.countDown();
                release.await();
            }));
            var second = newChildDescriptor(root, Step.of("second", innerContext -> {}));
            var third = newChildDescriptor(root, Step.of("third", innerContext -> {}));

            var firstFuture = scheduler.schedule(first, ExecutionMode.RUN, context);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            var secondFuture = scheduler.schedule(second, ExecutionMode.RUN, context);

            var thirdFuture = scheduler.schedule(third, ExecutionMode.RUN, context);
            assertThatThrownBy(thirdFuture::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(RejectedExecutionException.class);
            assertThat(third.isFailed()).isTrue();

            release.countDown();
            firstFuture.join();
            secondFuture.join();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("running reflects concurrently executing leaf actions")
    void runningReflectsExecutingLeafActions() throws InterruptedException {
        var scheduler = new Scheduler(2);
        try {
            var started = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var left = newChildDescriptor(root, Step.of("left", innerContext -> {
                started.countDown();
                release.await();
            }));
            var right = newChildDescriptor(root, Step.of("right", innerContext -> {
                started.countDown();
                release.await();
            }));

            var leftFuture = scheduler.schedule(left, ExecutionMode.RUN, context);
            var rightFuture = scheduler.schedule(right, ExecutionMode.RUN, context);

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.running()).isEqualTo(2);

            release.countDown();
            leftFuture.join();
            rightFuture.join();

            assertThat(scheduler.running()).isZero();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("managedJoin delegates to CompletableFuture join")
    void managedJoinDelegatesToFutureJoin() {
        var scheduler = new Scheduler(1);
        try {
            assertThat(scheduler.managedJoin(CompletableFuture.completedFuture("done")))
                    .isEqualTo("done");

            var failure = new IllegalStateException("boom");
            var failed = new CompletableFuture<String>();
            failed.completeExceptionally(failure);

            assertThatThrownBy(() -> scheduler.managedJoin(failed))
                    .isInstanceOf(CompletionException.class)
                    .hasCause(failure);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("parallel coordinator does not consume leaf permit")
    void parallelCoordinatorDoesNotConsumeLeafPermit() {
        var scheduler = new Scheduler(1);
        try {
            var leafRuns = new AtomicInteger();
            var action = Parallel.builder("parent")
                    .parallelism(1)
                    .child(Step.of("leaf", context -> leafRuns.incrementAndGet()))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            var context = newContext(scheduler, root);

            scheduler.schedule(root, ExecutionMode.RUN, context).join();

            assertThat(leafRuns.get()).isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("execution callback invoked on successful execution")
    void executionCallbackInvokedOnSuccessfulExecution() {
        var startInvoked = new AtomicBoolean();
        var completeInvoked = new AtomicBoolean();
        var scheduler = new Scheduler(1);
        try {
            var action = Step.of("success", context -> {});
            var root = new DescriptorBuilder().discover(action);
            var context = newContext(scheduler);

            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {
                    startInvoked.set(true);
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeInvoked.set(true);
                    assertThat(error).isNull();
                }
            };

            scheduler.schedule(root, ExecutionMode.RUN, context, callback).join();
            assertThat(startInvoked).isTrue();
            assertThat(completeInvoked).isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("execution callback invoked on failed execution")
    void executionCallbackInvokedOnFailedExecution() {
        var startInvoked = new AtomicBoolean();
        var completeInvoked = new AtomicBoolean();
        var completeError = new AtomicReference<Throwable>();
        var scheduler = new Scheduler(1);
        try {
            var expected = new IllegalStateException("boom");
            var action = Step.of("failure", context -> {
                throw expected;
            });
            var root = new DescriptorBuilder().discover(action);
            var context = newContext(scheduler);

            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {
                    startInvoked.set(true);
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeInvoked.set(true);
                    completeError.set(error);
                }
            };

            var future = scheduler.schedule(root, ExecutionMode.RUN, context, callback);
            assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCause(expected);
            assertThat(startInvoked).isTrue();
            assertThat(completeInvoked).isTrue();
            assertThat(completeError.get()).isSameAs(expected);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("execution callback receives status-derived failure")
    void executionCallbackReceivesStatusDerivedFailure() {
        var completeError = new AtomicReference<Throwable>();
        var scheduler = new Scheduler(1);
        try {
            var action = Step.of("status-failure", context -> FailException.fail("status-based failure"));
            var root = new DescriptorBuilder().discover(action);
            var context = newContext(scheduler, root);
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {}

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeError.set(error);
                }
            };

            var future = scheduler.schedule(root, ExecutionMode.RUN, context, callback);
            assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(FailException.class);
            assertThat(completeError.get()).isInstanceOf(FailException.class).hasMessage("status-based failure");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("close completes within timeout when executors have slow tasks")
    void closeCompletesWithinTimeoutWithSlowTasks() {
        var scheduler = new Scheduler(1, 16);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);
            var child = newChildDescriptor(root, Step.of("slow", innerContext -> {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            scheduler.schedule(child, ExecutionMode.RUN, context);

            var closeStart = System.nanoTime();
            scheduler.close();
            var closeElapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - closeStart);

            assertThat(closeElapsed).isLessThanOrEqualTo(15);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("close after concurrent schedule does not leak threads")
    void closeAfterConcurrentScheduleDoesNotLeakThreads() {
        var scheduler = new Scheduler(2, 16);
        var latch = new CountDownLatch(1);

        var threads = new Thread[4];
        for (var i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                var context = newContext(scheduler);
                var root = contextRoot(context);
                var child = newChildDescriptor(root, Step.of("task", innerContext -> {}));
                try {
                    scheduler.schedule(child, ExecutionMode.RUN, context);
                } catch (Exception ignored) {
                }
            });
            threads[i].start();
        }

        latch.countDown();
        for (var t : threads) {
            try {
                t.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        scheduler.close();
        assertThat(scheduler.readyQueueSize()).isZero();
    }

    @Test
    @DisplayName("unwrap preserves exception context for non-special causes")
    void unwrapPreservesExceptionContextForNonSpecialCauses() {
        var scheduler = new Scheduler(1);
        try {
            var action = Step.of("wrapping-action", context -> {
                throw new RuntimeException(new IOException("disk full"));
            });
            var root = new DescriptorBuilder().discover(action);
            var context = newContext(scheduler);

            var future = scheduler.schedule(root, ExecutionMode.RUN, context);
            assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .satisfies(ex -> assertThat(ex.getCause())
                            .isInstanceOf(IOException.class)
                            .hasMessage("disk full"));
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("unwrap recursively unwraps nested CompletionException")
    void unwrapRecursivelyUnwrapsNestedCompletionException() {
        var scheduler = new Scheduler(1);
        try {
            var innermost = new IOException("disk full");
            var middle = new CompletionException(innermost);
            var outer = new CompletionException(middle);

            var result = Throwables.unwrap(outer);

            assertThat(result).isSameAs(innermost);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("global queue permit semaphore never exceeds queueCapacity")
    void globalQueuePermitSemaphoreNeverExceedsQueueCapacity() throws Exception {
        var queueCapacity = 3;
        var scheduler = new Scheduler(1, queueCapacity);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var queuePermitsField = Scheduler.class.getDeclaredField("queuePermits");
            queuePermitsField.setAccessible(true);
            var queuePermits = (Semaphore) queuePermitsField.get(scheduler);

            for (var i = 0; i < 10; i++) {
                var task = newChildDescriptor(root, Step.of("task-" + i, innerContext -> {}));
                scheduler.schedule(task, ExecutionMode.RUN, context).join();
                assertThat(queuePermits.availablePermits()).isLessThanOrEqualTo(queueCapacity);
            }
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("parallel should not reject children when queue capacity is smaller than effective parallelism")
    void parallelMustNotRejectChildrenWhenQueueExhaustedAndThreadsBusy() throws Exception {
        var scheduler = new Scheduler(2, 1);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var blockerStarted = new CountDownLatch(1);
            var blockerRelease = new CountDownLatch(1);
            var blocker = newChildDescriptor(root, Step.of("blocker", ctx -> {
                blockerStarted.countDown();
                try {
                    blockerRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            scheduler.schedule(blocker, ExecutionMode.RUN, context);
            assertThat(blockerStarted.await(5, TimeUnit.SECONDS)).isTrue();

            var executed = new AtomicInteger();
            var parallelAction = Parallel.builder("parallel-under-pressure")
                    .child(Step.of("child-1", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-2", ctx -> executed.incrementAndGet()))
                    .child(Step.of("child-3", ctx -> executed.incrementAndGet()))
                    .build();
            var parallelRoot = new DescriptorBuilder().discover(parallelAction);
            var parallelContext = newContext(scheduler, parallelRoot);
            var parallelFuture = scheduler.schedule(parallelRoot, ExecutionMode.RUN, parallelContext);

            blockerRelease.countDown();

            assertThatCode(parallelFuture::join)
                    .as("parallel must complete successfully, not with FailException from rejected child")
                    .doesNotThrowAnyException();

            var children = parallelRoot.children();
            assertThat(children).hasSize(3);

            assertThat(children.get(0).isPassed()).as("child-1 must be PASSED").isTrue();
            assertThat(children.get(1).isPassed())
                    .as("child-2 must be PASSED — not silently rejected by queue capacity")
                    .isTrue();
            assertThat(children.get(2).isPassed()).as("child-3 must be PASSED").isTrue();

            assertThat(executed.get())
                    .as("all 3 children must execute, not just 2")
                    .isEqualTo(3);

            assertThat(parallelRoot.isPassed())
                    .as("parallel aggregate must be PASSED when all children pass")
                    .isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("schedule while closing returns IllegalStateException not RejectedExecutionException")
    void scheduleWhileClosingReturnsIllegalStateNotRejectedExecution() throws Exception {
        var scheduler = new Scheduler(1, 16);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            var taskStarted = new CountDownLatch(1);
            var taskProceed = new CountDownLatch(1);
            var blockingTask = newChildDescriptor(root, Step.of("blocking", innerContext -> {
                taskStarted.countDown();
                taskProceed.await(10, TimeUnit.SECONDS);
            }));
            scheduler.schedule(blockingTask, ExecutionMode.RUN, context);
            taskStarted.await();

            var closeThread = new Thread(scheduler::close);
            closeThread.start();

            Thread.sleep(100);

            var scheduleResult = new AtomicReference<Throwable>();
            var scheduleDone = new CountDownLatch(1);
            var scheduleThread = new Thread(() -> {
                var task = newChildDescriptor(root, Step.of("task", innerContext -> {}));
                try {
                    scheduler.schedule(task, ExecutionMode.RUN, context).join();
                } catch (Throwable t) {
                    scheduleResult.set(t);
                } finally {
                    scheduleDone.countDown();
                }
            });
            scheduleThread.start();

            taskProceed.countDown();
            closeThread.join(10_000);
            scheduleDone.await(10, TimeUnit.SECONDS);

            assertThat(scheduleResult.get())
                    .isNotNull()
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Scheduler is closing");
        } finally {
            scheduler.close();
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler) {
        var rootAction = Step.of("root", context -> {});
        var root = new DescriptorBuilder().discover(rootAction);
        return newContext(scheduler, root);
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

    private static MutableDescriptor newChildDescriptor(final MutableDescriptor parent, final Action action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }
}
