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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.ExecutionNode;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler delayed continuations")
@SuppressWarnings("removal")
class SchedulerDelayedContinuationTest {

    private static final Duration DELAY = Duration.ofMillis(500);

    @Test
    @Timeout(5)
    @DisplayName("delayed Loop continuation leaves worker available for independent descriptor")
    void delayedLoopContinuationLeavesWorkerAvailableForIndependentDescriptor() throws Exception {
        var firstIterationStarted = new CountDownLatch(1);
        var iterations = new AtomicInteger();
        var loop = Loop.builder("loop")
                .body(Step.of("body", context -> {
                    iterations.incrementAndGet();
                    firstIterationStarted.countDown();
                }))
                .maxIterations(2)
                .delay(DELAY)
                .build();
        var independent = Step.of("independent", context -> {});
        var root = new DescriptorBuilder()
                .discover(
                        Sequence.builder("root").child(loop).child(independent).build());
        root.freeze();
        var loopDescriptor = (MutableDescriptor) root.children().get(0);
        var independentDescriptor = (MutableDescriptor) root.children().get(1);
        var scheduler = new Scheduler(1);
        try {
            var context = context(root, scheduler);
            var loopFuture = scheduler.schedule(loopDescriptor, ExecutionMode.RUN, context);

            assertThat(firstIterationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            var independentFuture = scheduler.schedule(independentDescriptor, ExecutionMode.RUN, context);
            assertThat(independentFuture.get(250, TimeUnit.MILLISECONDS).isPassed())
                    .isTrue();
            assertThat(iterations).hasValue(1);
            assertThat(loopFuture.get(2, TimeUnit.SECONDS).isPassed()).isTrue();
            assertThat(iterations).hasValue(2);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("close finalizes Loop waiting for delayed continuation")
    void closeFinalizesLoopWaitingForDelayedContinuation() throws Exception {
        var firstIterationStarted = new CountDownLatch(1);
        var iterations = new AtomicInteger();
        var loop = Loop.builder("loop")
                .body(Step.of("body", context -> {
                    iterations.incrementAndGet();
                    firstIterationStarted.countDown();
                }))
                .maxIterations(2)
                .delay(DELAY)
                .build();
        var root = new DescriptorBuilder().discover(loop);
        root.freeze();
        var scheduler = new Scheduler(1);
        try {
            var future = scheduler.schedule(root, ExecutionMode.RUN, context(root, scheduler));

            assertThat(firstIterationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            scheduler.close();

            assertThat(future.isDone()).isTrue();
            assertThat(iterations).hasValue(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("duplicate delayed continuation runs observable node once")
    void duplicateDelayedContinuationRunsObservableNodeOnce() throws Exception {
        var continuationCount = new AtomicInteger();
        var scheduler = new Scheduler(1);
        try {
            var node = createNode(scheduler);
            node.continuation = () -> continuationCount.incrementAndGet();

            var startLatch = new CountDownLatch(1);
            var doneLatch = new CountDownLatch(2);
            var threads = new Thread[2];
            for (var i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    scheduler.executeContinuationAfter(node, 50, TimeUnit.MILLISECONDS);
                    doneLatch.countDown();
                });
                threads[i].start();
            }
            startLatch.countDown();

            assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
            for (var t : threads) {
                t.join(5_000);
            }

            // Allow the delay to expire and the continuation to execute.
            Thread.sleep(200);

            assertThat(continuationCount.get())
                    .as("observable continuation effect must occur exactly once")
                    .isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("close racing with delayed continuation finalizes exactly once")
    void closeRacingWithDelayedContinuationFinalizesExactlyOnce() throws Exception {
        var firstIterationStarted = new CountDownLatch(1);
        var continuationCount = new AtomicInteger();
        var terminalCount = new AtomicInteger();
        var scheduler = new Scheduler(1);
        try {
            var root = new DescriptorBuilder()
                    .discover(Loop.builder("loop")
                            .body(Step.of("body", context -> {
                                firstIterationStarted.countDown();
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }))
                            .maxIterations(2)
                            .delay(Duration.ofMillis(100))
                            .build());
            root.freeze();
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {}

                @Override
                public void onExecutionComplete(final Throwable error) {
                    terminalCount.incrementAndGet();
                }
            };
            var context = context(root, scheduler);
            var future = scheduler.schedule(root, ExecutionMode.RUN, context, callback);
            assertThat(firstIterationStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // Race delay expiry with close().
            var closeLatch = new CountDownLatch(1);
            var closeThread = new Thread(() -> {
                closeLatch.countDown();
                scheduler.close();
            });
            closeThread.start();
            closeLatch.await();
            // Small window to let delay executor register the delayed continuation.
            Thread.sleep(50);
            closeThread.join(10_000);

            assertThat(future.isDone()).as("root future must be done").isTrue();
            assertThat(root.isCompleted())
                    .as("root descriptor must be terminal")
                    .isTrue();
            for (var child : root.children()) {
                assertThat(child.isCompleted())
                        .as("every child must be terminal")
                        .isTrue();
            }
            assertThat(terminalCount.get())
                    .as("callback completion count must be one")
                    .isEqualTo(1);
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("executeContinuationAfter rejects null node")
    void executeContinuationAfterRejectsNullNode() {
        var scheduler = new Scheduler(1);
        try {
            assertThatThrownBy(() -> scheduler.executeContinuationAfter(null, 1, TimeUnit.MILLISECONDS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("node is null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("executeContinuationAfter rejects null unit")
    void executeContinuationAfterRejectsNullUnit() {
        var scheduler = new Scheduler(1);
        try {
            var node = createNode(scheduler);
            assertThatThrownBy(() -> scheduler.executeContinuationAfter(node, 1, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("unit is null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("executeContinuationAfter rejects negative delay")
    void executeContinuationAfterRejectsNegativeDelay() {
        var scheduler = new Scheduler(1);
        try {
            var node = createNode(scheduler);
            assertThatThrownBy(() -> scheduler.executeContinuationAfter(node, -1, TimeUnit.MILLISECONDS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("delay must not be negative, was: -1");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("executeContinuationAfter close uses immediate fallback")
    void executeContinuationAfterCloseUsesImmediateFallback() throws Exception {
        var scheduler = new Scheduler(1);
        var node = createNode(scheduler);
        scheduler.close();

        var start = System.nanoTime();
        scheduler.executeContinuationAfter(node, 1, TimeUnit.DAYS);
        var elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsed)
                .as("executeContinuationAfter must return immediately after close without waiting for delay")
                .isLessThan(1000);
    }

    private static ExecutionNode createNode(final Scheduler scheduler) {
        var rootAction = Sequential.builder("root")
                .independent()
                .child(Step.of("child", context -> {}))
                .build();
        var root = new DescriptorBuilder().discover(rootAction);
        root.markScheduled();
        var child = (MutableDescriptor) root.children().get(0);
        var node = new ExecutionNode(child, scheduler);
        child.setExecutionNode(node);
        return node;
    }

    private static ConcreteContext context(final MutableDescriptor root, final Scheduler scheduler) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
    }
}
