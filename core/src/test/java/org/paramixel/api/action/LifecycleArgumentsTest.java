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

@DisplayName("Lifecycle arguments")
class LifecycleArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Lifecycle.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Lifecycle.of(" ")).isInstanceOf(IllegalArgumentException.class);
        var lifecycle = Lifecycle.of("empty").resolve();
        assertThat(lifecycle.before()).isEmpty();
        assertThat(lifecycle.children()).isEmpty();
        assertThat(lifecycle.after()).isEmpty();
    }

    @Test
    @DisplayName("before(Spec) rejects null spec")
    void beforeSpecRejectsNull() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.before((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingConsumer) rejects null name")
    void beforeStringConsumerRejectsNullName() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.before(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingConsumer) rejects blank name")
    void beforeStringConsumerRejectsBlankName() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.before(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingConsumer) rejects null consumer")
    void beforeStringConsumerRejectsNullConsumer() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.before("before", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(Spec) rejects null spec")
    void afterSpecRejectsNull() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.after((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingConsumer) rejects null name")
    void afterStringConsumerRejectsNullName() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.after(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingConsumer) rejects blank name")
    void afterStringConsumerRejectsBlankName() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.after(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingConsumer) rejects null consumer")
    void afterStringConsumerRejectsNullConsumer() {
        var spec = Lifecycle.of("lifecycle");
        assertThatThrownBy(() -> spec.after("after", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder is one-shot — before(Spec) after resolve throws ISE")
    void builderOneShotBeforeBuilder() {
        var spec = Lifecycle.of("lifecycle");
        spec.before("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.before(Step.of("before", s -> {})));
    }

    @Test
    @DisplayName("builder is one-shot — after(Spec) after resolve throws ISE")
    void builderOneShotAfterBuilder() {
        var spec = Lifecycle.of("lifecycle");
        spec.after("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.after(Step.of("after", s -> {})));
    }

    @Test
    @DisplayName("before(Spec) overwrites previous before")
    void beforeBuilderOverwritesBefore() {
        var spec = Lifecycle.of("lifecycle");
        spec.before(Step.of("first", s -> {}));
        spec.before(Step.of("second", s -> {}));
        var lifecycle = spec.after("after", s -> {}).resolve();
        assertThat(lifecycle.before()).isPresent();
        assertThat(lifecycle.before().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("before(String, ThrowingConsumer) overwrites previous before")
    void beforeStringConsumerOverwritesBefore() {
        var spec = Lifecycle.of("lifecycle");
        spec.before("first", s -> {});
        spec.before("second", s -> {});
        var lifecycle = spec.after("after", s -> {}).resolve();
        assertThat(lifecycle.before()).isPresent();
        assertThat(lifecycle.before().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(Spec) overwrites previous after")
    void afterBuilderOverwritesAfter() {
        var spec = Lifecycle.of("lifecycle");
        spec.after(Step.of("first", s -> {}));
        spec.after(Step.of("second", s -> {}));
        var lifecycle = spec.before("before", s -> {}).resolve();
        assertThat(lifecycle.after()).isPresent();
        assertThat(lifecycle.after().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(String, ThrowingConsumer) overwrites previous after")
    void afterStringConsumerOverwritesAfter() {
        var spec = Lifecycle.of("lifecycle");
        spec.after("first", s -> {});
        spec.after("second", s -> {});
        var lifecycle = spec.before("before", s -> {}).resolve();
        assertThat(lifecycle.after()).isPresent();
        assertThat(lifecycle.after().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Lifecycle.of("lifecycle");
        spec.before("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.before("before", s -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.after("after", s -> {}));
    }
}
