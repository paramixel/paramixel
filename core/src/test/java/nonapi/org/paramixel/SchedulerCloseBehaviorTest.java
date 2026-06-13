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

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler close behavior")
class SchedulerCloseBehaviorTest {

    @Test
    @DisplayName("schedule returns failed future when closing is true")
    void scheduleReturnsFailedFutureWhenClosingIsTrue() throws Exception {
        var scheduler = new Scheduler(1);
        try {
            setClosingTrue(scheduler);

            var leaf = Step.of("leaf", context -> {});
            var root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            var future = scheduler.schedule(root, ExecutionMode.RUN, context);
            assertThat(future.isCompletedExceptionally()).isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule returns failed future after close() called")
    void scheduleReturnsFailedFutureAfterClose() {
        var scheduler = new Scheduler(1);
        scheduler.close();

        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        var future = scheduler.schedule(root, ExecutionMode.RUN, context);
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    @DisplayName("schedule does not enqueue work when closing is true")
    void scheduleDoesNotEnqueueWorkWhenClosing() throws Exception {
        var scheduler = new Scheduler(1);
        try {
            setClosingTrue(scheduler);

            var leaf = Step.of("leaf", context -> {});
            var root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            scheduler.schedule(root, ExecutionMode.RUN, context);

            assertThat(scheduler.readyQueueSize()).isZero();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("close shuts down global executor")
    void closeShutsDownGlobalExecutor() throws Exception {
        var scheduler = new Scheduler(1);

        var leaf = Step.of("leaf", context -> sleep(100));
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        scheduler.schedule(root, ExecutionMode.RUN, context);
        scheduler.close();

        var executorField = Scheduler.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        var executor = (ThreadPoolExecutor) executorField.get(scheduler);
        assertThat(executor.isShutdown()).isTrue();
    }

    // ================================================================
    // Issue #3: leafPermit invariant tests
    // ================================================================

    @Test
    @DisplayName("leafPermits invariant holds after close() with all leaves completed beforehand")
    void leafPermitInvariantAfterAllLeavesCompleted() throws Exception {
        var parallelism = 2;
        var scheduler = new Scheduler(parallelism);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            for (var i = 0; i < 4; i++) {
                var leaf = newChildDescriptor(root, Step.of("leaf-" + i, innerContext -> {}));
                scheduler.schedule(leaf, ExecutionMode.RUN, context).join();
            }

            scheduler.close();

            var leafPermits = getLeafPermits(scheduler);
            assertThat(leafPermits.availablePermits())
                    .as("available permits must not exceed parallelism after close() with completed leaves")
                    .isLessThanOrEqualTo(parallelism);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("close() unblocks leaf permit waiters within graceful timeout")
    void closeUnblocksLeafWaitersWithinGracefulPeriod() throws Exception {
        var parallelism = 2;
        var scheduler = new Scheduler(parallelism);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            // Hold one permit so the semaphore has room for a waiter
            var holdStarted = new CountDownLatch(1);
            var holdPermit = new CountDownLatch(1);
            var holder = newChildDescriptor(root, Step.of("holder", innerContext -> {
                holdStarted.countDown();
                try {
                    holdPermit.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            scheduler.schedule(holder, ExecutionMode.RUN, context);
            holdStarted.await(); // holder acquired one permit

            // Schedule a leaf that will wait for a permit (all parallelism permits used)
            var blockedLeaf = newChildDescriptor(root, Step.of("blocked", innerContext -> {}));
            scheduler.schedule(blockedLeaf, ExecutionMode.RUN, context);
            Thread.sleep(500); // give time for the waiter to queue

            // Close on a separate thread so we can verify it completes within graceful period
            var closeThread = new Thread(() -> scheduler.close());
            closeThread.start();

            // Release the holder's permit, allowing the waiter to proceed
            holdPermit.countDown();

            // close() should complete within graceful shutdown (5-second timeout)
            closeThread.join(3_000);

            assertThat(closeThread.isAlive())
                    .as("close() should complete within graceful period")
                    .isFalse();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("leafPermits invariant holds after close() with concurrent leaf execution")
    void leafPermitInvariantAfterCloseWithConcurrentLeaves() throws Exception {
        var parallelism = 3;
        for (var trial = 0; trial < 20; trial++) {
            var scheduler = new Scheduler(parallelism);
            try {
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var started = new CountDownLatch(parallelism);
                for (var i = 0; i < parallelism; i++) {
                    var leaf = newChildDescriptor(root, Step.of("leaf-" + i, innerContext -> {
                        started.countDown();
                        sleep(300);
                    }));
                    scheduler.schedule(leaf, ExecutionMode.RUN, context);
                }
                started.await(); // all leaves running, all permits held
                scheduler.close();

                var leafPermits = getLeafPermits(scheduler);
                assertThat(leafPermits.availablePermits())
                        .as("trial " + trial + ": available permits must not exceed parallelism")
                        .isLessThanOrEqualTo(parallelism);
            } finally {
                scheduler.close();
            }
        }
    }

    @Test
    @DisplayName("leafPermits invariant holds after close() for various parallelism levels")
    void leafPermitInvariantAfterCloseWithVariousParallelism() throws Exception {
        for (var parallelism : new int[] {1, 2, 4, 8}) {
            var scheduler = new Scheduler(parallelism);
            try {
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var leafCount = parallelism * 3;
                var futures = new ArrayList<CompletableFuture<org.paramixel.api.Descriptor>>(leafCount);
                for (var i = 0; i < leafCount; i++) {
                    var leaf = newChildDescriptor(root, Step.of("leaf-" + i, innerContext -> {}));
                    futures.add(scheduler.schedule(leaf, ExecutionMode.RUN, context));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .join();

                scheduler.close();

                var leafPermits = getLeafPermits(scheduler);
                assertThat(leafPermits.availablePermits())
                        .as("parallelism=" + parallelism + ": available permits must not exceed parallelism")
                        .isLessThanOrEqualTo(parallelism);
            } finally {
                scheduler.close();
            }
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("leafPermits invariant holds after close() with concurrent leaves and external waiter thread")
    void leafPermitInvariantAfterCloseWithConcurrentLeavesAndWaiter() throws Exception {
        var parallelism = 2;
        var scheduler = new Scheduler(parallelism);
        try {
            var context = newContext(scheduler);
            var root = contextRoot(context);

            // Two leaves that hold permits for a known duration
            var leafRunning = new CountDownLatch(parallelism);
            for (var i = 0; i < parallelism; i++) {
                var leaf = newChildDescriptor(root, Step.of("leaf-" + i, innerContext -> {
                    leafRunning.countDown();
                    sleep(2000);
                }));
                scheduler.schedule(leaf, ExecutionMode.RUN, context);
            }
            leafRunning.await(); // both leaves running, permits held, available = 0

            // Get leafPermits semaphore via reflection and create a direct waiter.
            // This simulates what managedJoin does: a non-executor thread acquires
            // a leaf permit to execute a task.
            var leafPermits = getLeafPermits(scheduler);

            var waiterDone = new CountDownLatch(1);
            var waiterThread = new Thread(() -> {
                try {
                    leafPermits.acquire();
                    Thread.sleep(500);
                    leafPermits.release();
                } catch (Throwable t) {
                    // thread may be interrupted during shutdown
                } finally {
                    waiterDone.countDown();
                }
            });
            waiterThread.start();
            Thread.sleep(500); // give waiter time to block on acquire()

            // Close triggers:
            //   1. release(parallelism) unblocks the waiter
            //   2. waiter acquires, does work, releases
            //   3. With deferred drain, drainPermits runs AFTER all workers (incl. waiter) finish
            scheduler.close();
            waiterDone.await(5, TimeUnit.SECONDS);

            assertThat(leafPermits.availablePermits())
                    .as("available permits must not exceed parallelism even with external waiter")
                    .isLessThanOrEqualTo(parallelism);
        } finally {
            scheduler.close();
        }
    }

    // ================================================================
    // Issue #1: close() throws IllegalStateException when interrupted,
    // shadowing results when called from a finally block.
    // ================================================================

    @Nested
    @DisplayName("close() when the calling thread is interrupted")
    class CloseWhenInterrupted {

        @Test
        @DisplayName("returns normally and restores interrupt flag")
        void returnsNormallyWhenInterrupted() throws Exception {
            var scheduler = new Scheduler(2);
            try {
                var context = newContext(scheduler);
                var root = contextRoot(context);

                // Schedule a leaf that takes time so executor has work to shut down.
                var leaf = newChildDescriptor(root, Step.of("leaf", innerContext -> sleep(500)));
                scheduler.schedule(leaf, ExecutionMode.RUN, context);

                // Interrupt the calling thread before close().
                // This simulates what happens when a user action throws
                // InterruptedException — the interrupt flag is restored,
                // then the finally block calls close(). close() must return
                // normally so the original result is not discarded.
                Thread.currentThread().interrupt();

                scheduler.close();

                assertThat(Thread.currentThread().isInterrupted())
                        .as("interrupt flag should be preserved after close()")
                        .isTrue();
            } finally {
                // Clear interrupt flag to avoid contaminating other tests.
                Thread.interrupted();
                scheduler.close();
            }
        }

        @Test
        @DisplayName("preserves Result when called from runInternal finally block")
        void preservesResultWhenCalledFromFinallyBlock() throws Exception {
            // Simulate the finally-block pattern in ConcreteRunner.runInternal().
            // When the try-block produces a result but the thread is interrupted,
            // close() must return normally so the result is not replaced.
            Object simulatedResult = null;
            var closeCompleted = false;

            var scheduler = new Scheduler(2);
            try {
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var leaf = newChildDescriptor(root, Step.of("leaf", innerContext -> sleep(200)));
                scheduler.schedule(leaf, ExecutionMode.RUN, context);

                Thread.currentThread().interrupt();

                try {
                    // Simulate the try block completing with a Result.
                    simulatedResult = new Object();
                } catch (Throwable t) {
                    /* not reached in this test */
                } finally {
                    closeQuietly(scheduler);
                    closeCompleted = true;
                }
            } finally {
                Thread.interrupted();
                scheduler.close();
            }

            assertThat(simulatedResult)
                    .as("result from try block should be preserved")
                    .isNotNull();
            assertThat(closeCompleted)
                    .as("finally block should complete normally")
                    .isTrue();
        }

        @Test
        @DisplayName("leafPermits invariant holds after interrupted close()")
        void leafPermitInvariantHoldsWhenInterrupted() throws Exception {
            var parallelism = 2;
            var scheduler = new Scheduler(parallelism);
            try {
                var context = newContext(scheduler);
                var root = contextRoot(context);

                // Hold a permit so the semaphore has waiters.
                var holdStarted = new CountDownLatch(1);
                var holdPermit = new CountDownLatch(1);
                var holder = newChildDescriptor(root, Step.of("holder", innerContext -> {
                    holdStarted.countDown();
                    try {
                        holdPermit.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
                scheduler.schedule(holder, ExecutionMode.RUN, context);
                holdStarted.await();

                // Schedule a waiter that will need a permit.
                var blocked = newChildDescriptor(root, Step.of("blocked", innerContext -> {}));
                scheduler.schedule(blocked, ExecutionMode.RUN, context);

                // Interrupt before close().
                Thread.currentThread().interrupt();

                scheduler.close();
                holdPermit.countDown();

                // Wait for holder to finish, drain remaining permits, then close.
                Thread.interrupted();
                scheduler.close();

                var leafPermits = getLeafPermits(scheduler);
                assertThat(leafPermits.availablePermits())
                        .as("available permits must not exceed parallelism after interrupted close")
                        .isLessThanOrEqualTo(parallelism);
            } finally {
                Thread.interrupted();
                scheduler.close();
            }
        }
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private static ConcreteContext newContext(final Scheduler scheduler) {
        var rootAction = Step.of("root", context -> {});
        var root = new DescriptorBuilder().discover(rootAction);
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static MutableDescriptor contextRoot(final ConcreteContext context) {
        return (MutableDescriptor) context.descriptor();
    }

    private static MutableDescriptor newChildDescriptor(final MutableDescriptor parent, final Action action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }

    private static Semaphore getLeafPermits(final Scheduler scheduler) throws Exception {
        var leafPermitsField = Scheduler.class.getDeclaredField("leafPermits");
        leafPermitsField.setAccessible(true);
        return (Semaphore) leafPermitsField.get(scheduler);
    }

    private static void setClosingTrue(Scheduler scheduler) throws Exception {
        var closingField = Scheduler.class.getDeclaredField("closing");
        closingField.setAccessible(true);
        closingField.setBoolean(scheduler, true);
    }

    private static void closeQuietly(final Scheduler scheduler) {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
