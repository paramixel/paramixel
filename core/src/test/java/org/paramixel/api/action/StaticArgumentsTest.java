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

@DisplayName("Static arguments")
class StaticArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Static.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Static.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Static.builder("empty").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("before(Action) rejects null builder")
    void beforeBuilderRejectsNull() {
        var builder = Static.builder("static");
        assertThatThrownBy(() -> builder.before((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(Action) rejects null builder")
    void afterBuilderRejectsNull() {
        var builder = Static.builder("static");
        assertThatThrownBy(() -> builder.after((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wrap(Action) rejects null builder")
    void wrapBuilderRejectsNull() {
        var builder = Static.builder("static");
        assertThatThrownBy(() -> builder.body((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wrap(Action) sets child")
    void wrapBuilderSetsBody() {
        var action =
                Static.builder("static").body(Step.of("step", context -> {})).build();
        assertThat(action.body()).isNotNull();
        assertThat(action.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var action = Static.builder("static")
                .body(Sequence.builder("body")
                        .child(Step.of("first", context -> {}))
                        .child(Step.of("second", context -> {}))
                        .child(Step.of("third", context -> {}))
                        .build())
                .build();
        assertThat(action.body()).isNotNull();
        assertThat(action.body().displayName()).isEqualTo("body");
    }

    @Test
    @DisplayName("before(Action) after build affects future snapshots")
    void beforeAfterBuildAffectsFutureSnapshots() {
        var builder = Static.builder("static");
        builder.body(Step.of("test", context -> {}));
        var first = builder.build();
        builder.before(Step.of("before", context -> {}));
        var second = builder.build();

        assertThat(first.before()).isEmpty();
        assertThat(second.before()).isPresent();
        assertThat(second.before().get().displayName()).isEqualTo("before");
    }

    @Test
    @DisplayName("after(Action) after build affects future snapshots")
    void afterAfterBuildAffectsFutureSnapshots() {
        var builder = Static.builder("static");
        builder.body(Step.of("test", context -> {}));
        var first = builder.build();
        builder.after(Step.of("after", context -> {}));
        var second = builder.build();

        assertThat(first.after()).isEmpty();
        assertThat(second.after()).isPresent();
        assertThat(second.after().get().displayName()).isEqualTo("after");
    }

    @Test
    @DisplayName("wrap(Action) after build affects future snapshots")
    void wrapAfterBuildAffectsFutureSnapshots() {
        var builder = Static.builder("static");
        builder.body(Step.of("test", context -> {}));
        var first = builder.build();
        builder.body(Step.of("other", context -> {}));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(second.body().displayName()).isEqualTo("other");
    }

    @Test
    @DisplayName("before(Action) overwrites previous before")
    void beforeBuilderOverwritesBefore() {
        var builder = Static.builder("static");
        builder.before(Step.of("first", context -> {}));
        builder.before(Step.of("second", context -> {}));
        var action = builder.body(Step.of("test", context -> {})).build();
        assertThat(action).isNotNull();
        assertThat(action.before()).isPresent();
        assertThat(action.before().get().displayName()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(Action) overwrites previous after")
    void afterBuilderOverwritesAfter() {
        var builder = Static.builder("static");
        builder.after(Step.of("first", context -> {}));
        builder.after(Step.of("second", context -> {}));
        var action = builder.body(Step.of("test", context -> {})).build();
        assertThat(action).isNotNull();
        assertThat(action.after()).isPresent();
        assertThat(action.after().get().displayName()).isEqualTo("second");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Static.builder("static");
        builder.body(Step.of("test", context -> {}));
        var first = builder.build();
        builder.body(Step.of("other", context -> {}));
        builder.before(Step.of("before", context -> {}));
        builder.after(Step.of("after", context -> {}));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(first.before()).isEmpty();
        assertThat(first.after()).isEmpty();
        assertThat(second.body().displayName()).isEqualTo("other");
        assertThat(second.before()).isPresent();
        assertThat(second.after()).isPresent();
    }
}
