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

import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.DeadlockDetected;

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

    @Test
    @DisplayName("single-level parallel with sufficient parallelism passes")
    void singleLevelParallelWithSufficientParallelismPasses() {
        Action root = Parallel.builder("outer")
                .parallelism(2)
                .child(Noop.of("a"))
                .child(Noop.of("b"))
                .build();

        assertThatCode(() -> new DeadlockDetector().validateNoDeadlock(root, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nested parallel without dedicated executor detects deadlock")
    void nestedParallelWithoutDedicatedExecutorDetectsDeadlock() {
        Action inner = Parallel.builder("inner")
                .child(Noop.of("a"))
                .child(Noop.of("b"))
                .build();
        Action middle = Parallel.builder("middle").child(inner).build();
        Action outer = Parallel.builder("outer").child(middle).build();

        assertThatThrownBy(() -> new DeadlockDetector().validateNoDeadlock(outer, 1))
                .isInstanceOf(DeadlockDetected.class);
    }

    @Test
    @DisplayName("nested parallel with dedicated executor breaks deadlock chain")
    void nestedParallelWithDedicatedExecutorBreaksDeadlockChain() {
        var executor = Executors.newFixedThreadPool(2);
        try {
            Action inner = Parallel.builder("inner")
                    .executorService(executor)
                    .child(Noop.of("a"))
                    .child(Noop.of("b"))
                    .build();
            Action outer = Parallel.builder("outer").child(inner).build();

            assertThatCode(() -> new DeadlockDetector().validateNoDeadlock(outer, 1))
                    .doesNotThrowAnyException();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("deeply nested parallel without dedicated executors detects deadlock")
    void deeplyNestedParallelWithoutDedicatedExecutorsDetectsDeadlock() {
        Action leaf = Parallel.builder("level-3")
                .child(Noop.of("a"))
                .child(Noop.of("b"))
                .build();
        Action level2 = Parallel.builder("level-2").child(leaf).build();
        Action level1 = Parallel.builder("level-1").child(level2).build();

        assertThatThrownBy(() -> new DeadlockDetector().validateNoDeadlock(level1, 1))
                .isInstanceOf(DeadlockDetected.class);
    }

    @Test
    @DisplayName("container wrapping parallel does not add to depth")
    void containerWrappingParallelDoesNotAddToDepth() {
        Action inner = Parallel.builder("inner")
                .child(Noop.of("a"))
                .child(Noop.of("b"))
                .build();
        Action container = Container.builder("container").child(inner).build();
        Action outer = Parallel.builder("outer").child(container).build();

        assertThatCode(() -> new DeadlockDetector().validateNoDeadlock(outer, 1))
                .doesNotThrowAnyException();
    }
}
