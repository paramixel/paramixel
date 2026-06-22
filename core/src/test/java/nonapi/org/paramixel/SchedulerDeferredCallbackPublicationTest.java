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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.FailException;

/**
 * Characterizes safe publication of the deferred {@link Scheduler.ExecutionCallback}.
 *
 * <p>Defect D2: {@code ExecutionNode.callback}/{@code callbackInvoked} are non-volatile and
 * written <em>after</em> the node is published and children are scheduled. A continuation that
 * runs (inline during admission, or on a worker thread before the write is visible) reads stale
 * values and silently drops {@code onExecutionComplete(...)}. The fix publishes callback state
 * on the descriptor <em>before</em> action execution.
 */
@DisplayName("Scheduler deferred callback publication")
class SchedulerDeferredCallbackPublicationTest {

    private static final int ITERATIONS = 2_000;

    @Test
    @DisplayName("pre-aborted child widens the race window; callback completes exactly once every run")
    void preAbortedChildDeliversCallbackOnce() {
        var delivered = new AtomicInteger();
        var skipped = new AtomicInteger();
        for (var i = 0; i < ITERATIONS; i++) {
            var callback = new CountingCallback();
            runDeferredWithPreAbortedChild(callback);
            if (callback.completed.get() == 1) {
                delivered.incrementAndGet();
            } else {
                skipped.incrementAndGet();
            }
        }
        assertThat(skipped.get())
                .as("onExecutionComplete must be delivered exactly once on every run; "
                        + "dropped/stalled count must be zero (delivered=" + delivered + "/"
                        + ITERATIONS + ")")
                .isZero();
    }

    @Test
    @DisplayName("fast-completing deferred Sequential delivers callback exactly once every run")
    void fastCompletingDeferredSequentialDeliversCallbackOnce() {
        var delivered = new AtomicInteger();
        var skipped = new AtomicInteger();
        for (var i = 0; i < ITERATIONS; i++) {
            var callback = new CountingCallback();
            runFastDeferredSequential(callback);
            if (callback.completed.get() == 1) {
                delivered.incrementAndGet();
            } else {
                skipped.incrementAndGet();
            }
        }
        assertThat(skipped.get())
                .as("onExecutionComplete must be delivered exactly once on every run " + "(delivered=" + delivered + "/"
                        + ITERATIONS + ")")
                .isZero();
    }

    @Test
    @DisplayName("deferred composite closed during flight still delivers callback exactly once")
    void closingDuringFlightDeliversCallbackOnce() throws Exception {
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var scheduler = new Scheduler(2);
        var action = Sequential.builder("root")
                .child(Step.of("blocker", ctx -> {
                    firstStarted.countDown();
                    releaseFirst.await(20, TimeUnit.SECONDS);
                }))
                .child(Step.of("ok", ctx -> {}))
                .build();
        var root = new DescriptorBuilder().discover(action);
        var callback = new CountingCallback();
        try {
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());
            scheduler.schedule(root, ExecutionMode.RUN, context, callback);
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // Close while the Sequential is mid-flight so the continuation finalizes via the
            // shutdown short-circuit path. The callback must still be delivered exactly once.
            scheduler.close();
            releaseFirst.countDown();
        } finally {
            scheduler.close();
        }
        assertThat(callback.started.get())
                .as("onExecutionStart should have been invoked")
                .isEqualTo(1);
        assertThat(callback.completed.get())
                .as("onExecutionComplete must be delivered exactly once even when finalized during close")
                .isEqualTo(1);
    }

    /**
     * Builds a Parallel whose first child is pre-aborted before the Parallel executes. The
     * pre-aborted child completes synchronously during admission ({@code schedule()} sees
     * {@code isCompleted()}), enqueuing a continuation on a worker thread while the executing
     * thread has not yet written callback state — the widest structural race window.
     */
    private static void runDeferredWithPreAbortedChild(final CountingCallback callback) {
        var scheduler = new Scheduler(2);
        try {
            var action = Parallel.builder("root")
                    .parallelism(2)
                    .child(Step.of("aborted", ctx -> {}))
                    .child(Step.of("ok", ctx -> {}))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            // Pre-abort the first child so its completion is synchronous during admission.
            ((MutableDescriptor) root.children().get(0)).abort(Status.aborted("pre-aborted"), new FailException("pre"));
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());
            try {
                scheduler.schedule(root, ExecutionMode.RUN, context, callback).join();
            } catch (java.util.concurrent.CompletionException expected) {
                // The pre-aborted child makes the aggregate FAILED/ABORTED; the run is expected to
                // fail. This test asserts callback delivery, not run success.
            }
        } finally {
            scheduler.close();
        }
    }

    private static void runFastDeferredSequential(final CountingCallback callback) {
        var scheduler = new Scheduler(2);
        try {
            var action = Sequential.builder("root")
                    .child(Step.of("a", ctx -> {}))
                    .child(Step.of("b", ctx -> {}))
                    .build();
            var root = new DescriptorBuilder().discover(action);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());
            scheduler.schedule(root, ExecutionMode.RUN, context, callback).join();
        } finally {
            scheduler.close();
        }
    }

    private static final class CountingCallback implements Scheduler.ExecutionCallback {
        final AtomicInteger admitted = new AtomicInteger();
        final AtomicInteger started = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onAdmitted() {
            admitted.incrementAndGet();
        }

        @Override
        public void onExecutionStart() {
            started.incrementAndGet();
        }

        @Override
        public void onExecutionComplete(final Throwable executionError) {
            completed.incrementAndGet();
            error.set(executionError);
        }
    }
}
