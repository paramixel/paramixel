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

@DisplayName("Step.kind")
class StepKindTest {

    @Test
    @DisplayName("default kind is Step")
    void defaultKindIsStep() {
        Step<?> step = Step.of("test", obj -> {});

        assertThat(step.kind()).isEqualTo("Step");
    }

    @Test
    @DisplayName("of with kind creates step with custom kind")
    void ofWithKindCreatesStepWithCustomKind() {
        Step<?> step = Step.of("setUp()", "Before", ctx -> {});

        assertThat(step.name()).isEqualTo("setUp()");
        assertThat(step.kind()).isEqualTo("Before");
    }

    @Test
    @DisplayName("of with kind rejects null kind")
    void ofWithKindRejectsNullKind() {
        assertThatThrownBy(() -> Step.of("test", (String) null, obj -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind must not be null");
    }

    @Test
    @DisplayName("of with kind rejects blank kind")
    void ofWithKindRejectsBlankKind() {
        assertThatThrownBy(() -> Step.of("test", "   ", obj -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kind must not be blank");
    }

    @Test
    @DisplayName("of with kind rejects null name")
    void ofWithKindRejectsNullName() {
        assertThatThrownBy(() -> Step.of(null, "Before", obj -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    @DisplayName("of with kind rejects blank name")
    void ofWithKindRejectsBlankName() {
        assertThatThrownBy(() -> Step.of("   ", "Before", obj -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    @DisplayName("of with kind rejects null consumer")
    void ofWithKindRejectsNullConsumer() {
        assertThatThrownBy(() -> Step.of("test", "Before", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("consumer must not be null");
    }

    @Test
    @DisplayName("custom kind is returned by getKind")
    void customKindIsReturnedByGetKind() {
        Step<?> step = Step.of("setUp()", ctx -> {}).kind("Before");

        assertThat(step.kind()).isEqualTo("Before");
    }

    @Test
    @DisplayName("kind rejects null")
    void kindRejectsNull() {
        Step<?> step = Step.of("test", obj -> {});

        assertThatThrownBy(() -> step.kind(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind must not be null");
    }

    @Test
    @DisplayName("kind rejects blank")
    void kindRejectsBlank() {
        Step<?> step = Step.of("test", obj -> {});

        assertThatThrownBy(() -> step.kind("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kind must not be blank");
    }

    @Test
    @DisplayName("kind returns new instance without modifying original")
    void kindReturnsNewInstanceWithoutModifyingOriginal() {
        Step<?> original = Step.of("setUp()", ctx -> {});
        Step<?> customized = original.kind("Before");

        assertThat(original.kind()).isEqualTo("Step");
        assertThat(customized.kind()).isEqualTo("Before");
        assertThat(original).isNotSameAs(customized);
        assertThat(original.name()).isEqualTo(customized.name());
    }
}
