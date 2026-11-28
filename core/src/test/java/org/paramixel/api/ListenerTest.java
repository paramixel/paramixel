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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.reporting.ReportingDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Listener contract: event ordering, exactly one terminal event per occurrence, and error isolation.
 */
class ListenerTest {

    @Test
    @DisplayName("each occurrence produces exactly one terminal event, delivered in order")
    void terminalEventsAreExactlyOnce() {
        final var listener = new RecordingListener();
        final var definition = Parallel.builder("root", Pool.named("p"))
                .each(List.of("a", "b", "c"), name -> Sequential.builder(name).step("work", () -> {}))
                .build();

        final var result = Runner.builder()
                .listener(listener)
                .pool(Pool.named("p"), 3)
                .build()
                .run(definition);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        final Map<String, Integer> terminals = new HashMap<>();
        for (TaskEvent event : listener.events) {
            if (event.phase() == TaskPhase.TERMINAL) {
                terminals.merge(event.occurrenceId(), 1, Integer::sum);
            }
        }
        assertThat(terminals).hasSize(result.occurrenceCount());
        assertThat(terminals.values()).allSatisfy(count -> assertThat(count).isEqualTo(1));
        final var sequences = listener.events.stream().map(TaskEvent::sequence).toList();
        assertThat(sequences).isSorted().doesNotHaveDuplicates();
        assertThat(listener.threadNames).allSatisfy(name -> assertThat(name).isEqualTo("paramixel-listener"));
    }

    @Test
    @DisplayName("per-occurrence phase order is legal")
    void phaseOrderIsLegal() {
        final var listener = new RecordingListener();
        final var definition = Sequential.builder("root")
                .step("body", () -> {}, children -> children.step("nested", () -> {}))
                .build();

        Runner.builder().listener(listener).build().run(definition);

        final Map<String, List<TaskPhase>> phasesByOccurrence = new HashMap<>();
        for (TaskEvent event : listener.events) {
            phasesByOccurrence
                    .computeIfAbsent(event.occurrenceId(), key -> new ArrayList<>())
                    .add(event.phase());
        }
        for (List<TaskPhase> phases : phasesByOccurrence.values()) {
            assertThat(phases.get(0)).isEqualTo(TaskPhase.ADMITTED);
            assertThat(phases.get(phases.size() - 1)).isEqualTo(TaskPhase.TERMINAL);
            assertThat(phases).doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("listener exceptions never change the semantic status")
    void listenerFailuresAreIsolated() {
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                throw new IllegalStateException("listener exploded");
            }

            @Override
            public void onRunCompleted(final Result result) {
                throw new IllegalStateException("listener completion exploded");
            }
        };

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(Sequential.builder("root").step("work", () -> {}).build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("listener"));
    }

