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

@DisplayName("Timeout arguments")
class TimeoutArgumentsTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Timeout.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Timeout.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Timeout.builder("timeout").body((Action) null))
                .isInstanceOf(NullPointerException.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> Timeout.builder("empty").body(STEP).build())
                .withMessage("timeout duration must be configured");
    }

    @Test
    @DisplayName("of(String, Action) adds child from builder")
    void ofStringActionAddsBody() {
        var timeout =
                Timeout.builder("timeout").body(STEP).timeout(ofMillis(100)).build();
        assertThat(timeout.body()).isNotNull();
        assertThat(timeout.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("timeout(Duration) rejects null")
    void timeoutDurationRejectsNull() {
        var builder = Timeout.builder("timeout").body(STEP);
        assertThatThrownBy(() -> builder.timeout(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("timeout(Duration) rejects zero")
    void timeoutDurationRejectsZero() {
        var builder = Timeout.builder("timeout").body(STEP);
        assertThatThrownBy(() -> builder.timeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeout(Duration) rejects negative")
    void timeoutDurationRejectsNegative() {
        var builder = Timeout.builder("timeout").body(STEP);
        assertThatThrownBy(() -> builder.timeout(ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeout(Duration) sets timeout")
    void timeoutDurationSetsTimeout() {
        var timeout =
                Timeout.builder("timeout").body(STEP).timeout(ofMillis(500)).build();
        assertThat(timeout.timeout()).isEqualTo(ofMillis(500));
    }

    @Test
    @DisplayName("timeoutMillis() rejects zero")
    void timeoutMillisRejectsZero() {
        var builder = Timeout.builder("timeout").body(STEP);
        assertThatThrownBy(() -> builder.timeoutMillis(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeoutMillis() rejects negative")
    void timeoutMillisRejectsNegative() {
        var builder = Timeout.builder("timeout").body(STEP);
        assertThatThrownBy(() -> builder.timeoutMillis(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timeoutMillis() sets timeout")
    void timeoutMillisSetsTimeout() {
        var timeout = Timeout.builder("timeout").body(STEP).timeoutMillis(500).build();
        assertThat(timeout.timeout()).isEqualTo(ofMillis(500));
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Timeout.builder("timeout").body(Step.of("test", s -> {}));
        builder.timeout(ofMillis(100));
        var first = builder.build();
        builder.timeout(ofMillis(200));
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(first.timeout()).isEqualTo(ofMillis(100));
        assertThat(second.body().displayName()).isEqualTo("test");
        assertThat(second.timeout()).isEqualTo(ofMillis(200));
    }
}
