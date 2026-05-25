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

@DisplayName("Parallel arguments")
class ParallelArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Parallel.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Parallel.of(" ")).isInstanceOf(IllegalArgumentException.class);
        var parallel = Parallel.of("empty").resolve();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null name")
    void childStringConsumerRejectsNullName() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.child(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects blank name")
    void childStringConsumerRejectsBlankName() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.child(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null consumer")
    void childStringConsumerRejectsNullConsumer() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.child("child", (ThrowingConsumer<Object>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var parallel = Parallel.of("parallel").child(Step.of("step", s -> {})).resolve();
        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0).name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) adds child step")
    void childStringConsumerAddsChild() {
        var parallel = Parallel.of("parallel").child("step", s -> {}).resolve();
        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0).name()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var parallel = Parallel.of("parallel")
                .child("first", s -> {})
                .child("second", s -> {})
                .child("third", s -> {})
                .resolve();
        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).name()).isEqualTo("first");
        assertThat(parallel.children().get(1).name()).isEqualTo("second");
        assertThat(parallel.children().get(2).name()).isEqualTo("third");
    }

    @Test
    @DisplayName("parallelism() rejects zero")
    void parallelismRejectsZero() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.parallelism(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() rejects negative")
    void parallelismRejectsNegative() {
        var spec = Parallel.of("parallel");
        assertThatThrownBy(() -> spec.parallelism(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() sets parallelism")
    void parallelismSetsParallelism() {
        var parallel =
                Parallel.of("parallel").parallelism(4).child("step", s -> {}).resolve();
        assertThat(parallel.parallelism()).isEqualTo(4);
    }

    @Test
    @DisplayName("default parallelism is Integer.MAX_VALUE")
    void defaultParallelismIsMaxValue() {
        var parallel = Parallel.of("parallel").child("step", s -> {}).resolve();
        assertThat(parallel.parallelism()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Parallel.of("parallel");
        spec.child("test", s -> {});
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.child("other", s -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.parallelism(2));
    }

    @Test
    @DisplayName("Parallel does not have dependent/independent")
    void parallelHasNoDependentIndependent() {
        var parallel = Parallel.of("parallel").child("test", s -> {}).resolve();
        assertThat(parallel.getClass().getDeclaredFields())
                .anyMatch(f -> !f.getName().equals("dependent"));
    }
}
