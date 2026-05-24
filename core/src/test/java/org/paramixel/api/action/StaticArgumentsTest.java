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
import org.paramixel.api.ThrowingRunnable;

@DisplayName("Static arguments")
class StaticArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Static.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Static.of(" ")).isInstanceOf(IllegalArgumentException.class);
        var action = Static.of("empty").resolve();
        assertThat(action.before()).isEmpty();
        assertThat(action.children()).isEmpty();
        assertThat(action.after()).isEmpty();
    }

    @Test
    @DisplayName("before(Spec) rejects null spec")
    void beforeSpecRejectsNull() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.before((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingRunnable) rejects null name")
    void beforeStringRunnableRejectsNullName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.before(null, () -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingRunnable) rejects blank name")
    void beforeStringRunnableRejectsBlankName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.before(" ", () -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("before(String, ThrowingRunnable) rejects null runnable")
    void beforeStringRunnableRejectsNullRunnable() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.before("before", (ThrowingRunnable) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(Spec) rejects null spec")
    void afterSpecRejectsNull() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.after((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingRunnable) rejects null name")
    void afterStringRunnableRejectsNullName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.after(null, () -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingRunnable) rejects blank name")
    void afterStringRunnableRejectsBlankName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.after(" ", () -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("after(String, ThrowingRunnable) rejects null runnable")
    void afterStringRunnableRejectsNullRunnable() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.after("after", (ThrowingRunnable) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var action = Static.of("static").child(Step.of("step", ctx -> {})).resolve();
        assertThat(action.children().stream().filter(a -> "step".equals(a.name())))
                .hasSize(1);
    }

    @Test
    @DisplayName("child(String, ThrowingRunnable) rejects null name")
    void childStringRunnableRejectsNullName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.child(null, () -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingRunnable) rejects blank name")
    void childStringRunnableRejectsBlankName() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.child(" ", () -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingRunnable) rejects null runnable")
    void childStringRunnableRejectsNullRunnable() {
        var spec = Static.of("static");
        assertThatThrownBy(() -> spec.child("child", (ThrowingRunnable) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var action = Static.of("static")
                .child("first", () -> {})
                .child("second", () -> {})
                .child("third", () -> {})
                .resolve();
        assertThat(action.children().stream()
                        .filter(a -> "first".equals(a.name()) || "second".equals(a.name()) || "third".equals(a.name())))
                .hasSize(3);
    }

    @Test
    @DisplayName("builder is one-shot — before(Spec) after resolve throws ISE")
    void builderOneShotBeforeBuilder() {
        var spec = Static.of("static");
        spec.child("test", () -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.before(Step.of("before", ctx -> {})));
    }

    @Test
    @DisplayName("builder is one-shot — after(Spec) after resolve throws ISE")
    void builderOneShotAfterBuilder() {
        var spec = Static.of("static");
        spec.child("test", () -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.after(Step.of("after", ctx -> {})));
    }

    @Test
    @DisplayName("builder is one-shot — child(Spec) after resolve throws ISE")
    void builderOneShotChildBuilder() {
        var spec = Static.of("static");
        spec.child("test", () -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.child(Step.of("other", ctx -> {})));
    }

    @Test
    @DisplayName("before(Spec) overwrites previous before")
    void beforeBuilderOverwritesBefore() {
        var spec = Static.of("static");
        spec.before(Step.of("first", ctx -> {}));
        spec.before(Step.of("second", ctx -> {}));
        var action = spec.child("test", () -> {}).resolve();
        assertThat(action).isNotNull();
        assertThat(action.before()).isPresent();
        assertThat(action.before().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("before(String, ThrowingRunnable) overwrites previous before")
    void beforeStringRunnableOverwritesBefore() {
        var spec = Static.of("static");
        spec.before("first", () -> {});
        spec.before("second", () -> {});
        var action = spec.child("test", () -> {}).resolve();
        assertThat(action).isNotNull();
        assertThat(action.before()).isPresent();
        assertThat(action.before().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(Spec) overwrites previous after")
    void afterBuilderOverwritesAfter() {
        var spec = Static.of("static");
        spec.after(Step.of("first", ctx -> {}));
        spec.after(Step.of("second", ctx -> {}));
        var action = spec.child("test", () -> {}).resolve();
        assertThat(action).isNotNull();
        assertThat(action.after()).isPresent();
        assertThat(action.after().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("after(String, ThrowingRunnable) overwrites previous after")
    void afterStringRunnableOverwritesAfter() {
        var spec = Static.of("static");
        spec.after("first", () -> {});
        spec.after("second", () -> {});
        var action = spec.child("test", () -> {}).resolve();
        assertThat(action).isNotNull();
        assertThat(action.after()).isPresent();
        assertThat(action.after().get().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Static.of("static");
        spec.child("test", () -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.child("other", () -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.before("before", () -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.after("after", () -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.dependent());
        assertThatIllegalStateException().isThrownBy(() -> spec.independent());
    }

    @Test
    @DisplayName("default is dependent")
    void defaultIsDependent() {
        var action = Static.of("static").child("test", () -> {}).resolve();
        assertThat(action.isDependent()).isTrue();
        assertThat(action.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var action = Static.of("static").independent().child("test", () -> {}).resolve();
        assertThat(action.isIndependent()).isTrue();
        assertThat(action.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var action = Static.of("static")
                .independent()
                .dependent()
                .child("test", () -> {})
                .resolve();
        assertThat(action.isDependent()).isTrue();
    }
}
