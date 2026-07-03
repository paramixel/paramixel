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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler lifecycle defect regressions")
class SchedulerLifecycleDefectTest {

    @Test
    @Timeout(5)
    @DisplayName("fail-fast skipped descriptor invokes no execution callbacks")
    void failFastSkippedDescriptorInvokesNoExecutionCallbacks() {
        var scheduler = new Scheduler(1);
        try {
            scheduler.setFailFast(true);
            var root = new ConcreteDescriptor(Step.of("root", context -> {}));
            var failing = newChildDescriptor(root, Step.of("failing", context -> {
                throw new IllegalStateException("boom");
            }));
            var skipped = newChildDescriptor(root, Step.of("skipped", context -> {}));
            var context = newContext(scheduler, root);

            assertThatThrownBy(() -> scheduler
                            .schedule(failing, ExecutionMode.RUN, context)
                            .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);

            var admittedInvoked = new AtomicBoolean();
            var startInvoked = new AtomicBoolean();
            var completeInvoked = new AtomicBoolean();
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onAdmitted() {
                    admittedInvoked.set(true);
                }

                @Override
                public void onExecutionStart() {
                    startInvoked.set(true);
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeInvoked.set(true);
                }
            };

            assertThatCode(() -> scheduler
                            .schedule(skipped, ExecutionMode.RUN, context, callback)
                            .join())
                    .doesNotThrowAnyException();

            assertThat(skipped.isSkipped()).isTrue();
            // A skipped action never begins execution, so the entire paired ExecutionCallback
            // lifecycle (onAdmitted/onExecutionStart/onExecutionComplete) is omitted. The
            // descriptor future still completes (skipped is not a failure).
            assertThat(admittedInvoked).isFalse();
            assertThat(startInvoked).isFalse();
            assertThat(completeInvoked).isFalse();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("completion callback failure is reported without changing future result")
    void completionCallbackFailureIsReportedWithoutChangingFutureResult() throws Exception {
        var scheduler = new Scheduler(1);
        var originalErr = System.err;
        var capturedErr = new ByteArrayOutputStream();
        try (var capture = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            var root = new ConcreteDescriptor(Step.of("success", context -> {}));
            var context = newContext(scheduler, root);
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {}

                @Override
                public void onExecutionComplete(final Throwable error) {
                    throw new RuntimeException("cleanup failed");
                }
            };

            assertThatCode(() -> scheduler
                            .schedule(root, ExecutionMode.RUN, context, callback)
                            .join())
                    .doesNotThrowAnyException();
            assertThat(root.isPassed()).isTrue();
            assertThat(waitForCapturedErr(capturedErr, "ExecutionCallback.onExecutionComplete threw exception"))
                    .contains("ExecutionCallback.onExecutionComplete threw exception")
                    .contains("cleanup failed");
        } finally {
            System.setErr(originalErr);
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("execution start callback failure leaves descriptor terminal")
    void executionStartCallbackFailureLeavesDescriptorTerminal() {
        var scheduler = new Scheduler(1);
        try {
            var root = new ConcreteDescriptor(Step.of("success", context -> {}));
            var context = newContext(scheduler, root);
            var completeError = new AtomicReference<Throwable>();
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onExecutionStart() {
                    throw new IllegalStateException("start failed");
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeError.set(error);
                }
            };

            assertThatThrownBy(() -> scheduler
                            .schedule(root, ExecutionMode.RUN, context, callback)
                            .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(root.isCompleted()).isTrue();
            assertThat(root.isFailed()).isTrue();
            assertThat(root.message()).contains("start failed");
            assertThat(completeError.get())
                    .as("onExecutionComplete must be called with the start failure error")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("start failed");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("onAdmitted failure delivers onExecutionComplete with the admission error")
    void admissionCallbackFailureDeliversOnExecutionComplete() {
        var scheduler = new Scheduler(1);
        try {
            var root = new ConcreteDescriptor(Step.of("success", context -> {}));
            var context = newContext(scheduler, root);
            var started = new AtomicBoolean();
            var completeError = new AtomicReference<Throwable>();
            var callback = new Scheduler.ExecutionCallback() {
                @Override
                public void onAdmitted() {
                    throw new IllegalStateException("admission failed");
                }

                @Override
                public void onExecutionStart() {
                    started.set(true);
                }

                @Override
                public void onExecutionComplete(final Throwable error) {
                    completeError.set(error);
                }
            };

            assertThatThrownBy(() -> scheduler
                            .schedule(root, ExecutionMode.RUN, context, callback)
                            .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);

            assertThat(root.isCompleted()).isTrue();
            assertThat(root.isFailed()).isTrue();
            assertThat(root.message()).contains("admission failed");
            assertThat(started.get())
                    .as("onExecutionStart must not be called after onAdmitted throws")
                    .isFalse();
            assertThat(completeError.get())
                    .as("onExecutionComplete must be called with the admission error")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("admission failed");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("managedJoin exits after scheduler is closed (executor terminated, drain empty)")
    void managedJoinExitsAfterExecutorTermination() throws Exception {
        var scheduler = new Scheduler(1);
        var root = new ConcreteDescriptor(Step.of("root", context -> {}));
        var context = newContext(scheduler, root);

        // Create a future that will never complete — simulating a deferred descriptor
        // whose completion path is severed by executor termination.
        var hangingFuture = new CompletableFuture<Descriptor>();

        var joinError = new AtomicReference<Throwable>();
        var joinDone = new CountDownLatch(1);
        var joinThread = new Thread(
                () -> {
                    try {
                        scheduler.managedJoin(hangingFuture);
                    } catch (Throwable t) {
                        joinError.set(t);
                    } finally {
                        joinDone.countDown();
                    }
                },
                "managedJoin-test");
        joinThread.setDaemon(true);
        joinThread.start();

        // Give managedJoin time to enter the backoff-park loop.
        Thread.sleep(200);

        // Close the scheduler — this shuts down the executor, waits for termination,
        // and clears the shutdown drain. After close() returns, managedJoin should
        // detect isTerminated() && shutdownDrain.isEmpty() and exit the loop.
        scheduler.close();

        // managedJoin must return within the timeout (the test's @Timeout(10) guards this).
        assertThat(joinDone.await(5, TimeUnit.SECONDS))
                .as("managedJoin must exit after executor termination")
                .isTrue();
        // After termination, the uncompletable future is cancelled and join() throws.
        // This is acceptable — the critical property is that managedJoin returns,
        // not that it returns successfully.
        if (joinError.get() != null) {
            assertThat(joinError.get()).isInstanceOf(java.util.concurrent.CancellationException.class);
        }
    }

    private static ConcreteContext newContext(final Scheduler scheduler, final MutableDescriptor root) {
        return new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());
    }

    private static MutableDescriptor newChildDescriptor(final MutableDescriptor parent, final Action action) {
        var child = new ConcreteDescriptor(parent, action);
        parent.addChild(child);
        return child;
    }

    private static String waitForCapturedErr(final ByteArrayOutputStream capturedErr, final String expected)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        String stderr;
        do {
            stderr = capturedErr.toString(StandardCharsets.UTF_8);
            if (stderr.contains(expected)) {
                return stderr;
            }
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        return stderr;
    }
}
