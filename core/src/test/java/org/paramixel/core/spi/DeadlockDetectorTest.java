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

package org.paramixel.core.spi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Noop;

@DisplayName("DeadlockDetector")
class DeadlockDetectorTest {

    @Test
    @DisplayName("validateNoDeadlock rejects null root")
    void validateNoDeadlockRejectsNullRoot() {
        assertThatThrownBy(() -> new DeadlockDetector().validateNoDeadlock(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root must not be null");
    }

    @Test
    @DisplayName("validateNoDeadlock rejects zero parallelism")
    void validateNoDeadlockRejectsZeroParallelism() {
        assertThatThrownBy(() -> new DeadlockDetector().validateNoDeadlock(Noop.of("test"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parallelism must be positive, was: 0");
    }

    @Test
    @DisplayName("validateNoDeadlock rejects negative parallelism")
    void validateNoDeadlockRejectsNegativeParallelism() {
        assertThatThrownBy(() -> new DeadlockDetector().validateNoDeadlock(Noop.of("test"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parallelism must be positive, was: -1");
    }
}
