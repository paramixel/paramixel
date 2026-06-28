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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.FailException;

@DisplayName("Ordered action execution semantics")
@SuppressWarnings("removal")
class OrderedActionExecutionSemanticsTest {

    @Test
    @DisplayName("Sequence children run in descriptor order without overlap")
    void sequenceChildrenRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var action = Sequence.builder("sequence")
                .independent()
                .child(probe.step("one", 1))
                .child(probe.step("two", 2))
                .child(probe.step("three", 3))
                .child(probe.step("four", 4))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Sequential independent children still run in descriptor order without overlap")
    void sequentialIndependentChildrenRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var action = Sequential.builder("sequential")
                .independent()
                .child(probe.step("one", 1))
                .child(probe.step("two", 2))
                .child(probe.step("three", 3))
                .child(probe.step("four", 4))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Repeat iterations run in descriptor order without overlap")
    void repeatIterationsRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var iteration = new AtomicInteger();
        var action = Repeat.builder("repeat")
                .body(Step.of("body", context -> probe.record(iteration.incrementAndGet())))
                .iterations(4)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Loop iterations run in descriptor order without overlap")
    void loopIterationsRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var iteration = new AtomicInteger();
        var action = Loop.builder("loop")
                .body(Step.of("body", context -> probe.record(iteration.incrementAndGet())))
                .maxIterations(4)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Until attempts run in descriptor order without overlap")
    void untilAttemptsRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var iteration = new AtomicInteger();
        var action = Until.builder("until")
                .body(Step.of("body", context -> {
                    var current = iteration.incrementAndGet();
                    probe.record(current);
                    if (current < 4) {
                        FailException.fail("not yet");
                    }
                }))
                .maxIterations(4)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Scope runs before, full body, then after in order")
    void scopeRunsBeforeBodyAfterInOrder() {
        var order = Collections.synchronizedList(new ArrayList<String>());
        var body = Sequential.builder("body")
                .independent()
                .child(Step.of("body-1", context -> order.add("body-1")))
                .child(Step.of("body-2", context -> order.add("body-2")))
                .build();
        var action = Scope.builder("scope")
                .before(Step.of("before", context -> order.add("before")))
                .body(body)
                .after(Step.of("after", context -> order.add("after")))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(order).containsExactly("before", "body-1", "body-2", "after");
    }

    @Test
    @DisplayName("Scope before failure contributes to aggregate status")
    void scopeBeforeFailureContributesToAggregateStatus() {
        var body = Sequential.builder("body")
                .independent()
                .child(Step.of("body-1", context -> {}))
                .build();
        var action = Scope.builder("scope")
                .before(Step.of("before", context -> {
                    throw new org.paramixel.api.exception.FailException("before failed");
                }))
                .body(body)
                .after(Step.of("after", context -> {}))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed())
                .as("aggregate must be FAILED when before fails, not SKIPPED")
                .isTrue();
    }

    void loopEarlySatisfactionSkipsRemainingCompositeDescendants() {
        var iteration = new AtomicInteger();
        var body = Sequential.builder("body")
                .independent()
                .child(Step.of("leaf-1", context -> iteration.incrementAndGet()))
                .child(Step.of("leaf-2", context -> {}))
                .build();
        var action = Loop.builder("loop")
                .body(body)
                .until(context -> iteration.get() >= 1)
                .maxIterations(3)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(3);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertCompositeSkipped(root.children().get(1));
        assertCompositeSkipped(root.children().get(2));
    }

    @Test
    @DisplayName("Until early satisfaction skips remaining composite descendants")
    void untilEarlySatisfactionSkipsRemainingCompositeDescendants() {
        var body = Sequential.builder("body")
                .independent()
                .child(Step.of("leaf-1", context -> {}))
                .child(Step.of("leaf-2", context -> {}))
                .build();
        var action = Until.builder("until").body(body).maxIterations(3).build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(3);
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertCompositeSkipped(root.children().get(1));
        assertCompositeSkipped(root.children().get(2));
    }

    @Test
    @DisplayName("Scope before failure skips body composite descendants and still runs after")
    void scopeBeforeFailureSkipsBodyCompositeDescendantsAndStillRunsAfter() {
        var afterRan = new AtomicInteger();
        var body = Sequential.builder("body")
                .independent()
                .child(Step.of("leaf-1", context -> {}))
                .child(Step.of("leaf-2", context -> {}))
                .build();
        var action = Scope.builder("scope")
                .before(Step.of("before", context -> FailException.fail("before failed")))
                .body(body)
                .after(Step.of("after", context -> afterRan.incrementAndGet()))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before()).get().matches(before -> before.isFailed(), "before failed");
        assertCompositeSkipped(root.children().get(0));
        assertThat(root.after()).get().matches(after -> after.isPassed(), "after passed");
        assertThat(afterRan.get()).isEqualTo(1);
    }

    private static Runner runner() {
        return Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
                .build();
    }

    private static void assertCompositeSkipped(final org.paramixel.api.Descriptor descriptor) {
        assertThat(descriptor.isSkipped()).isTrue();
        for (var child : descriptor.children()) {
            assertThat(child.isSkipped()).isTrue();
        }
    }

    private static final class ConcurrencyProbe {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final java.util.List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        Step step(final String name, final int value) {
            return Step.of(name, context -> record(value));
        }

        void record(final int value) throws InterruptedException {
            var current = inFlight.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            order.add(value);
            try {
                Thread.sleep(25);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        void assertSequentialOrder(final Integer... expectedOrder) {
            assertThat(order).containsExactly(expectedOrder);
            assertThat(maxConcurrent.get()).isEqualTo(1);
        }
    }
}
