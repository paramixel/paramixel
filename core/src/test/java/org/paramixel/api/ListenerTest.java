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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