    @Test
    @DisplayName("warnings and diagnostics are observable exactly once")
    void warningsAreObservable() {
        final var warnings = new AtomicInteger();
        final var listener = new Listener() {
            @Override
            public void onWarning(final String message) {
                warnings.incrementAndGet();
            }
        };

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(Sequential.builder("empty").build());

        assertThat(warnings).hasValue(1);
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic).contains("EMPTY_TASK"));
    }

    @Test
    @DisplayName("run started and run completed are delivered once with the same configuration")
    void runLifecycleIsExactlyOnce() {
        final var started = new AtomicInteger();
        final var completed = new AtomicInteger();
        final var listener = new Listener() {
            @Override
            public void onRunStarted(final Configuration configuration) {
                started.incrementAndGet();
            }

            @Override
            public void onRunCompleted(final Result result) {
                completed.incrementAndGet();
            }
        };

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(Sequential.builder("root").step("work", () -> {}).build());

        assertThat(started).hasValue(1);
        assertThat(completed).hasValue(1);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("onRunCompleted receives the run's infrastructure diagnostics")
    void runCompletedReceivesInfrastructureDiagnostics() {
        final var seen = new AtomicReference<List<String>>();
        final var listener = new Listener() {
            @Override
            public void onRunCompleted(final Result result) {
                seen.set(result.diagnostics());
            }
        };
        final Task definition = Sequential.builder("root")
                .timeout(Duration.ofMillis(80))
                .step("stuck", () -> ignoreInterruptionFor(Duration.ofMillis(400)))
                .build();

        final var result = Runner.builder()
                .listener(listener)
                .configuration(Configuration.of(
                        Map.of(Configuration.SHUTDOWN_GRACE_MILLIS, "80", Configuration.ANSI, "false")))
                .build()
                .run(definition);

        assertThat(result.isQuiescent()).isFalse();
        assertThat(seen.get()).anySatisfy(diagnostic -> assertThat(diagnostic).contains("NON_QUIESCENT"));
        assertThat(result.diagnostics()).containsAll(seen.get());
    }

    @Test
    @Timeout(30)
    @DisplayName("a listener that does not drain in time cannot complete the report or rewrite passing task outcomes")
    void blockedListenerDrainIsBoundedAndReported() throws InterruptedException {
        final var blocked = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var dispatcher = new AtomicReference<Thread>();
        final var completionStarted = new AtomicBoolean();
        final var eventsDelivered = new AtomicInteger();
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                eventsDelivered.incrementAndGet();
                if (event.phase() == TaskPhase.BODY
                        && "a".equals(event.name())
                        && dispatcher.compareAndSet(null, Thread.currentThread())) {
                    blocked.countDown();
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void onRunCompleted(final Result result) {
                completionStarted.set(true);
            }
        };
        final Task definition = Sequential.builder("root")
                .step("a", () -> {})
                .step("b", () -> {})
                .step("c", () -> {})
                .build();

        final var startedNanos = System.nanoTime();
        final Result result;
        try {
            result = Runner.builder()
                    .listener(listener)
                    .configuration(Configuration.of(
                            Map.of(Configuration.LISTENER_DRAIN_TIMEOUT_MILLIS, "500", Configuration.ANSI, "false")))
                    .build()
                    .run(definition);
        } finally {
            release.countDown();
        }
        final var elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);

        assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();
        dispatcher.get().join(5_000L);
        assertThat(dispatcher.get().isAlive()).isFalse();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.diagnostics())
                .anySatisfy(
                        diagnostic -> assertThat(diagnostic).contains(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC));
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.isQuiescent()).isTrue();
        assertThat(result.failures())
                .extracting(Failure::phase)
                .containsExactly(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC);
        assertThat(completionStarted).isFalse();
        assertThat(eventsDelivered).hasValue(3);
    }

    @Test
    @Timeout(15)
    void saturatedListenerCanResumeWithoutLosingOrReorderingEvents() throws Exception {
        final var listening = new CountDownLatch(1);
        final var allowDelivery = new CountDownLatch(1);
        final var bodyStarted = new CountDownLatch(1);
        final var events = new ArrayList<TaskEvent>();
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                if (events.isEmpty()) {
                    listening.countDown();
                    try {
                        allowDelivery.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                events.add(event);
            }
        };
        final var definition = Sequential.builder("root")
                .onlyIf(context -> true)
                .before(bodyStarted::countDown)
                .step("work", () -> {})
                .build();
        try (var runner = smallQueueRunner(listener, 5_000)) {
            final var run = startRun(runner, definition);
            try {
                assertThat(listening.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(bodyStarted.await(250, TimeUnit.MILLISECONDS)).isFalse();
                allowDelivery.countDown();
                final var result = run.get(5, TimeUnit.SECONDS);
                assertThat(bodyStarted.getCount()).isZero();
                assertThat(result.exitCode()).isZero();
                assertThat(events).extracting(TaskEvent::sequence).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
                assertThat(events.stream()
                                .filter(event -> event.phase() == TaskPhase.TERMINAL)
                                .count())
                        .isEqualTo(result.occurrenceCount());
            } finally {
                allowDelivery.countDown();
            }
        }
    }

    @Test
    @Timeout(15)
    void saturatedListenerStopsTheRunAndSuppressesCompletion() throws Exception {
        final var listening = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var completed = new AtomicBoolean();
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                listening.countDown();
                awaitIgnoringInterruption(release);
            }

            @Override
            public void onRunCompleted(final Result result) {
                completed.set(true);
            }
        };
        final var calls = new AtomicInteger();
        final var definition =
                Sequential.builder("root").step("work", calls::incrementAndGet).build();
        try (var runner = smallQueueRunner(listener, 300)) {
            final var run = startRun(runner, definition);
            try {
                assertThat(listening.await(5, TimeUnit.SECONDS)).isTrue();
                final var result = run.get(3, TimeUnit.SECONDS);
                assertThat(result.status().kind()).isEqualTo(Status.Kind.CANCELED);
                assertThat(result.isQuiescent()).isTrue();
                assertThat(result.exitCode()).isNotZero();
                assertThat(result.failures())
                        .extracting(Failure::phase)
                        .contains(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC);
                assertThat(result.diagnostics()).anyMatch(message -> message.contains("incomplete"));
                assertThat(completed).isFalse();
                assertThat(calls).hasValue(0);
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Timeout(20)
    void closeAndTimeoutCanFinalizeAnActiveChildWhileListenerIsBlocked(final boolean close) throws Exception {
        final var blocked = new CountDownLatch(1);
        final var releaseListener = new CountDownLatch(1);
        final var active = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        final var allowBodyReturn = new CountDownLatch(1);
        final var finalized = new CountDownLatch(1);
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                if (event.phase() == TaskPhase.BODY && event.name().equals("marker")) {
                    blocked.countDown();
                    awaitIgnoringInterruption(releaseListener);
                }
            }
        };
        final var definition = Parallel.builder("root", Pool.named("p"))
                .step(
                        "active",
                        () -> {
                            active.countDown();
                            try {
                                allowBodyReturn.await();
                            } catch (InterruptedException e) {
                                interrupted.countDown();
                                throw e;
                            }
                        },
                        settings -> {
                            settings.after(finalized::countDown);
                            if (!close) {
                                settings.timeout(Duration.ofSeconds(2));
                            }
                        })
                .step("marker", () -> active.await(5, TimeUnit.SECONDS))
                .build();
        try (var runner = smallQueueRunner(listener, 10_000)) {
            final var run = startRun(runner, definition);
            try {
                assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();
                if (close) {
                    runner.close();
                }
                assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(finalized.await(5, TimeUnit.SECONDS)).isTrue();
                releaseListener.countDown();
                final var result = run.get(5, TimeUnit.SECONDS);
                assertThat(result.isQuiescent()).isTrue();
                assertThat(result.failures()).isEmpty();
                assertThat(result.status().kind()).isEqualTo(close ? Status.Kind.CANCELED : Status.Kind.FAILED);
                assertThat(result.timedOutCount()).isEqualTo(close ? 0 : 1);
            } finally {
                releaseListener.countDown();
                allowBodyReturn.countDown();
            }
        }
    }

    @Test
    @Timeout(10)
    void completionCallbackIsAlsoBoundedWithoutChangingTaskStatus() throws Exception {
        final var release = new CountDownLatch(1);
        final var completed = new CountDownLatch(1);
        final var listener = new Listener() {
            @Override
            public void onRunCompleted(final Result result) {
                completed.countDown();
                awaitIgnoringInterruption(release);
            }
        };
        try (var runner = smallQueueRunner(listener, 500)) {
            final var run = startRun(
                    runner, Sequential.builder("root").step("pass", () -> {}).build());
            try {
                assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
                final var result = run.get(3, TimeUnit.SECONDS);
                assertThat(result.status().isPassed()).isTrue();
                assertThat(result.isQuiescent()).isTrue();
                assertThat(result.exitCode()).isEqualTo(1);
                assertThat(result.failures())
                        .extracting(Failure::phase)
                        .containsExactly(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC);
            } finally {
                release.countDown();
            }
        }
    }

    private static Runner smallQueueRunner(final Listener listener, final long drainMillis) {
        return Runner.builder()
                .listener(listener)
                .configuration(Configuration.of(Map.of(
                        Configuration.LISTENER_QUEUE_CAPACITY, "1",
                        Configuration.LISTENER_DRAIN_TIMEOUT_MILLIS, Long.toString(drainMillis),
                        Configuration.WORKERS, "2")))
                .pool(Pool.named("p"), 2)
                .build();
    }

    private static FutureTask<Result> startRun(final Runner runner, final Task definition) {
        final var run = new FutureTask<>(() -> runner.run(definition));
        final var caller = new Thread(run);
        caller.setDaemon(true);
        caller.start();
        return run;
    }

    private static void awaitIgnoringInterruption(final CountDownLatch release) {
        for (; ; ) {
            try {
                release.await();
                return;
            } catch (InterruptedException ignored) {
                // Model an uncooperative listener; the test always releases it in finally.
            }
        }
    }

    @Test
    @DisplayName("a listener that drains normally produces no drain diagnostic")
    void normalDrainProducesNoDiagnostic() {
        final var listener = new RecordingListener();

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(Sequential.builder("root").step("work", () -> {}).build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.diagnostics())
                .noneMatch(diagnostic -> diagnostic.contains(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC));
        assertThat(listener.events)
                .anySatisfy(event -> assertThat(event.phase()).isEqualTo(TaskPhase.TERMINAL));
    }

    @Test
    void listenerInterruptDoesNotLoseLaterEvents() {
        final var terminal = new AtomicInteger();
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                if (event.phase() == TaskPhase.ADMITTED) {
                    Thread.currentThread().interrupt();
                } else if (event.phase() == TaskPhase.TERMINAL) {
                    terminal.incrementAndGet();
                }
            }
        };

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(Sequential.builder("root").step("work", () -> {}).build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(terminal).hasValue(result.occurrenceCount());
    }

    @Test
    void fatalListenerErrorReachesTheRunnerEvenWithABoundedQueue() throws Exception {
        final var fatal = new InternalError("simulated fatal listener error");
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                throw fatal;
            }
        };
        final var definition = Sequential.builder("root").step("work", () -> {}).build();
        try (var runner = Runner.builder()
                .listener(listener)
                .configuration(Configuration.of(Map.of(Configuration.LISTENER_QUEUE_CAPACITY, "1")))
                .build()) {
            final var run = new FutureTask<>(() -> runner.run(definition));
            final var caller = new Thread(run);
            caller.setDaemon(true);
            caller.start();
            try {
                assertThatThrownBy(() -> run.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .satisfies(error -> assertThat(error.getCause()).isSameAs(fatal));
            } finally {
                caller.interrupt();
                caller.join(5_000L);
            }
        }
    }

    @Test
    void interruptedFinishStopsDispatchAndInterruptsTheListener() throws Exception {
        final var listening = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var listener = new Listener() {
            @Override
            public void onWarning(final String message) {
                listening.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            }
        };
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .build()
                .run(Sequential.builder("root").build());
        try (var dispatcher = new ReportingDispatcher(listener, Configuration.of(Map.of()))) {
            dispatcher.warning("waiting listener");
            assertThat(listening.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> dispatcher.finish(result)).isInstanceOf(InterruptedException.class);
            } finally {
                Thread.interrupted();
            }
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    /**
     * Busy-waits for the supplied duration while deliberately ignoring interruption, to force a non-quiescent run.
     */
    private static void ignoreInterruptionFor(final Duration duration) {
        final var deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ignored) {
                // Deliberately uncooperative.
            }
        }
    }

    /**
     * Records every event.
     */
    private static final class RecordingListener implements Listener {

        private final List<TaskEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onTaskEvent(final TaskEvent event) {
            events.add(event);
            threadNames.add(Thread.currentThread().getName());
        }

        @Override
        public void onDiscoveryCompleted(final Optional<Task> root) {
            // Intentionally empty
        }
    }
}
