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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.task;

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

/**
 * Listener contract: event ordering, exactly one terminal event per occurrence, and error isolation.
 */
class ListenerTest {

    @Test
    @DisplayName("each occurrence produces exactly one terminal event, delivered in order")
    void terminalEventsAreExactlyOnce() {
        final var listener = new RecordingListener();
        final var definition = parallel("root", Pool.named("p"))
                .each(List.of("a", "b", "c"), name -> task(name).step("work", () -> {}))
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
        final var definition = task("root")
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
                .run(task("root").step("work", () -> {}).build());

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

        final var result =
                Runner.builder().listener(listener).build().run(task("empty").build());

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
                .run(task("root").step("work", () -> {}).build());

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
        final Task definition = task("root")
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
    @DisplayName("a listener that does not drain in time is reported, and no event is delivered after onRunCompleted")
    void blockedListenerDrainIsBoundedAndReported() throws InterruptedException {
        final var blocked = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var dispatcher = new AtomicReference<Thread>();
        final var completionStarted = new AtomicBoolean();
        final var eventsAfterCompletion = new AtomicInteger();
        final var diagnosticsAtCompletion = new AtomicReference<List<String>>();
        final var listener = new Listener() {
            @Override
            public void onTaskEvent(final TaskEvent event) {
                if (completionStarted.get()) {
                    eventsAfterCompletion.incrementAndGet();
                }
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
                diagnosticsAtCompletion.set(result.diagnostics());
            }
        };
        final Task definition = task("root")
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
        assertThat(diagnosticsAtCompletion.get())
                .anySatisfy(
                        diagnostic -> assertThat(diagnostic).contains(ReportingDispatcher.DRAIN_TIMEOUT_DIAGNOSTIC));
        assertThat(eventsAfterCompletion).hasValue(0);
    }

    @Test
    @DisplayName("a listener that drains normally produces no drain diagnostic")
    void normalDrainProducesNoDiagnostic() {
        final var listener = new RecordingListener();

        final var result = Runner.builder()
                .listener(listener)
                .build()
                .run(task("root").step("work", () -> {}).build());

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
                .run(task("root").step("work", () -> {}).build());

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
        final var definition = task("root").step("work", () -> {}).build();
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
                .run(task("root").build());
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
