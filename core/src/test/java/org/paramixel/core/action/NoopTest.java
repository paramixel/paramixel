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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

@DisplayName("Noop")
class NoopTest {

    @Test
    @DisplayName("passes without work")
    void passesWithoutWork() {
        Noop action = Noop.of("noop");

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(action.contextMode()).isEqualTo(Action.ContextMode.ISOLATED);
    }

    @Test
    @DisplayName("validates name")
    void validatesName() {
        assertThatThrownBy(() -> Noop.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Noop.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
