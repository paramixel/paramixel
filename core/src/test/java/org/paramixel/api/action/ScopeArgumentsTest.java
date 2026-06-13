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

@DisplayName("Scope arguments")
@SuppressWarnings("removal")
class ScopeArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Scope.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Scope.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Scope.builder("empty").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("before(Action) rejects null builder")
    void beforeBuilderRejectsNull() {
        var builder = Scope.builder("scope");
        assertThatThrownBy(() -> builder.before((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Step.of() rejects null name")
    void stepOfRejectsNullName() {
        assertThatThrownBy(() -> Step.of(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Step.of() rejects blank name")
    void stepOfRejectsBlankName() {
        assertThatThrownBy(() -> Step.of(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Step.of() rejects null consumer")
    void stepOfRejectsNullConsumer() {
        assertThatThrownBy(() -> Step.of("before", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(Action) rejects null builder")
    void afterBuilderRejectsNull() {
        var builder = Scope.builder("scope");
        assertThatThrownBy(() -> builder.after((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("before(Action) after build affects future snapshots")
    void beforeAfterBuildAffectsFutureSnapshots() {
        var builder = Scope.builder("scope");
        builder.body(Step.of("body", s -> {}));
        builder.before(Step.of("test", s -> {}));
        var first = builder.build();
        builder.before(Step.of("before", s -> {}));
        var second = builder.build();

        assertThat(first.before()).isPresent();
        assertThat(first.before().get().displayName()).isEqualTo("test");
        assertThat(second.before()).isPresent();
        assertThat(second.before().get().displayName()).isEqualTo("before");
    }

    @Test
    @DisplayName("after(Action) after build affects future snapshots")
    void afterAfterBuildAffectsFutureSnapshots() {
        var builder = Scope.builder("scope");
        builder.body(Step.of("body", s -> {}));
        builder.after(Step.of("test", s -> {}));
        var first = builder.build();
        builder.after(Step.of("after", s -> {}));
        var second = builder.build();

        assertThat(first.after()).isPresent();
        assertThat(first.after().get().displayName()).isEqualTo("test");
        assertThat(second.after()).isPresent();
        assertThat(second.after().get().displayName()).isEqualTo("after");
    }

    @Test
    @DisplayName("before(Action) overwrites previous before")
    void beforeBuilderOverwritesBefore() {
        var builder = Scope.builder("scope");
        builder.body(Step.of("body", s -> {}));
        builder.before(Step.of("first", s -> {}));
        builder.before(Step.of("second", s -> {}));
        var scope = builder.after(Step.of("after", s -> {})).build();
        assertThat(scope.before()).isPresent();
        assertThat(scope.before().get().displayName()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(Action) overwrites previous after")
    void afterBuilderOverwritesAfter() {
        var builder = Scope.builder("scope");
        builder.body(Step.of("body", s -> {}));
        builder.after(Step.of("first", s -> {}));
        builder.after(Step.of("second", s -> {}));
        var scope = builder.before(Step.of("before", s -> {})).build();
        assertThat(scope.after()).isPresent();
        assertThat(scope.after().get().displayName()).isEqualTo("second");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var action = Scope.builder("scope");
        action.body(Step.of("body", s -> {}));
        action.before(Step.of("test", s -> {}));
        var first = action.build();
        action.body(Step.of("child", s -> {}));
        action.after(Step.of("after", s -> {}));
        var second = action.build();

        assertThat(first.body()).isNotNull();
        assertThat(first.after()).isEmpty();
        assertThat(second.body()).isNotNull();
        assertThat(second.after()).isPresent();
        assertThat(second.after().get().displayName()).isEqualTo("after");
    }

    @Test
    @DisplayName("before(Builder) builds and sets before action")
    void beforeBuilderBuildsAndSetsBeforeAction() {
        var action = Scope.builder("scope")
                .before(Sequence.builder("before-seq").child(Step.of("step", context -> {})))
                .body(Step.of("body", context -> {}))
                .build();
        assertThat(action.before()).isPresent();
        assertThat(action.before().get().displayName()).isEqualTo("before-seq");
    }

    @Test
    @DisplayName("body(Builder) builds and sets body action")
    void bodyBuilderBuildsAndSetsBodyAction() {
        var action = Scope.builder("scope")
                .body(Sequence.builder("body-seq").child(Step.of("step", context -> {})))
                .build();
        assertThat(action.body()).isNotNull();
        assertThat(action.body().displayName()).isEqualTo("body-seq");
    }

    @Test
    @DisplayName("after(Builder) builds and sets after action")
    void afterBuilderBuildsAndSetsAfterAction() {
        var action = Scope.builder("scope")
                .body(Step.of("body", context -> {}))
                .after(Sequence.builder("after-seq").child(Step.of("step", context -> {})))
                .build();
        assertThat(action.after()).isPresent();
        assertThat(action.after().get().displayName()).isEqualTo("after-seq");
    }

    @Test
    @DisplayName("before(Builder) rejects null builder")
    void beforeBuilderOverloadRejectsNull() {
        var builder = Scope.builder("scope");
        assertThatThrownBy(() -> builder.before((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("body(Builder) rejects null builder")
    void bodyBuilderOverloadRejectsNull() {
        var builder = Scope.builder("scope");
        assertThatThrownBy(() -> builder.body((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(Builder) rejects null builder")
    void afterBuilderOverloadRejectsNull() {
        var builder = Scope.builder("scope");
        assertThatThrownBy(() -> builder.after((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }
}
