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
import static org.assertj.core.data.Offset.offset;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Runner;

@DisplayName("Delay action")
class DelayTest {

    @Test
    @DisplayName("fixed delay with milliseconds passes")
    void fixedDelayWithMillisecondsPasses() {
        var action = Delay.of("fixed-ms", 50);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("fixed delay with duration passes")
    void fixedDelayWithDurationPasses() {
        var action = Delay.of("fixed-duration", Duration.ofMillis(50));
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("random delay passes and respects bounds")
    void randomDelayPassesAndRespectsBounds() {
        long minimumMilliseconds = 10;
        long maximumMilliseconds = 100;
        var action = Delay.random("random-delay", minimumMilliseconds, maximumMilliseconds);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(elapsedMillis(root)).isCloseTo(minimumMilliseconds, offset(maximumMilliseconds));
    }

    @Test
    @DisplayName("random delay with equal min and max acts as fixed")
    void randomDelayWithEqualMinMaxActsAsFixed() {
        var action = Delay.random("equal-bounds", 50, 50);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(elapsedMillis(root)).isCloseTo(50, offset(50L));
    }

    @Test
    @DisplayName("zero-millisecond delay passes immediately")
    void zeroMillisecondDelayPassesImmediately() {
        var action = Delay.of("zero-delay", 0L);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(elapsedMillis(root)).isLessThan(100);
    }

    @Test
    @DisplayName("getName returns the supplied name")
    void getNameReturnsSuppliedName() {
        var action = Delay.of("my-delay", 100);

        assertThat(action.displayName()).isEqualTo("my-delay");
    }

    private static long elapsedMillis(final Descriptor descriptor) {
        return Duration.between(
                        descriptor.startedAt().orElseThrow(),
                        descriptor.completedAt().orElseThrow())
                .toMillis();
    }
}
