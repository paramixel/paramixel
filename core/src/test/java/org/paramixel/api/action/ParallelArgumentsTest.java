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

@DisplayName("Parallel arguments")
class ParallelArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Parallel.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Parallel.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        var parallel = Parallel.builder("empty").build();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Action) rejects null builder")
    void childBuilderRejectsNull() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.child((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Action) adds child from builder")
    void childBuilderAddsChild() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("step", s -> {})).build();
        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var parallel = Parallel.builder("parallel")
                .child(Step.of("first", s -> {}))
                .child(Step.of("second", s -> {}))
                .child(Step.of("third", s -> {}))
                .build();
        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("first");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("second");
        assertThat(parallel.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("parallelism() rejects zero")
    void parallelismRejectsZero() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.parallelism(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() rejects negative")
    void parallelismRejectsNegative() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.parallelism(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() sets parallelism")
    void parallelismSetsParallelism() {
        var parallel = Parallel.builder("parallel")
                .parallelism(4)
                .child(Step.of("step", s -> {}))
                .build();
        assertThat(parallel.parallelism()).isEqualTo(4);
    }

    @Test
    @DisplayName("default parallelism is Integer.MAX_VALUE")
    void defaultParallelismIsMaxValue() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("step", s -> {})).build();
        assertThat(parallel.parallelism()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Parallel.builder("parallel");
        builder.child(Step.of("test", s -> {}));
        var first = builder.build();
        builder.child(Step.of("other", s -> {}));
        builder.parallelism(2);
        var second = builder.build();

        assertThat(first.children()).extracting(Action::displayName).containsExactly("test");
        assertThat(first.parallelism()).isEqualTo(Integer.MAX_VALUE);
        assertThat(second.children()).extracting(Action::displayName).containsExactly("test", "other");
        assertThat(second.parallelism()).isEqualTo(2);
    }

    @Test
    @DisplayName("Parallel does not have dependent/independent")
    void parallelHasNoDependentIndependent() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("test", s -> {})).build();
        assertThat(parallel.getClass().getDeclaredFields())
                .anyMatch(f -> !f.getName().equals("dependent"));
    }
}
