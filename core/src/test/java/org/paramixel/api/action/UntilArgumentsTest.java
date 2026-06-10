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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Until arguments")
class UntilArgumentsTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Until.builder(null).maxIterations(1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Until.builder(" ").maxIterations(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Until.builder("until").maxIterations(1).body((Action) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("bodyAcceptsAction()")
    void bodyAcceptsAction() {
        var until = Until.builder("until").body(STEP).maxIterations(1).build();
        assertThat(until.body()).isNotNull();
        assertThat(until.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("maxIterations() rejects zero")
    void maxIterationsRejectsZero() {
        var builder = Until.builder("until").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() rejects negative")
    void maxIterationsRejectsNegative() {
        var builder = Until.builder("until").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() sets value")
    void maxIterationsSetsValue() {
        var until = Until.builder("until").body(STEP).maxIterations(5).build();
        assertThat(until.maxIterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("until() rejects null")
    void untilRejectsNull() {
        var builder = Until.builder("until").body(STEP);
        assertThatThrownBy(() -> builder.until(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate is null");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Until.builder("until").body(STEP);
        var first = builder.maxIterations(1).build();
        builder.maxIterations(3);
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("step");
        assertThat(first.maxIterations()).isEqualTo(1);
        assertThat(second.body().displayName()).isEqualTo("step");
        assertThat(second.maxIterations()).isEqualTo(3);
    }
}
