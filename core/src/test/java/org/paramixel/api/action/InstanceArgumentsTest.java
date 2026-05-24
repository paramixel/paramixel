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

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ThrowingConsumer;

@DisplayName("Instance arguments")
class InstanceArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Instance.of(null, (Supplier<?>) () -> "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Instance.of(" ", (Supplier<?>) () -> "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Instance.of("instance", (Supplier<?>) null)).isInstanceOf(NullPointerException.class);
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
        var instance = Instance.of(String.class).child("test", s -> {}).resolve();
        assertThat(instance.name()).isEqualTo("String");
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Instance.of("instance", Object::new);
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var instance = Instance.of("instance", Object::new)
                .child(Step.of("step", s -> {}))
                .resolve();
        assertThat(instance.children().stream().filter(a -> "step".equals(a.name())))
                .hasSize(1);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null name")
    void childStringConsumerRejectsNullName() {
        var spec = Instance.of("instance", Object::new);
        assertThatThrownBy(() -> spec.child(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects blank name")
    void childStringConsumerRejectsBlankName() {
        var spec = Instance.of("instance", Object::new);
        assertThatThrownBy(() -> spec.child(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null consumer")
    void childStringConsumerRejectsNullConsumer() {
        var spec = Instance.of("instance", Object::new);
        assertThatThrownBy(() -> spec.child("child", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var instance = Instance.of("instance", Object::new)
                .child("first", s -> {})
                .child("second", s -> {})
                .child("third", s -> {})
                .resolve();
        assertThat(instance.children().stream()
                        .filter(a -> "first".equals(a.name()) || "second".equals(a.name()) || "third".equals(a.name())))
                .hasSize(3);
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Instance.of("instance", Object::new);
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
        var instance =
                Instance.of("instance", Object::new).child("test", s -> {}).resolve();
        assertThat(instance.isDependent()).isTrue();
        assertThat(instance.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var instance = Instance.of("instance", Object::new)
                .independent()
                .child("test", s -> {})
                .resolve();
        assertThat(instance.isIndependent()).isTrue();
        assertThat(instance.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var instance = Instance.of("instance", Object::new)
                .independent()
                .dependent()
                .child("test", s -> {})
                .resolve();
        assertThat(instance.isDependent()).isTrue();
    }
}
