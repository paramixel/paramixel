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
import org.paramixel.api.Context;
import org.paramixel.api.Status;
import org.paramixel.api.support.Backoff;

@DisplayName("Loop arguments")
class LoopArgumentsTest {

    private static final Action STEP = Step.step("step", s -> {});

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Loop.loop(null).maxIterations(1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Loop.loop(" ").maxIterations(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Loop.loop("loop").maxIterations(1).body((Action) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("bodyAcceptsAction()")
    void bodyAcceptsAction() {
        var loop = Loop.loop("loop").body(STEP).maxIterations(1).build();
        assertThat(loop.body()).isNotNull();
        assertThat(loop.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("maxIterations() rejects zero")
    void maxIterationsRejectsZero() {
        var builder = Loop.loop("loop").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() rejects negative")
    void maxIterationsRejectsNegative() {
        var builder = Loop.loop("loop").body(STEP);
        assertThatThrownBy(() -> builder.maxIterations(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxIterations() sets value")
    void maxIterationsSetsValue() {
        var loop = Loop.loop("loop").body(STEP).maxIterations(5).build();
        assertThat(loop.maxIterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("until() rejects null")
    void untilRejectsNull() {
        var builder = Loop.loop("loop").body(STEP);
        assertThatThrownBy(() -> builder.until((java.util.function.Predicate<Context>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate is null");
    }

    @Test
    @DisplayName("until(BiPredicate) rejects null")
    void untilBiPredicateRejectsNull() {
        var builder = Loop.loop("loop").body(STEP);
        assertThatThrownBy(() -> builder.until((java.util.function.BiPredicate<Context, Status>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate is null");
    }

    @Test
    @DisplayName("untilPassed configures a predicate")
    void untilPassedConfiguresPredicate() {
        var action = Loop.loop("loop").body(STEP).maxIterations(2).untilPassed().build();

        assertThat(action.until()).isPresent();
    }

    @Test
    @DisplayName("delay rejects null")
    void delayRejectsNull() {
        var builder = Loop.loop("loop").body(STEP);
        assertThatThrownBy(() -> builder.delay(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("backoff is null");
    }

    @Test
    @DisplayName("delay stores the backoff schedule")
    void delayStoresBackoff() {
        var backoff = Backoff.fixed(Duration.ofMillis(500));
        var action =
                Loop.loop("loop").body(STEP).maxIterations(1).delay(backoff).build();

        assertThat(action.delay()).containsSame(backoff);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Loop.loop("loop").body(STEP);
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
