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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Repeat arguments")
@SuppressWarnings("removal")
class RepeatArgumentsTest {

    private static final Action STEP = Step.of("step", s -> {});

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Repeat.builder(null).body(STEP)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Repeat.builder(" ").body(STEP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Repeat.builder("repeat").body((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Action) adds child from builder")
    void ofStringActionAddsBody() {
        var repeat = Repeat.builder("repeat").body(STEP).build();
        assertThat(repeat.body()).isNotNull();
        assertThat(repeat.body().displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("count() rejects zero")
    void countRejectsZero() {
        var builder = Repeat.builder("repeat").body(STEP);
        assertThatThrownBy(() -> builder.iterations(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("count() rejects negative")
    void countRejectsNegative() {
        var builder = Repeat.builder("repeat").body(STEP);
        assertThatThrownBy(() -> builder.iterations(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("count() sets repeat count")
    void countSetsRepeatCount() {
        var repeat = Repeat.builder("repeat").body(STEP).iterations(5).build();
        assertThat(repeat.iterations()).isEqualTo(5);
    }

    @Test
    @DisplayName("default count is 1")
    void defaultCountIsOne() {
        var repeat = Repeat.builder("repeat").body(STEP).build();
        assertThat(repeat.iterations()).isEqualTo(1);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Repeat.builder("repeat").body(Step.of("test", s -> {}));
        var first = builder.build();
        builder.iterations(3);
        var second = builder.build();

        assertThat(first.body().displayName()).isEqualTo("test");
        assertThat(first.iterations()).isEqualTo(1);
        assertThat(second.body().displayName()).isEqualTo("test");
        assertThat(second.iterations()).isEqualTo(3);
    }
}
