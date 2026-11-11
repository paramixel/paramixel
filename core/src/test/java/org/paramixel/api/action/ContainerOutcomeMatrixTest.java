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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

/**
 * Pins the table in the {@link Action} javadoc: how a child's outcome affects the rest of its container.
 */
@DisplayName("Container outcome matrix")
class ContainerOutcomeMatrixTest {

    enum Outcome {
        FAILED,
        SKIPPED,
        ABORTED
    }

    private static Action outcomeStep(final String name, final Outcome outcome) {
        return Step.step(name, context -> {
            switch (outcome) {
                case FAILED -> {
                    throw FailException.fail("failed");
                }
                case SKIPPED -> {
                    throw SkipException.skip("skipped");
                }
                case ABORTED -> {
                    throw AbortException.abort("aborted");
                }
            }
        });
    }

    private static List<Descriptor> run(final Action action) {
        var root = Runner.builder().build().run(action).descriptor().orElseThrow();
        return new ArrayList<>(root.children());
    }

    private static Status statusOf(final Descriptor descriptor) {
        return descriptor.status();
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    @DisplayName("dependent Sequential: FAILED and SKIPPED skip the rest, ABORTED does not")
    void dependentSequential(final Outcome outcome) {
        var tail = new AtomicInteger();
        var children = run(Sequential.sequential("seq")
                .child(outcomeStep("first", outcome))
                .child(Step.step("second", context -> tail.incrementAndGet()))
                .child(Step.step("third", context -> tail.incrementAndGet()))
                .build());

        if (outcome == Outcome.ABORTED) {
            assertThat(statusOf(children.get(1))).isSameAs(Status.PASSED);
            assertThat(statusOf(children.get(2))).isSameAs(Status.PASSED);
            assertThat(tail.get()).isEqualTo(2);
        } else {
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(tail.get()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    @DisplayName("independent Sequential: every child runs")
    void independentSequential(final Outcome outcome) {
        var tail = new AtomicInteger();
        var children = run(Sequential.sequential("seq")
                .independent()
                .child(outcomeStep("first", outcome))
                .child(Step.step("second", context -> tail.incrementAndGet()))
                .child(Step.step("third", context -> tail.incrementAndGet()))
                .build());

        assertThat(children.get(1).status().isPassed()).isTrue();
        assertThat(children.get(2).status().isPassed()).isTrue();
        assertThat(tail.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    @DisplayName("Parallel: every child runs")
    void parallel(final Outcome outcome) {
        var tail = new AtomicInteger();
        var children = run(Parallel.parallel("par", "t0")
                .child(outcomeStep("first", outcome))
                .child(Step.step("second", context -> tail.incrementAndGet()))
                .child(Step.step("third", context -> tail.incrementAndGet()))
                .build());

        assertThat(children.get(1).status().isPassed()).isTrue();
        assertThat(children.get(2).status().isPassed()).isTrue();
        assertThat(tail.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    @DisplayName("Loop: ABORTED stops the loop, FAILED and SKIPPED do not")
    void loop(final Outcome outcome) {
        var iteration = new AtomicInteger();
        var root = Runner.builder()
                .build()
                .run(Loop.loop("loop")
                        .maxIterations(3)
                        .body(Step.step("body", context -> {
                            if (iteration.incrementAndGet() == 1) {
                                switch (outcome) {
                                    case FAILED -> {
                                        throw FailException.fail("failed");
                                    }
                                    case SKIPPED -> {
                                        throw SkipException.skip("skipped");
                                    }
                                    case ABORTED -> {
                                        throw AbortException.abort("aborted");
                                    }
                                }
                            }
                        }))
                        .build())
                .descriptor()
                .orElseThrow();
        var children = root.children();

        if (outcome == Outcome.ABORTED) {
            assertThat(iteration.get()).isEqualTo(1);
            assertThat(children.get(1).status().isSkipped()).isTrue();
            assertThat(children.get(2).status().isSkipped()).isTrue();
            assertThat(root.status().isAborted()).isTrue();
        } else {
            assertThat(iteration.get()).isEqualTo(3);
            assertThat(children.get(1).status().isPassed()).isTrue();
            assertThat(children.get(2).status().isPassed()).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    @DisplayName("Scope: a non-passing before skips the body, and the after still runs")
    void scopeBefore(final Outcome outcome) {
        var bodyRan = new AtomicInteger();
        var afterRan = new AtomicInteger();
        var root = Runner.builder()
                .build()
                .run(Scope.scope("scope")
                        .before(outcomeStep("before", outcome))
                        .body(Step.step("body", context -> bodyRan.incrementAndGet()))
                        .after(Step.step("after", context -> afterRan.incrementAndGet()))
                        .build())
                .descriptor()
                .orElseThrow();

        assertThat(bodyRan.get()).isZero();
        assertThat(afterRan.get()).isEqualTo(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
    }
}
