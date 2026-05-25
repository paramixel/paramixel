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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.ThrowingConsumer;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.action.ConcreteDescriptor;
import org.paramixel.api.internal.action.DescriptorBuilder;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.spi.action.Mode;

@DisplayName("AsyncScheduler queue bounds")
class AsyncSchedulerQueueBoundsTest {

    @Test
    @DisplayName("ready queue respects capacity")
    void readyQueueRespectsCapacity() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var scheduler = new AsyncScheduler(1, 3);
            try {
                var blocker = new CountDownLatch(1);
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var blockingChild = newChildDescriptor(root, Step.of("blocker", obj -> blocker.await()));
                var blockingFuture = scheduler.schedule(blockingChild, Mode.RUN, context);

                var futures = new ArrayList<CompletableFuture<Descriptor>>();
                for (int i = 0; i < 3; i++) {
                    var child = newChildDescriptor(root, Step.of("fill-" + i, obj -> {}));
                    futures.add(scheduler.schedule(child, Mode.RUN, context));
                }

                assertThat(scheduler.readyQueueSize()).isEqualTo(3);
                assertThat(scheduler.readyQueueSize()).isLessThanOrEqualTo(scheduler.queueCapacity());

                blocker.countDown();
                blockingFuture.join();
                for (var f : futures) {
                    f.join();
                }
            } finally {
                scheduler.close();
            }
        });
    }

    @Test
    @DisplayName("enqueue blocks when ready queue is full")
    void enqueueBlocksWhenReadyQueueIsFull() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var scheduler = new AsyncScheduler(1, 2);
            try {
                var blocker = new CountDownLatch(1);
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var blockingChild = newChildDescriptor(root, Step.of("blocker", obj -> blocker.await()));
                var blockingFuture = scheduler.schedule(blockingChild, Mode.RUN, context);

                var futures = new ArrayList<CompletableFuture<Descriptor>>();
                for (int i = 0; i < 2; i++) {
                    var child = newChildDescriptor(root, Step.of("fill-" + i, obj -> {}));
                    futures.add(scheduler.schedule(child, Mode.RUN, context));
                }

                assertThat(scheduler.readyQueueSize()).isEqualTo(2);

                var overflowFuture = new AtomicReference<CompletableFuture<Descriptor>>();
                var overflowChild = newChildDescriptor(root, Step.of("overflow", obj -> {}));
                var schedulingThread = new Thread(
                        () -> overflowFuture.set(scheduler.schedule(overflowChild, Mode.RUN, context)), "overflow");
                schedulingThread.start();

                Thread.sleep(200);
                assertThat(schedulingThread.isAlive()).isTrue();
                assertThat(scheduler.readyQueueSize()).isEqualTo(2);
                assertThat(scheduler.readyQueueSize()).isEqualTo(scheduler.queueCapacity());

                blocker.countDown();
                schedulingThread.join(Duration.ofSeconds(5).toMillis());
                assertThat(schedulingThread.isAlive()).isFalse();
                blockingFuture.join();
                for (var f : futures) {
                    f.join();
                }
                overflowFuture.get().join();
            } finally {
                scheduler.close();
            }
        });
    }

    @Test
    @DisplayName("executor queue never exceeds parallelism")
    void executorQueueNeverExceedsParallelism() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var parallelism = 2;
            var scheduler = new AsyncScheduler(parallelism, 16);
            try {
                var blocker = new CountDownLatch(1);
                var context = newContext(scheduler);
                var root = contextRoot(context);

                var blockingFutures = new ArrayList<CompletableFuture<Descriptor>>();
                for (int i = 0; i < parallelism; i++) {
                    var child = newChildDescriptor(root, Step.of("blocker-" + i, obj -> blocker.await()));
                    blockingFutures.add(scheduler.schedule(child, Mode.RUN, context));
                }

                var waitingFutures = new ArrayList<CompletableFuture<Descriptor>>();
                for (int i = 0; i < 14; i++) {
                    var child = newChildDescriptor(root, Step.of("wait-" + i, obj -> {}));
                    waitingFutures.add(scheduler.schedule(child, Mode.RUN, context));
                }

                assertThat(scheduler.executorQueueSize()).isLessThanOrEqualTo(parallelism);

                blocker.countDown();
                for (var f : blockingFutures) {
                    f.join();
                }
                for (var f : waitingFutures) {
                    f.join();
                }
                assertThat(scheduler.executorQueueSize()).isEqualTo(0);
            } finally {
                scheduler.close();
            }
        });
    }

    @Test
    @DisplayName("managedJoin drains ready without growing executor queue")
    void managedJoinDrainsReadyWithoutGrowingExecutorQueue() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var scheduler = new AsyncScheduler(1, 4);
            try {
                var joinerStarted = new CountDownLatch(1);
                var completionFuture = new CompletableFuture<Descriptor>();

                var context = newContext(scheduler);
                var root = contextRoot(context);

                ThrowingConsumer<Object> joinerConsumer = obj -> {
                    joinerStarted.countDown();
                    scheduler.managedJoin(completionFuture);
                };
                var joinerChild = newChildDescriptor(root, Step.of("joiner", joinerConsumer));
                var joinerFuture = scheduler.schedule(joinerChild, Mode.RUN, context);

                assertThat(joinerStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();

                var waitingFutures = new ArrayList<CompletableFuture<Descriptor>>();
                for (int i = 0; i < 3; i++) {
                    var child = newChildDescriptor(root, Step.of("wait-" + i, obj -> {}));
                    waitingFutures.add(scheduler.schedule(child, Mode.RUN, context));
                }

                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                    while (waitingFutures.stream().anyMatch(future -> !future.isDone())) {
                        Thread.sleep(10);
                    }
                });

                assertThat(scheduler.executorQueueSize()).isLessThanOrEqualTo(1);

                completionFuture.complete(null);

                joinerFuture.join();
                for (var f : waitingFutures) {
                    f.join();
                }
                assertThat(scheduler.executorQueueSize()).isEqualTo(0);
            } finally {
                scheduler.close();
            }
        });
    }

    @Test
    @DisplayName("managedJoin wakes when completion future finishes without additional scheduler work")
    void managedJoinWakesOnCompletionSignal() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var scheduler = new AsyncScheduler(1, 2);
            try {
                var joinerStarted = new CountDownLatch(1);
                var completionFuture = new CompletableFuture<Descriptor>();
                var context = newContext(scheduler);
                var root = contextRoot(context);

                ThrowingConsumer<Object> joinerConsumer = obj -> {
                    joinerStarted.countDown();
                    scheduler.managedJoin(completionFuture);
                };
                var joinerChild = newChildDescriptor(root, Step.of("joiner", joinerConsumer));
                var joinerFuture = scheduler.schedule(joinerChild, Mode.RUN, context);

                assertThat(joinerStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();

                completionFuture.complete(null);
                assertTimeoutPreemptively(Duration.ofSeconds(1), joinerFuture::join);
            } finally {
                scheduler.close();
            }
        });
    }

    @Test
    @DisplayName("execution callback invoked on successful execution")
    void executionCallbackInvokedOnSuccessfulExecution() {
        var startInvoked = new AtomicBoolean();
        var completeInvoked = new AtomicBoolean();
        var scheduler = new AsyncScheduler(1);
        try {
            var action = Step.of("success", ctx -> {});
            var root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(action);
            var context = newContext(scheduler);

            var callback = new AsyncScheduler.ExecutionCallback() {
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

            var future = scheduler.schedule(root, Mode.RUN, context, callback);
            future.join();
            assertThat(startInvoked).isTrue();
            assertThat(completeInvoked).isTrue();
        } finally {
            scheduler.close();
        }
    }

    private static ConcreteExecutionContext newContext(AsyncScheduler scheduler) {
        var rootAction = Step.of("root", obj -> {});
        var root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(rootAction);
        return new ConcreteExecutionContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static MutableDescriptor contextRoot(ConcreteExecutionContext context) {
        return (MutableDescriptor) context.descriptor();
    }

    private static MutableDescriptor newChildDescriptor(MutableDescriptor parent, Action<?> action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }
}
