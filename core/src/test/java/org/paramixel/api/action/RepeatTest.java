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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.FailException;

@DisplayName("Repeat action")
@SuppressWarnings("removal")
class RepeatTest {

    @Test
    @DisplayName("all repetitions pass")
    void allRepetitionsPass() {
        var counter = new AtomicInteger();
        var action = Repeat.builder("dependent-all-pass")
                .body(Step.of("step", context -> counter.incrementAndGet()))
                .iterations(3)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("second repetition fails, remaining still run")
    void secondRepetitionFailsRemainingStillRun() {
        var counter = new AtomicInteger();
        var action = Repeat.builder("second-fails")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        FailException.fail("intentional failure");
                    }
                }))
                .iterations(3)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("single repetition (count=1) passes")
    void singleRepetitionPasses() {
        var action = Repeat.builder("single")
                .body(Step.of("step", context -> {}))
                .iterations(1)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
    }

    @Test
    @DisplayName("getName returns the supplied name")
    void getNameReturnsSuppliedName() {
        var action =
                Repeat.builder("my-repeat").body(Step.of("step", context -> {})).build();

        assertThat(action.displayName()).isEqualTo("my-repeat");
    }

    @Test
    @DisplayName("accessors return expected values")
    void accessorsReturnExpectedValues() {
        var child = Step.of("step", context -> {});
        var action = Repeat.builder("repeat").body(child).iterations(5).build();

        assertThat(action.body()).isSameAs(child);
        assertThat(action.iterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("descriptor tree has N children for N repetitions")
    void descriptorTreeHasNChildren() {
        var action = Repeat.builder("repeat-tree")
                .body(Step.of("step", context -> {}))
                .iterations(4)
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(4);
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("build throws when child not configured")
        void buildThrowsWhenChildNotConfigured() {
            assertThatThrownBy(() -> Repeat.builder("bad-repeat").build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("child action must be configured");
        }

        @Test
        @DisplayName("body accepts another Builder")
        void bodyAcceptsAnotherBuilder() {
            var child = Step.of("inner-step", context -> {});
            var repeat = Repeat.builder("from-builder")
                    .body(Instance.builder("inner", child::getClass).body(child))
                    .iterations(2)
                    .build();

            assertThat(repeat.displayName()).isEqualTo("from-builder");
            assertThat(repeat.iterations()).isEqualTo(2);
            assertThat(repeat.body()).isNotNull();
        }
    }
}
