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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;

@DisplayName("CycleDetector")
class CycleDetectorTest {

    @Test
    @DisplayName("validateNoCycles rejects null root")
    void validateNoCyclesRejectsNullRoot() {
        assertThatThrownBy(() -> new CycleDetector().validateNoCycles(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root must not be null");
    }

    @Test
    @DisplayName("linear action graph passes validation")
    void linearActionGraphPassesValidation() {
        Action leaf1 = Noop.of("leaf-1");
        Action leaf2 = Noop.of("leaf-2");
        Action root = Container.builder("root").child(leaf1).child(leaf2).build();

        assertThatCode(() -> new CycleDetector().validateNoCycles(root)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("diamond action graph passes validation")
    void diamondActionGraphPassesValidation() {
        Action shared = Noop.of("shared");
        Action branch1 = Container.builder("branch-1").child(shared).build();
        Action branch2 = Container.builder("branch-2").child(shared).build();
        Action root = Container.builder("root").child(branch1).child(branch2).build();

        assertThatCode(() -> new CycleDetector().validateNoCycles(root)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deeply nested graph passes validation")
    void deeplyNestedGraphPassesValidation() {
        Action deep = Noop.of("deep");
        Action level2 = Container.builder("level-2").child(deep).build();
        Action level1 = Container.builder("level-1").child(level2).build();
        Action root = Container.builder("root").child(level1).build();

        assertThatCode(() -> new CycleDetector().validateNoCycles(root)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("parallel action graph passes validation")
    void parallelActionGraphPassesValidation() {
        Action root = Parallel.builder("parallel")
                .child(Noop.of("a"))
                .child(Noop.of("b"))
                .build();

        assertThatCode(() -> new CycleDetector().validateNoCycles(root)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("single action passes validation")
    void singleActionPassesValidation() {
        Action root = Noop.of("single");

        assertThatCode(() -> new CycleDetector().validateNoCycles(root)).doesNotThrowAnyException();
    }
}
