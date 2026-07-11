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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.ExecutionNode;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler managed join")
@SuppressWarnings("removal")
class SchedulerManagedJoinTest {

    @Test
    @Timeout(10)
    @DisplayName("managedJoin steals queued prioritized task and returns watched result")
    void managedJoinStealsQueuedPrioritizedTaskAndReturnsWatchedResult() throws Exception {
        var scheduler = new Scheduler(1, 8);
        try {
            var watchedFuture = new CompletableFuture<String>();
            var taskRan = new AtomicInteger();

            // Schedule a prioritized task that completes the watched future.
            var context = newContext(scheduler);
            var root = contextRoot(context);
            var task = newChildDescriptor(root, Step.of("task", innerContext -> {
                taskRan.incrementAndGet();
                watchedFuture.complete("result");
            }));
            // Schedule it for async execution so it enters the executor queue.
            scheduler.schedule(task, ExecutionMode.RUN, context);

            // managedJoin should steal and run the queued task, then return the result.
            var joinThread = new Thread(
                    () -> {
                        var result = scheduler.managedJoin(watchedFuture);
                        assertThat(result).isEqualTo("result");
                    },
                    "managedJoin-test");
            joinThread.start();

            joinThread.join(5_000);
            assertThat(joinThread.isAlive()).as("managedJoin should return").isFalse();
            assertThat(taskRan.get()).as("queued action must run exactly once").isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("managedJoin steals queued continuation task and returns watched result")
    void managedJoinStealsQueuedContinuationTaskAndReturnsWatchedResult() throws Exception {
        var scheduler = new Scheduler(1, 8);
        try {
            var watchedFuture = new CompletableFuture<String>();
            var continuationRan = new AtomicInteger();

            // Create an ExecutionNode whose continuation completes the watched future.
            var rootAction = Step.of("root", context -> {});
            var root = new DescriptorBuilder().discover(rootAction);
            var node = new ExecutionNode(root, scheduler);
            root.setExecutionNode(node);
            node.continuation = () -> {
                continuationRan.incrementAndGet();
                watchedFuture.complete("result");
            };

            // Schedule the continuation via the executor queue.
            scheduler.executeContinuation(node);

            var joinThread = new Thread(
                    () -> {
                        var result = scheduler.managedJoin(watchedFuture);
                        assertThat(result).isEqualTo("result");
                    },
                    "managedJoin-test");
            joinThread.start();

            joinThread.join(5_000);
            assertThat(joinThread.isAlive()).as("managedJoin should return").isFalse();
            assertThat(continuationRan.get())
                    .as("queued continuation must run exactly once")
                    .isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("managedJoin from external thread does not retain worker state")
    void managedJoinFromExternalThreadDoesNotRetainWorkerState() throws Exception {
        var scheduler = new Scheduler(1, 8);
        try {
            var watchedFuture = new CompletableFuture<String>();
            var taskRan = new AtomicInteger();
            var executed = new AtomicBoolean();

            // Schedule a task that completes the watched future.
            var context = newContext(scheduler);
            var root = contextRoot(context);
            var task = newChildDescriptor(root, Step.of("task", innerContext -> {
                taskRan.incrementAndGet();
                watchedFuture.complete("done");
            }));
            scheduler.schedule(task, ExecutionMode.RUN, context);

            // Execute managedJoin from a non-executor thread.
            var externalThread = new Thread(
                    () -> {
                        scheduler.managedJoin(watchedFuture);
                    },
                    "external-thread");
            externalThread.start();
            externalThread.join(5_000);

            // After managedJoin returns, schedule a follow-up action on an executor thread.
            // This verifies that the scheduler still works correctly — the external thread's
            // work-stealing did not corrupt scheduler state.
            var followUp = newChildDescriptor(root, Step.of("follow-up", innerContext -> {
                executed.set(true);
            }));
            scheduler.schedule(followUp, ExecutionMode.RUN, context).join();

            assertThat(taskRan.get()).isEqualTo(1);
            assertThat(executed.get())
                    .as("follow-up action should execute normally on a scheduler worker")
                    .isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("managedJoin consumes preexisting interrupt and waits for future completion")
    void managedJoinConsumesPreexistingInterruptAndWaitsForFutureCompletion() throws Exception {
        var scheduler = new Scheduler(1, 8);
        try {
            var watchedFuture = new CompletableFuture<String>();
            var resultRef = new AtomicReference<String>();
            var wasInterrupted = new AtomicBoolean();
            var completed = new CountDownLatch(1);

            // Interrupt the joining thread before entry.
            var joinThread = new Thread(
                    () -> {
                        Thread.currentThread().interrupt();
                        wasInterrupted.set(Thread.currentThread().isInterrupted());
                        var result = scheduler.managedJoin(watchedFuture);
                        resultRef.set(result);
                        completed.countDown();
                    },
                    "join-thread");
            joinThread.start();

            // managedJoin should consume the preexisting interrupt, park, and wait.
            // After a short delay, complete the future.
            Thread.sleep(200);
            watchedFuture.complete("expected");

            assertThat(completed.await(5, TimeUnit.SECONDS))
                    .as("managedJoin should complete after future completion")
                    .isTrue();
            assertThat(resultRef.get()).isEqualTo("expected");
            assertThat(wasInterrupted.get())
                    .as("interrupt flag should have been set before managedJoin")
                    .isTrue();
            // managedJoin calls Thread.interrupted() which clears the flag, then parks.
            joinThread.join(2_000);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("managedJoin consumes parked thread interrupt and waits for future completion")
    void managedJoinConsumesParkedThreadInterruptAndWaitsForFutureCompletion() throws Exception {
        var scheduler = new Scheduler(1, 8);
        try {
            var watchedFuture = new CompletableFuture<String>();
            var resultRef = new AtomicReference<String>();
            var enteredPark = new CountDownLatch(1);
            var completed = new CountDownLatch(1);

            Thread[] joinThreadHolder = new Thread[1];
            joinThreadHolder[0] = new Thread(
                    () -> {
                        // Use a separate watcher thread to signal when managedJoin has started.
                        var watcher = new Thread(() -> {
                            try {
                                enteredPark.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            // Allow managedJoin to enter park, then interrupt.
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            joinThreadHolder[0].interrupt();
                            // Complete the future after a short delay so managedJoin can
                            // consume the interrupt and continue waiting.
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            watchedFuture.complete("expected");
                        });
                        watcher.setDaemon(true);
                        watcher.start();

                        // Signal that managedJoin has been called.
                        enteredPark.countDown();
                        var result = scheduler.managedJoin(watchedFuture);
                        resultRef.set(result);
                        completed.countDown();
                    },
                    "join-thread");
            joinThreadHolder[0].start();

            assertThat(completed.await(10, TimeUnit.SECONDS))
                    .as("managedJoin should complete after future completion despite interrupt")
                    .isTrue();
            assertThat(resultRef.get()).isEqualTo("expected");
            joinThreadHolder[0].join(2_000);
        } finally {
            scheduler.close();
        }
    }

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
        return ConcreteContext.require(context).descriptor();
    }

    private static MutableDescriptor newChildDescriptor(
            final MutableDescriptor parent, final org.paramixel.api.action.Action action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }
}
