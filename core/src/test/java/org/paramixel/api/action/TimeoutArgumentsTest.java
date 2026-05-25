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

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ThrowingConsumer;

@DisplayName("Timeout arguments")
class TimeoutArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Timeout.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Timeout.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> Timeout.of("empty").resolve())
                .withMessage("timeout must contain a child action");
    }

    @Test
    @DisplayName("resolve without timeout throws ISE")
    void resolveWithoutTimeoutThrowsISE() {
        var spec = Timeout.of("timeout").child("step", s -> {});
        assertThatIllegalStateException().isThrownBy(spec::resolve).withMessage("timeout duration must be configured");
    }

    @Test
    @DisplayName("child(Spec) rejects null spec")
    void childSpecRejectsNull() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.child((Spec<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null name")
    void childStringConsumerRejectsNullName() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.child(null, s -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects blank name")
    void childStringConsumerRejectsBlankName() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.child(" ", s -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) rejects null consumer")
    void childStringConsumerRejectsNullConsumer() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.child("child", (ThrowingConsumer<?>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Spec) adds child from spec")
    void childSpecAddsChild() {
        var timeout = Timeout.of("timeout")
                .timeout(ofMillis(100))
                .child(Step.of("step", s -> {}))
                .resolve();
        assertThat(timeout.child()).isNotNull();
        assertThat(timeout.child().name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) adds child step")
    void childStringConsumerAddsChild() {
        var timeout = Timeout.of("timeout")
                .timeout(ofMillis(100))
                .child("step", s -> {})
                .resolve();
        assertThat(timeout.child()).isNotNull();
        assertThat(timeout.child().name()).isEqualTo("step");
    }

    @Test
    @DisplayName("child(Spec) overwrites previous child")
    void childBuilderOverwritesChild() {
        var timeout = Timeout.of("timeout")
                .timeout(ofMillis(100))
                .child(Step.of("first", s -> {}))
                .child(Step.of("second", s -> {}))
                .resolve();
        assertThat(timeout.child().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("child(String, ThrowingConsumer) overwrites previous child")
    void childStringConsumerOverwritesChild() {
        var timeout = Timeout.of("timeout")
                .timeout(ofMillis(100))
                .child("first", s -> {})
                .child("second", s -> {})
                .resolve();
        assertThat(timeout.child().name()).isEqualTo("second");
    }

    @Test
    @DisplayName("timeout(Duration) rejects null")
    void timeoutDurationRejectsNull() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.timeout(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("timeout(Duration) rejects zero")
    void timeoutDurationRejectsZero() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.timeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeout(Duration) rejects negative")
    void timeoutDurationRejectsNegative() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.timeout(ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeout(Duration) sets timeout")
    void timeoutDurationSetsTimeout() {
        var timeout = Timeout.of("timeout")
                .timeout(ofMillis(500))
                .child("step", s -> {})
                .resolve();
        assertThat(timeout.timeout()).isEqualTo(ofMillis(500));
    }

    @Test
    @DisplayName("timeoutMillis() rejects zero")
    void timeoutMillisRejectsZero() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.timeoutMillis(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeoutMillis() rejects negative")
    void timeoutMillisRejectsNegative() {
        var spec = Timeout.of("timeout");
        assertThatThrownBy(() -> spec.timeoutMillis(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeoutMillis() sets timeout")
    void timeoutMillisSetsTimeout() {
        var timeout =
                Timeout.of("timeout").timeoutMillis(500).child("step", s -> {}).resolve();
        assertThat(timeout.timeout()).isEqualTo(ofMillis(500));
    }

    @Test
    @DisplayName("builder is one-shot")
    void builderOneShotBehavior() {
        var spec = Timeout.of("timeout");
        spec.child("test", s -> {});
        spec.timeout(ofMillis(100));
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.resolve());
        assertThatIllegalStateException().isThrownBy(() -> spec.child("other", s -> {}));
        assertThatIllegalStateException().isThrownBy(() -> spec.timeout(ofMillis(200)));
        assertThatIllegalStateException().isThrownBy(() -> spec.timeoutMillis(200));
    }
}
