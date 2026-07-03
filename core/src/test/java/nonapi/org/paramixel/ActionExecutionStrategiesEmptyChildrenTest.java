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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;

@DisplayName("Empty composite action execution")
class ActionExecutionStrategiesEmptyChildrenTest {

    @Test
    @DisplayName("empty Parallel completes without exception")
    void emptyParallelShouldNotThrow() {
        var parallel = Parallel.parallel("empty-parallel").build();
        var runner = Runner.builder().build();

        var result = runner.run(parallel);
        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("empty Sequential completes without exception")
    void emptySequentialShouldNotThrow() {
        var sequential = Sequential.sequential("empty-sequential").build();
        var runner = Runner.builder().build();

        var result = runner.run(sequential);
        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("empty Sequence completes without exception")
    void emptySequenceShouldNotThrow() {
        var sequence = Sequence.sequence("empty-sequence").build();
        var runner = Runner.builder().build();

        var result = runner.run(sequence);
        assertThat(result).isNotNull();
        assertThat(result.isPassed()).isTrue();
    }
}
