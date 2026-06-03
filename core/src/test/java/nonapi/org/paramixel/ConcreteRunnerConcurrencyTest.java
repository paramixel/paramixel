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

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteRunner concurrency")
class ConcreteRunnerConcurrencyTest {

    @Test
    @DisplayName("concurrent run(Action) calls serialize listener callbacks without interleaving")
    void concurrentRunBuilderCallsSerializeListenerCallbacks() throws Exception {
        var threadCount = 4;
        var barrier = new CyclicBarrier(threadCount);
        var errors = new AtomicInteger();
        var callbackLog = new CopyOnWriteArrayList<CallbackEntry>();
        var activeRun = new AtomicInteger();
        var maxConcurrentRuns = new AtomicInteger();

        var listener = new InterleavingDetectorListener(callbackLog, activeRun, maxConcurrentRuns);
        var runner = createRunner(2, listener);

        var builder = Step.of("concurrent-step", context -> {});

        var latch = new CountDownLatch(threadCount);
        var threads = new Thread[threadCount];
        for (var i = 0; i < threadCount; i++) {
            var index = i;
            threads[i] = new Thread(
                    () -> {
                        try {
                            barrier.await();
                            var result = runner.run(builder);
                            assertThat(result.isPassed()).isTrue();
                        } catch (Throwable t) {
                            errors.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    },
                    "runner-thread-" + index);
        }

        for (var t : threads) {
            t.start();
        }
        latch.await();

        assertThat(errors.get()).isZero();
        assertThat(maxConcurrentRuns.get()).isLessThanOrEqualTo(1);

        verifyNoCallbackInterleaving(callbackLog, threadCount);
    }

    @Test
    @DisplayName("concurrent runAndReturnExitCode(Action) calls serialize execution")
    void concurrentRunAndReturnExitCodeCallsSerialize() throws Exception {
        var threadCount = 4;
        var barrier = new CyclicBarrier(threadCount);
        var errors = new AtomicInteger();
        var activeRun = new AtomicInteger();
        var maxConcurrentRuns = new AtomicInteger();

        var listener = new ConcurrencyCountingListener(activeRun, maxConcurrentRuns);
        var runner = createRunner(2, listener);

        var builder = Step.of("exit-code-step", context -> {});

        var latch = new CountDownLatch(threadCount);
        var threads = new Thread[threadCount];
        for (var i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    var exitCode = runner.runAndReturnExitCode(builder);
                    assertThat(exitCode).isZero();
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        for (var t : threads) {
            t.start();
        }
        latch.await();

        assertThat(errors.get()).isZero();
        assertThat(maxConcurrentRuns.get()).isLessThanOrEqualTo(1);
    }

    private static void verifyNoCallbackInterleaving(CopyOnWriteArrayList<CallbackEntry> log, int expectedRunCount) {
        var runStartCount = 0;
        var runCompleteCount = 0;
        var activeRunDepth = 0;

        for (var entry : log) {
            switch (entry.method) {
                case "onRunStarted" -> {
                    runStartCount++;
                    activeRunDepth++;
                    assertThat(activeRunDepth)
                            .as("onRunStarted must not interleave with another run")
                            .isLessThanOrEqualTo(1);
                }
                case "onRunCompleted" -> {
                    activeRunDepth--;
                    runCompleteCount++;
                    assertThat(activeRunDepth)
                            .as("onRunCompleted must not interleave with another run")
                            .isGreaterThanOrEqualTo(0);
                }
                default -> {
                    // discovery and execution callbacks within a run are expected
                }
            }
        }

        assertThat(runStartCount).isEqualTo(expectedRunCount);
        assertThat(runCompleteCount).isEqualTo(expectedRunCount);
    }

    private static Runner createRunner(int parallelism, Listener listener) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                "32",
                Configuration.ANSI,
                "false"));
        return Runner.builder().configuration(configuration).listener(listener).build();
    }

    private record CallbackEntry(String method) {}

    private static final class InterleavingDetectorListener implements Listener {

        private final CopyOnWriteArrayList<CallbackEntry> callbackLog;
        private final AtomicInteger activeRun;
        private final AtomicInteger maxConcurrentRuns;

        private InterleavingDetectorListener(
                CopyOnWriteArrayList<CallbackEntry> callbackLog,
                AtomicInteger activeRun,
                AtomicInteger maxConcurrentRuns) {
            this.callbackLog = callbackLog;
            this.activeRun = activeRun;
            this.maxConcurrentRuns = maxConcurrentRuns;
        }

        @Override
        public void initialize(Configuration configuration) {
            callbackLog.add(new CallbackEntry("initialize"));
        }

        @Override
        public void onRunStarted() {
            var current = activeRun.incrementAndGet();
            maxConcurrentRuns.accumulateAndGet(current, Math::max);
            callbackLog.add(new CallbackEntry("onRunStarted"));
        }

        @Override
        public void onDiscoveryStarted() {
            callbackLog.add(new CallbackEntry("onDiscoveryStarted"));
        }

        @Override
        public void onDiscoveryCompleted(Descriptor root) {
            callbackLog.add(new CallbackEntry("onDiscoveryCompleted"));
        }

        @Override
        public void onBeforeExecution(Descriptor descriptor) {
            callbackLog.add(new CallbackEntry("onBeforeExecution"));
        }

        @Override
        public void onAfterExecution(Descriptor descriptor) {
            callbackLog.add(new CallbackEntry("onAfterExecution"));
        }

        @Override
        public void onRunCompleted(Result result) {
            callbackLog.add(new CallbackEntry("onRunCompleted"));
            activeRun.decrementAndGet();
        }
    }

    private static final class ConcurrencyCountingListener implements Listener {

        private final AtomicInteger activeRun;
        private final AtomicInteger maxConcurrentRuns;

        private ConcurrencyCountingListener(AtomicInteger activeRun, AtomicInteger maxConcurrentRuns) {
            this.activeRun = activeRun;
            this.maxConcurrentRuns = maxConcurrentRuns;
        }

        @Override
        public void onRunStarted() {
            var current = activeRun.incrementAndGet();
            maxConcurrentRuns.accumulateAndGet(current, Math::max);
        }

        @Override
        public void onRunCompleted(Result result) {
            activeRun.decrementAndGet();
        }
    }
}
