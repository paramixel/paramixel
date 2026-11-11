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
class OrderedActionExecutionSemanticsTest {

    @Test
    @DisplayName("Sequential independent children still run in descriptor order without overlap")
    void sequentialIndependentChildrenRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var action = Sequential.sequential("sequential")
                .independent()
                .child(probe.step("one", 1))
                .child(probe.step("two", 2))
                .child(probe.step("three", 3))
                .child(probe.step("four", 4))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Loop iterations run in descriptor order without overlap")
    void loopIterationsRunInDescriptorOrderWithoutOverlap() {
        var probe = new ConcurrencyProbe();
        var iteration = new AtomicInteger();
        var action = Loop.loop("loop")
                .body(Step.step("body", context -> probe.record(iteration.incrementAndGet())))
                .maxIterations(4)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        probe.assertSequentialOrder(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Scope runs before, full body, then after in order")
    void scopeRunsBeforeBodyAfterInOrder() {
        var order = Collections.synchronizedList(new ArrayList<String>());
        var body = Sequential.sequential("body")
                .independent()
                .child(Step.step("body-1", context -> order.add("body-1")))
                .child(Step.step("body-2", context -> order.add("body-2")))
                .build();
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> order.add("before")))
                .body(body)
                .after(Step.step("after", context -> order.add("after")))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(order).containsExactly("before", "body-1", "body-2", "after");
    }

    @Test
    @DisplayName("Scope before failure contributes to aggregate status")
    void scopeBeforeFailureContributesToAggregateStatus() {
        var body = Sequential.sequential("body")
                .independent()
                .child(Step.step("body-1", context -> {}))
                .build();
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw new org.paramixel.api.exception.FailException("before failed");
                }))
                .body(body)
                .after(Step.step("after", context -> {}))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed())
                .as("aggregate must be FAILED when before fails, not SKIPPED")
                .isTrue();
    }

    @Test
    @DisplayName("Loop early satisfaction skips remaining composite descendants")
    void loopEarlySatisfactionSkipsRemainingCompositeDescendants() {
        var iteration = new AtomicInteger();
        var body = Sequential.sequential("body")
                .independent()
                .child(Step.step("leaf-1", context -> iteration.incrementAndGet()))
                .child(Step.step("leaf-2", context -> {}))
                .build();
        var action = Loop.loop("loop")
                .body(body)
                .until(context -> iteration.get() >= 1)
                .maxIterations(3)
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(3);
        assertThat(root.children().get(0).status().isPassed()).isTrue();
        assertCompositeSkipped(root.children().get(1));
        assertCompositeSkipped(root.children().get(2));
    }

    @Test
    @DisplayName("Scope before failure skips body composite descendants and still runs after")
    void scopeBeforeFailureSkipsBodyCompositeDescendantsAndStillRunsAfter() {
        var afterRan = new AtomicInteger();
        var body = Sequential.sequential("body")
                .independent()
                .child(Step.step("leaf-1", context -> {}))
                .child(Step.step("leaf-2", context -> {}))
                .build();
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw FailException.fail("before failed");
                }))
                .body(body)
                .after(Step.step("after", context -> afterRan.incrementAndGet()))
                .build();

        var root = runner().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before()).get().matches(before -> before.status().isFailed(), "before failed");
        assertCompositeSkipped(root.children().get(0));
        assertThat(root.after()).get().matches(after -> after.status().isPassed(), "after passed");
        assertThat(afterRan.get()).isEqualTo(1);
    }

    private static Runner runner() {
        return Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
                .build();
    }

    private static void assertCompositeSkipped(final org.paramixel.api.Descriptor descriptor) {
        assertThat(descriptor.status().isSkipped()).isTrue();
        for (var child : descriptor.children()) {
            assertThat(child.status().isSkipped()).isTrue();
        }
    }

    private static final class ConcurrencyProbe {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final java.util.List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        Step step(final String name, final int value) {
            return Step.step(name, context -> record(value));
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
