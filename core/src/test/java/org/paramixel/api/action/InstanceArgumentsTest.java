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

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Instance arguments")
class InstanceArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Instance.builder(null, (Supplier<?>) () -> ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.builder(" ", (Supplier<?>) () -> ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Instance.builder("instance", (Supplier<?>) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.builder(null, String.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.builder(" ", String.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Instance.builder("instance", (Class<?>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder(Class<T>) rejects class without public no-arg constructor")
    void builderClassRejectsNoPublicConstructor() {
        assertThatThrownBy(() -> Instance.builder(Integer.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("builder(Class<T>) uses simple name as action name")
    void builderClassUsesSimpleName() {
        var instance =
                Instance.builder(String.class).body(Step.of("test", s -> {})).build();
        assertThat(instance.displayName()).isEqualTo("String");
    }

    @Test
    @DisplayName("wrap(Action) rejects null builder")
    void wrapBuilderRejectsNull() {
        var builder = Instance.builder("instance", Object::new);
        assertThatThrownBy(() -> builder.body((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wrap(Action) sets child")
    void wrapBuilderSetsBody() {
        var instance = Instance.builder("instance", Object::new)
                .body(Step.of("step", s -> {}))
                .build();
        assertThat(instance.body()).isNotNull();
        assertThat(instance.body().displayName()).isEqualTo("step");
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
        assertThatThrownBy(() -> Step.of("child", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var instance = Instance.builder("instance", Object::new)
                .body(Sequence.builder("body")
                        .child(Step.of("first", s -> {}))
                        .child(Step.of("second", s -> {}))
                        .child(Step.of("third", s -> {}))
                        .build())
                .build();
        assertThat(instance.body()).isNotNull();
        assertThat(instance.body().displayName()).isEqualTo("body");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Instance.builder("instance", Object::new);
        builder.body(Step.of("test", s -> {}));
        var first = builder.build();
        builder.body(Step.of("other", s -> {}));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(second.body().displayName()).isEqualTo("other");
    }
}
