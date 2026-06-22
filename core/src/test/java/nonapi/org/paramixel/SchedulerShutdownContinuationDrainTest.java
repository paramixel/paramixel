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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Characterizes the continuation drain behavior when {@link Scheduler#close()} runs
 * while a large coordination tree is mid-flight.
 *
 * <p>Defect D1: the pre-fix inline {@code executeContinuation} fallback recurses once
 * per remaining child, blowing the stack and leaving the root non-terminal with its
 * scheduled future never completed. These tests pin the iterative-drain fix.
 */
@DisplayName("Scheduler shutdown continuation drain")
@SuppressWarnings("removal")
class SchedulerShutdownContinuationDrainTest {

    @ParameterizedTest(name = "Sequential of {0} children completes after close()")
    @ValueSource(ints = {100, 2_000, 20_000})
    void sequentialLargeTreeCompletesAfterClose(final int childCount) throws Exception {
        assertCompletesAfterClose(blocker -> buildSequential(childCount, blocker));
    }

    @ParameterizedTest(name = "Sequence of {0} children completes after close()")
    @ValueSource(ints = {100, 2_000})
    void sequenceLargeTreeCompletesAfterClose(final int childCount) throws Exception {
        assertCompletesAfterClose(blocker -> buildSequence(childCount, blocker));
    }

    @ParameterizedTest(name = "Parallel of {0} children completes after close()")
    @ValueSource(ints = {100, 2_000})
    void parallelLargeTreeCompletesAfterClose(final int childCount) throws Exception {
        assertCompletesAfterClose(blocker -> buildParallel(childCount, blocker));
    }

    @ParameterizedTest(name = "Loop of {0} iterations completes after close()")
    @ValueSource(ints = {100, 2_000})
    void loopLargeTreeCompletesAfterClose(final int childCount) throws Exception {
        assertCompletesAfterClose(blocker ->
                Loop.builder("root").body(blocker).maxIterations(childCount).build());
    }

    private static Action buildSequential(final int childCount, final Step blocker) {
        var builder = Sequential.builder("root").child(blocker);
        for (var i = 1; i < childCount; i++) {
            builder = builder.child(Step.of("s-" + i, ctx -> {}));
        }
        return builder.build();
    }

    private static Action buildSequence(final int childCount, final Step blocker) {
        var builder = Sequence.builder("root").child(blocker);
        for (var i = 1; i < childCount; i++) {
            builder = builder.child(Step.of("s-" + i, ctx -> {}));
        }
        return builder.build();
    }

    private static Action buildParallel(final int childCount, final Step blocker) {
        var builder = Parallel.builder("root").parallelism(2).child(blocker);
        for (var i = 1; i < childCount; i++) {
            builder = builder.child(Step.of("s-" + i, ctx -> {}));
        }
        return builder.build();
    }

    private void assertCompletesAfterClose(final Function<Step, Action> rootFactory) throws Exception {
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var blocker = Step.of("blocker", ctx -> {
            firstStarted.countDown();
            try {
                releaseFirst.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var root = new DescriptorBuilder().discover(rootFactory.apply(blocker));
        root.markScheduled();
        CompletableFuture<?> rootFuture = root.scheduledFuture();
        var scheduler = new Scheduler(2);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        var uncaught = new AtomicReference<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught.compareAndSet(null, e));

        // executeDescriptor blocks until the descriptor tree completes (via
        // managedJoin on the nodeCompletion future), so it must run on a
        // separate thread to allow the test to call close().
        var execThread = new Thread(() -> scheduler.executeDescriptor(root, context, ExecutionMode.RUN), "exec");
        execThread.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS))
                .as("blocking child should start")
                .isTrue();

        var closer = new Thread(scheduler::close, "closer");
        closer.start();
        // Let close() reach executor shutdown so continuations fall back to the
        // shutdown drain path.
        Thread.sleep(500);
        releaseFirst.countDown();

        closer.join(60_000);
        assertThat(closer.isAlive()).as("close() should return").isFalse();

        execThread.join(60_000);
        assertThat(execThread.isAlive()).as("exec thread should return").isFalse();

        assertThat(root.isCompleted())
                .as("root must reach a terminal status after close")
                .isTrue();
        assertThat(hasPendingDescriptor(root))
                .as("shutdown drain should not leave pending descriptors in the tree")
                .isFalse();
        assertThat(rootFuture).isNotNull();
        assertThat(rootFuture.isDone())
                .as("root scheduled future must complete")
                .isTrue();
        assertThat(uncaught.get())
                .as("no StackOverflowError or other uncaught throwable")
                .isNull();
    }

    private static boolean hasPendingDescriptor(final Descriptor descriptor) {
        if (!descriptor.isCompleted()) {
            return true;
        }
        if (descriptor
                .before()
                .filter(SchedulerShutdownContinuationDrainTest::hasPendingDescriptor)
                .isPresent()) {
            return true;
        }
        for (var child : descriptor.children()) {
            if (hasPendingDescriptor(child)) {
                return true;
            }
        }
        return descriptor
                .after()
                .filter(SchedulerShutdownContinuationDrainTest::hasPendingDescriptor)
                .isPresent();
    }
}
