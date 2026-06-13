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

@DisplayName("Isolated arguments")
@SuppressWarnings("removal")
class IsolatedArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Isolated.builder(null, "lock")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Isolated.builder(" ", "lock")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isolated.builder("isolated", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Isolated.builder("isolated", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Isolated.builder("isolated", "lock").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("body(Action) rejects null action")
    void bodyActionRejectsNull() {
        var builder = Isolated.builder("isolated", "lock");
        assertThatThrownBy(() -> builder.body((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("body(Builder) builds and sets body action")
    void bodyBuilderBuildsAndSetsBodyAction() {
        var isolated = Isolated.builder("isolated", "lock")
                .body(Sequence.builder("body-seq").child(Step.of("step", context -> {})))
                .build();
        assertThat(isolated.body()).isNotNull();
        assertThat(isolated.body().displayName()).isEqualTo("body-seq");
    }

    @Test
    @DisplayName("body(Builder) rejects null builder")
    void bodyBuilderOverloadRejectsNull() {
        var builder = Isolated.builder("isolated", "lock");
        assertThatThrownBy(() -> builder.body((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Isolated.builder("isolated", "lock");
        builder.body(Step.of("test", s -> {}));
        var first = builder.build();
        builder.body(Step.of("other", s -> {}));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(second.body().displayName()).isEqualTo("other");
    }
}
