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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Isolated action")
@SuppressWarnings("removal")
class SchedulerIsolatedTest {

    @Test
    @DisplayName("body executes and passes")
    void bodyExecutesAndPasses() {
        var executed = new AtomicInteger();
        var action = Isolated.builder("test", "lock")
                .body(Step.of("ok", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(1).run(action);

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("body failure propagates")
    void bodyFailurePropagates() {
        var action = Isolated.builder("fail-test", "lock")
                .body(Step.of("fail", ctx -> FailException.fail("expected")))
                .build();

        var result = runner(1).run(action);

        assertThat(result.isFailed()).isTrue();
    }

    @Test
    @DisplayName("body abort propagates")
    void bodyAbortPropagates() {
        var action = Isolated.builder("abort-test", "lock")
                .body(Step.of("abort", ctx -> AbortedException.abort("expected")))
                .build();

        var result = runner(1).run(action);

        assertThat(result.descriptor().orElseThrow().isAborted()).isTrue();
    }

    @Test
    @DisplayName("body skip propagates")
    void bodySkipPropagates() {
        var action = Isolated.builder("skip-test", "lock")
                .body(Step.of("skip", ctx -> SkipException.skip("expected")))
                .build();

        var result = runner(1).run(action);

        assertThat(result.isSkipped()).isTrue();
    }

    @Test
    @DisplayName("same lock name serializes")
    void sameLockNameSerializes() {
        var concurrent = new AtomicInteger();
        var maxConcurrent = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(4)
                .child(Isolated.builder("first", "db-lock")
                        .body(Step.of("step-1", ctx -> {
                            var current = concurrent.incrementAndGet();
                            maxConcurrent.accumulateAndGet(current, Math::max);
                            sleep(20);
                            concurrent.decrementAndGet();
                        }))
                        .build())
                .child(Isolated.builder("second", "db-lock")
                        .body(Step.of("step-2", ctx -> {
                            var current = concurrent.incrementAndGet();
                            maxConcurrent.accumulateAndGet(current, Math::max);
                            sleep(20);
                            concurrent.decrementAndGet();
                        }))
                        .build())
                .build();

        var result = runner(4).run(root);

        assertThat(result.isPassed()).isTrue();
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("different lock names run concurrently")
    void differentLockNamesRunConcurrently() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(2)
                .child(Isolated.builder("first", "lock-a")
                        .body(Step.of("step-a", ctx -> {
                            var current = active.incrementAndGet();
                            maxActive.accumulateAndGet(current, Math::max);
                            sleep(50);
                            active.decrementAndGet();
                        }))
                        .build())
                .child(Isolated.builder("second", "lock-b")
                        .body(Step.of("step-b", ctx -> {
                            var current = active.incrementAndGet();
                            maxActive.accumulateAndGet(current, Math::max);
                            sleep(50);
                            active.decrementAndGet();
                        }))
                        .build())
                .build();

        var result = runner(2).run(root);

        assertThat(result.isPassed()).isTrue();
        assertThat(maxActive.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("re-entrant nested same lock")
    void reEntrantNestedSameLock() {
        var executed = new AtomicInteger();
        var action = Isolated.builder("outer", "L")
                .body(Isolated.builder("inner", "L")
                        .body(Step.of("work", ctx -> executed.incrementAndGet()))
                        .build())
                .build();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> runner(1).run(action));

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("re-entrant nested same lock inside Parallel body")
    void reEntrantNestedSameLockInsideParallelBody() {
        var executed = new AtomicInteger();
        var action = Isolated.builder("outer", "L")
                .body(Parallel.builder("body")
                        .parallelism(2)
                        .child(Isolated.builder("inner-1", "L")
                                .body(Step.of("work-1", ctx -> executed.incrementAndGet()))
                                .build())
                        .child(Isolated.builder("inner-2", "L")
                                .body(Step.of("work-2", ctx -> executed.incrementAndGet()))
                                .build())
                        .build())
                .build();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> runner(2).run(action));

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("lock released on body failure")
    void lockReleasedOnBodyFailure() {
        var secondExecuted = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Isolated.builder("first", "L")
                        .body(Step.of("fail", ctx -> FailException.fail("expected")))
                        .build())
                .child(Isolated.builder("second", "L")
                        .body(Step.of("ok", ctx -> secondExecuted.incrementAndGet()))
                        .build())
                .build();

        var result = runner(1).run(root);

        assertThat(secondExecuted.get()).isEqualTo(1);
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    @DisplayName("lock released on body abort")
    void lockReleasedOnBodyAbort() {
        var secondExecuted = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Isolated.builder("first", "L")
                        .body(Step.of("abort", ctx -> AbortedException.abort("expected")))
                        .build())
                .child(Isolated.builder("second", "L")
                        .body(Step.of("ok", ctx -> secondExecuted.incrementAndGet()))
                        .build())
                .build();

        var result = runner(1).run(root);

        assertThat(secondExecuted.get()).isEqualTo(1);
        assertThat(result.descriptor().orElseThrow().isAborted()).isTrue();
    }

    @Test
    @DisplayName("Parallel.parallelism bounds within Isolated body")
    void parallelParallelismBoundsWithinIsolatedBody() {
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        var parallelBody = Parallel.builder("db-tests").parallelism(3);
        for (var i = 0; i < 10; i++) {
            var idx = i;
            parallelBody.child(Step.of("child-" + idx, ctx -> {
                var current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                sleep(25);
                active.decrementAndGet();
            }));
        }
        var action =
                Isolated.builder("db", "db-lock").body(parallelBody.build()).build();

        var result = runner(8).run(action);

        assertThat(result.isPassed()).isTrue();
        assertThat(maxActive.get()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("same-lock waiters work-steal while blocked")
    void sameLockWaitersWorkStealWhileBlocked() throws Exception {
        var innerStarted = new java.util.concurrent.CountDownLatch(4);
        var releaseInner = new java.util.concurrent.CountDownLatch(1);
        var innerParallel = Parallel.builder("inner").parallelism(4);
        for (var i = 0; i < 4; i++) {
            innerParallel.child(Step.of("inner-" + i, ctx -> {
                innerStarted.countDown();
                releaseInner.await();
            }));
        }
        var root = Parallel.builder("root")
                .parallelism(4)
                .child(Isolated.builder("holder", "L")
                        .body(innerParallel.build())
                        .build())
                .child(Isolated.builder("waiter-1", "L")
                        .body(Step.of("waiter-1", ctx -> {}))
                        .build())
                .child(Isolated.builder("waiter-2", "L")
                        .body(Step.of("waiter-2", ctx -> {}))
                        .build())
                .child(Isolated.builder("waiter-3", "L")
                        .body(Step.of("waiter-3", ctx -> {}))
                        .build())
                .build();

        var resultFuture = CompletableFuture.supplyAsync(() -> runner(4).run(root));
        try {
            assertThat(innerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    .as("lock waiters should not occupy all scheduler workers while the lock holder waits")
                    .isTrue();
        } finally {
            releaseInner.countDown();
        }

        assertThat(resultFuture.get(5, java.util.concurrent.TimeUnit.SECONDS).isPassed())
                .isTrue();
    }

    @Test
    @DisplayName("lock not acquired on SKIP dispatch")
    void lockNotAcquiredOnSkipDispatch() {
        var bodyExecuted = new AtomicInteger();
        var action = Sequence.builder("root")
                .child(Step.of("fail", ctx -> FailException.fail("expected")))
                .child(Isolated.builder("skipped-iso", "L")
                        .body(Step.of("should-skip", ctx -> bodyExecuted.incrementAndGet()))
                        .build())
                .build();

        var result = runner(1).run(action);

        assertThat(result.isFailed()).isTrue();
        assertThat(bodyExecuted.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("null displayName throws")
    void nullDisplayNameThrows() {
        assertThatThrownBy(() -> Isolated.builder(null, "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("blank displayName throws")
    void blankDisplayNameThrows() {
        assertThatThrownBy(() -> Isolated.builder("", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null lockName throws")
    void nullLockNameThrows() {
        assertThatThrownBy(() -> Isolated.builder("x", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("blank lockName throws")
    void blankLockNameThrows() {
        assertThatThrownBy(() -> Isolated.builder("x", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null body action throws")
    void nullBodyActionThrows() {
        assertThatThrownBy(() -> Isolated.builder("x", "L").body((org.paramixel.api.action.Action) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("build without body throws")
    void buildWithoutBodyThrows() {
        assertThatThrownBy(() -> Isolated.builder("x", "L").build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("coordination action classification")
    void coordinationActionClassification() throws Exception {
        var action =
                Isolated.builder("iso", "L").body(Step.of("work", ctx -> {})).build();
        var descriptor = new nonapi.org.paramixel.action.DescriptorBuilder().discover(action);

        assertThat(descriptor.isCoordinationAction()).isTrue();
    }

    @Test
    @DisplayName("leaf action classification")
    void leafActionClassification() throws Exception {
        var action =
                Isolated.builder("iso", "L").body(Step.of("work", ctx -> {})).build();
        var descriptor = new nonapi.org.paramixel.action.DescriptorBuilder().discover(action);

        assertThat(descriptor.isLeafAction()).isFalse();
    }

    @Test
    @DisplayName("listener callbacks fire")
    void listenerCallbacksFire() {
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
        var action =
                Isolated.builder("iso", "L").body(Step.of("work", ctx -> {})).build();

        var result = runner(1, listener).run(action);

        assertThat(result.isPassed()).isTrue();
        assertThat(before).containsExactlyInAnyOrder("iso", "work");
        assertThat(after).containsExactlyInAnyOrder("iso", "work");
    }

    @Test
    @DisplayName("body builder overwrites previous")
    void bodyBuilderOverwritesPrevious() {
        var first = Step.of("first", ctx -> {});
        var second = Step.of("second", ctx -> {});
        var isolated = Isolated.builder("iso", "L").body(first).body(second).build();

        assertThat(isolated.body().displayName()).isEqualTo("second");
    }

    @Test
    @Timeout(15)
    @DisplayName("close with multiple deferred same-lock waiters does not strand descriptors")
    void closeWithMultipleDeferredSameLockWaitersDoesNotStrandDescriptors() throws Exception {
        var holderStarted = new CountDownLatch(1);
        var holderRelease = new CountDownLatch(1);
        var waiter1Started = new AtomicInteger();
        var waiter2Started = new AtomicInteger();

        var action = Parallel.builder("root")
                .parallelism(3)
                .child(Isolated.builder("holder", "L")
                        .body(Step.of("holder-body", ctx -> {
                            holderStarted.countDown();
                            try {
                                holderRelease.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .build())
                .child(Isolated.builder("waiter1", "L")
                        .body(Step.of("waiter1-body", ctx -> waiter1Started.incrementAndGet()))
                        .build())
                .child(Isolated.builder("waiter2", "L")
                        .body(Step.of("waiter2-body", ctx -> waiter2Started.incrementAndGet()))
                        .build())
                .build();

        var root = new DescriptorBuilder().discover(action);
        root.freeze();
        var scheduler = new Scheduler(3, 16);
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
            var future = scheduler.schedule(root, ExecutionMode.RUN, context);

            // Wait for the holder to enter its body.
            assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // Start close on a helper thread.
            var closeThread = new Thread(scheduler::close, "closer");
            closeThread.start();

            // Give close time to set closing state and begin shutdown.
            Thread.sleep(200);

            // Release the holder in finally to guarantee it happens.
            holderRelease.countDown();

            closeThread.join(10_000);
            assertThat(closeThread.isAlive()).as("close() should return").isFalse();

            // Root future must be done and root must be terminal.
            assertThat(future.isDone()).as("root future must be done").isTrue();
            assertThat(root.isCompleted()).as("root must be terminal").isTrue();

            for (var child : root.children()) {
                assertThat(child.isCompleted())
                        .as("child " + child.action().displayName() + " must be terminal")
                        .isTrue();
            }

            // Waiter bodies should not begin after shutdown unless they already started.
            // Since close() was called while the holder held the lock, waiters were deferred
            // and should not execute after shutdown.
        } finally {
            holderRelease.countDown();
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("concurrent owner release and close publishes each same-lock branch once")
    void concurrentOwnerReleaseAndClosePublishesEachSameLockBranchOnce() throws Exception {
        var holderStarted = new CountDownLatch(1);
        var holderRelease = new CountDownLatch(1);
        var completionCount = new AtomicInteger();

        var action = Parallel.builder("root")
                .parallelism(2)
                .child(Isolated.builder("holder", "L")
                        .body(Step.of("holder-body", ctx -> {
                            holderStarted.countDown();
                            try {
                                holderRelease.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .build())
                .child(Isolated.builder("waiter", "L")
                        .body(Step.of("waiter-body", ctx -> {}))
                        .build())
                .build();

        var root = new DescriptorBuilder().discover(action);
        root.freeze();
        var scheduler = new Scheduler(2, 16);
        var callback = new Scheduler.ExecutionCallback() {
            @Override
            public void onExecutionStart() {}

            @Override
            public void onExecutionComplete(final Throwable error) {
                completionCount.incrementAndGet();
            }
        };
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
            var future = scheduler.schedule(root, ExecutionMode.RUN, context, callback);

            assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // Release holder and close concurrently.
            var closeLatch = new CountDownLatch(1);
            var closeThread = new Thread(() -> {
                closeLatch.countDown();
                scheduler.close();
            });
            closeThread.start();
            closeLatch.await();

            // Release the holder concurrently with close.
            holderRelease.countDown();
            closeThread.join(10_000);

            assertThat(future.isDone()).as("root future must be done").isTrue();

            for (var child : root.children()) {
                assertThat(child.isCompleted())
                        .as("child " + child.action().displayName() + " must be terminal")
                        .isTrue();
            }

            // Each branch should be published at most once.
            assertThat(completionCount.get())
                    .as("callback completion must be at most once per branch")
                    .isLessThanOrEqualTo(1);
        } finally {
            holderRelease.countDown();
            scheduler.close();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("deferred same-lock waiter abort releases lock for independent action")
    void deferredSameLockWaiterAbortReleasesLockForIndependentAction() throws Exception {
        var holderStarted = new CountDownLatch(1);
        var holderRelease = new CountDownLatch(1);
        var holderCompleted = new CountDownLatch(1);
        var independentExecuted = new AtomicInteger();

        var action = Parallel.builder("root")
                .parallelism(1)
                .child(Isolated.builder("holder", "L")
                        .body(Step.of("holder-body", ctx -> {
                            holderStarted.countDown();
                            try {
                                holderRelease.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .build())
                .child(Isolated.builder("waiter", "L")
                        .body(Step.of("waiter-body", ctx -> {
                            // This waiter will be deferred behind the holder.
                        }))
                        .build())
                .child(Isolated.builder("independent", "L")
                        .body(Step.of("independent-body", ctx -> independentExecuted.incrementAndGet()))
                        .build())
                .build();

        var root = new DescriptorBuilder().discover(action);
        root.freeze();
        var scheduler = new Scheduler(1, 16);
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(), new Listener() {}, root, scheduler, new InstanceHolder());
            var future = scheduler.schedule(root, ExecutionMode.RUN, context);

            assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // Release the holder so the waiter can proceed and the independent action
            // can be scheduled.
            holderRelease.countDown();

            future.get(10, TimeUnit.SECONDS);
            holderCompleted.countDown();

            // The independent action should have completed.
            assertThat(independentExecuted.get())
                    .as("independent same-lock action must complete after holder release")
                    .isEqualTo(1);

            // All descriptors must be terminal.
            for (var child : root.children()) {
                assertThat(child.isCompleted())
                        .as("child " + child.action().displayName() + " must be terminal")
                        .isTrue();
            }
        } finally {
            holderRelease.countDown();
            scheduler.close();
        }
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

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
