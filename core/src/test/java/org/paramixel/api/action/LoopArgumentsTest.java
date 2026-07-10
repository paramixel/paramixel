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

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Loop arguments")
class LoopArgumentsTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Loop.builder(null).maxIterations(1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Loop.builder(" ").maxIterations(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Loop.builder("loop").maxIterations(1).body((Action) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("bodyAcceptsAction()")
    void bodyAcceptsAction() {
        var loop = Loop.builder("loop").body(STEP).maxIterations(1).build();
        assertThat(loop.body()).isNotNull();
        assertThat(loop.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("maxIterations() rejects zero")
    void maxIterationsRejectsZero() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() rejects negative")
    void maxIterationsRejectsNegative() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() sets value")
    void maxIterationsSetsValue() {
        var loop = Loop.builder("loop").body(STEP).maxIterations(5).build();
        assertThat(loop.maxIterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("until() rejects null")
    void untilRejectsNull() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.until(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate is null");
    }

    @Test
    @DisplayName("delay policy rejects null")
    void delayPolicyRejectsNull() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay((Loop.DelayPolicy) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy is null");
    }

    @Test
    @DisplayName("delay duration rejects null")
    void delayDurationRejectsNull() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay((Duration) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fixedDelay is null");
    }

    @Test
    @DisplayName("delay duration rejects negative")
    void delayDurationRejectsNegative() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedDelay is negative");
    }

    @Test
    @DisplayName("delay duration rejects negative sub-millisecond duration")
    void delayDurationRejectsNegativeSubMillisecondDuration() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay(Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedDelay is negative");
    }

    @Test
    @DisplayName("delay millis rejects negative")
    void delayMillisRejectsNegative() {
        var builder = Loop.builder("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedDelayMillis is negative");
    }

    @Test
    @DisplayName("delay millis wraps in Linear policy")
    void delayMillisWrapsInLinear() {
        var action =
                Loop.builder("loop").body(STEP).maxIterations(1).delay(500L).build();
        assertThat(action.delay()).isPresent();
        assertThat(action.delay().get()).isInstanceOf(Loop.DelayPolicy.Linear.class);
        assertThat(((Loop.DelayPolicy.Linear) action.delay().get()).delay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("delay convenience wraps in Linear")
    void delayConvenienceWrapsInLinear() {
        var action = Loop.builder("loop")
                .body(STEP)
                .maxIterations(1)
                .delay(Duration.ofMillis(500))
                .build();
        assertThat(action.delay()).isPresent();
        assertThat(action.delay().get()).isInstanceOf(Loop.DelayPolicy.Linear.class);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Loop.builder("loop").body(STEP);
        var first = builder.maxIterations(1).build();
        builder.maxIterations(3);
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("step");
        assertThat(first.maxIterations()).isEqualTo(1);
        assertThat(second.body().displayName()).isEqualTo("step");
        assertThat(second.maxIterations()).isEqualTo(3);
    }

    @Test
    @DisplayName("loop() is alias for builder()")
    void loopIsAliasForBuilder() {
        var action = Loop.loop("loop").body(STEP).maxIterations(1).build();
        assertThat(action.displayName()).isEqualTo("loop");
        assertThat(action.body()).isNotNull();
    }

    @Test
    @DisplayName("loop() rejects null")
    void loopRejectsNull() {
        assertThatThrownBy(() -> Loop.loop(null).maxIterations(1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("loop() rejects blank")
    void loopRejectsBlank() {
        assertThatThrownBy(() -> Loop.loop(" ").maxIterations(1)).isInstanceOf(IllegalArgumentException.class);
    }
}
