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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Runner;

@DisplayName("Noop")
class NoopTest {

    @Test
    @DisplayName("of returns a fresh action for the same name")
    void ofReturnsAFreshActionForTheSameName() {
        Noop first = Noop.of("noop");
        Noop second = Noop.of("noop");

        assertThat(first).isNotSameAs(second);
        assertThat(first.getName()).isEqualTo("noop");
        assertThat(second.getName()).isEqualTo("noop");
    }

    @Test
    @DisplayName("same noop name can be used multiple times in one action tree")
    void sameNoopNameCanBeUsedMultipleTimesInOneActionTree() {
        Lifecycle lifecycle = Lifecycle.of("lifecycle", Noop.of("noop"), Noop.of("noop"), Noop.of("noop"));

        Runner.builder().build().run(lifecycle);

        assertThat(lifecycle.getResult().getStatus().isPass()).isTrue();
        assertThat(lifecycle.getChildren())
                .allSatisfy(action ->
                        assertThat(action.getResult().getStatus().isPass()).isTrue());
    }
}
