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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ThrowingConsumer;

@DisplayName("Repeat arguments")
class RepeatArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Repeat.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Repeat.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> Repeat.of("empty").resolve())
                .withMessage("repeat must contain a child action");
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null name")
    void childStringConsumerRejectsNullName() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.child(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects blank name")
    void childStringConsumerRejectsBlankName() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.child(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null consumer")
    void childStringConsumerRejectsNullConsumer() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.child("child", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var repeat = Repeat.of("repeat").child(Step.of("step", s -> {})).resolve();
        assertThat(repeat.child()).isNotNull();
        assertThat(repeat.child().name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) adds child step")
    void childStringConsumerAddsChild() {
        var repeat = Repeat.of("repeat").child("step", s -> {}).resolve();
        assertThat(repeat.child()).isNotNull();
        assertThat(repeat.child().name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(Spec) overwrites previous child")
    void childBuilderOverwritesChild() {
        var repeat = Repeat.of("repeat")
                .child(Step.of("first", s -> {}))
                .child(Step.of("second", s -> {}))
                .resolve();
        assertThat(repeat.child().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) overwrites previous child")
    void childStringConsumerOverwritesChild() {
        var repeat = Repeat.of("repeat")
                .child("first", s -> {})
                .child("second", s -> {})
                .resolve();
        assertThat(repeat.child().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("count() rejects zero")
    void countRejectsZero() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.count(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("count() rejects negative")
    void countRejectsNegative() {
        var spec = Repeat.of("repeat");
        assertThatThrownBy(() -> spec.count(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("count() sets repeat count")
    void countSetsRepeatCount() {
        var repeat = Repeat.of("repeat").count(5).child("step", s -> {}).resolve();
        assertThat(repeat.repeatCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("default count is 1")
    void defaultCountIsOne() {
        var repeat = Repeat.of("repeat").child("step", s -> {}).resolve();
        assertThat(repeat.repeatCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Repeat.of("repeat");
        spec.child("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.child("other", s -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.dependent());
        assertThatIllegalStateException().isThrownBy(() -> spec.independent());
        assertThatIllegalStateException().isThrownBy(() -> spec.count(3));
    }

    @Test
    @DisplayName("default is dependent")
    void defaultIsDependent() {
        var repeat = Repeat.of("repeat").child("test", s -> {}).resolve();
        assertThat(repeat.isDependent()).isTrue();
        assertThat(repeat.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var repeat = Repeat.of("repeat").independent().child("test", s -> {}).resolve();
        assertThat(repeat.isIndependent()).isTrue();
        assertThat(repeat.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var repeat = Repeat.of("repeat")
                .independent()
                .dependent()
                .child("test", s -> {})
                .resolve();
        assertThat(repeat.isDependent()).isTrue();
    }
}
