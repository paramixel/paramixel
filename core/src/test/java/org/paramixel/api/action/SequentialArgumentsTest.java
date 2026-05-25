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

@DisplayName("Sequential arguments")
class SequentialArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Sequential.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sequential.of(" ")).isInstanceOf(IllegalArgumentException.class);
        var seq = Sequential.of("empty").resolve();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Sequential.of("seq");
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null name")
    void childStringConsumerRejectsNullName() {
        var spec = Sequential.of("seq");
        assertThatThrownBy(() -> spec.child(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects blank name")
    void childStringConsumerRejectsBlankName() {
        var spec = Sequential.of("seq");
        assertThatThrownBy(() -> spec.child(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null consumer")
    void childStringConsumerRejectsNullConsumer() {
        var spec = Sequential.of("seq");
        assertThatThrownBy(() -> spec.child("child", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var seq = Sequential.of("seq").child(Step.of("step", s -> {})).resolve();
        assertThat(seq.children()).hasSize(1);
        assertThat(seq.children().get(0).name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) adds child step")
    void childStringConsumerAddsChild() {
        var seq = Sequential.of("seq").child("step", s -> {}).resolve();
        assertThat(seq.children()).hasSize(1);
        assertThat(seq.children().get(0).name()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var seq = Sequential.of("seq")
                .child("first", s -> {})
                .child("second", s -> {})
                .child("third", s -> {})
                .resolve();
        assertThat(seq.children()).hasSize(3);
        assertThat(seq.children().get(0).name()).isEqualTo("first");
        assertThat(seq.children().get(1).name()).isEqualTo("second");
        assertThat(seq.children().get(2).name()).isEqualTo("third");
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Sequential.of("seq");
        spec.child("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.child("other", s -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.dependent());
        assertThatIllegalStateException().isThrownBy(() -> spec.independent());
    }

    @Test
    @DisplayName("default is dependent")
    void defaultIsDependent() {
        var seq = Sequential.of("seq").child("test", s -> {}).resolve();
        assertThat(seq.isDependent()).isTrue();
        assertThat(seq.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var seq = Sequential.of("seq").independent().child("test", s -> {}).resolve();
        assertThat(seq.isIndependent()).isTrue();
        assertThat(seq.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var seq = Sequential.of("seq")
                .independent()
                .dependent()
                .child("test", s -> {})
                .resolve();
        assertThat(seq.isDependent()).isTrue();
    }
}
