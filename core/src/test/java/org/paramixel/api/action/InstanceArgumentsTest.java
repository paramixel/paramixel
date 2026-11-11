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

@DisplayName("Instance arguments")
class InstanceArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Instance.of(null, String.class, () -> "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.of(" ", String.class, () -> "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Instance.of("instance", null, () -> "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.of("instance", String.class, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.of(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.of(" ", String.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Instance.of("instance", (Class<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder(Class<T>) rejects class without public no-arg constructor")
    void builderClassRejectsNoPublicConstructor() {
        assertThatThrownBy(() -> Instance.of(Integer.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("builder(Class<T>) uses simple name as action name")
    void builderClassUsesSimpleName() {
        var instance =
                Instance.of(String.class).body(Step.step("test", s -> {})).build();
        assertThat(instance.displayName()).isEqualTo("String");
    }

    @Test
    @DisplayName("wrap(Action) rejects null builder")
    void wrapBuilderRejectsNull() {
        var builder = Instance.of("instance", Object.class, Object::new);
        assertThatThrownBy(() -> builder.body((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wrap(Action) sets child")
    void wrapBuilderSetsBody() {
        var instance = Instance.of("instance", Object.class, Object::new)
                .body(Step.step("step", s -> {}))
                .build();
        assertThat(instance.body()).isNotNull();
        assertThat(instance.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("Step.step() rejects null name")
    void stepOfRejectsNullName() {
        assertThatThrownBy(() -> Step.step(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Step.step() rejects blank name")
    void stepOfRejectsBlankName() {
        assertThatThrownBy(() -> Step.step(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Step.step() rejects null consumer")
    void stepOfRejectsNullConsumer() {
        assertThatThrownBy(() -> Step.step("child", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var instance = Instance.of("instance", Object.class, Object::new)
                .body(Sequential.sequential("body")
                        .child(Step.step("first", s -> {}))
                        .child(Step.step("second", s -> {}))
                        .child(Step.step("third", s -> {}))
                        .build())
                .build();
        assertThat(instance.body()).isNotNull();
        assertThat(instance.body().displayName()).isEqualTo("body");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Instance.of("instance", Object.class, Object::new);
        builder.body(Step.step("test", s -> {}));
        var first = builder.build();
        builder.body(Step.step("other", s -> {}));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(second.body().displayName()).isEqualTo("other");
    }
}
