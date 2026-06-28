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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Repeat execution")
@SuppressWarnings("removal")
class RepeatExecutionTest {

    @Test
    @DisplayName("repetitions run sequentially without overlap")
    void repetitionsRunSequentiallyWithoutOverlap() {
        var inFlight = new AtomicInteger();
        var maxConcurrent = new AtomicInteger();
        var iteration = new AtomicInteger();
        var observedOrder = Collections.synchronizedList(new ArrayList<Integer>());

        var action = Repeat.builder("repeat")
                .body(Step.of("step", context -> {
                    var current = inFlight.incrementAndGet();
                    maxConcurrent.accumulateAndGet(current, Math::max);
                    observedOrder.add(iteration.incrementAndGet());
                    try {
                        Thread.sleep(25);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                }))
                .iterations(4)
                .build();

        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "4")))
                .build();
        var root = runner.run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(observedOrder).containsExactly(1, 2, 3, 4);
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("second rep aborts, all still run")
    void secondRepAbortsAllStillRun() {
        var counter = new AtomicInteger();

        var action = Repeat.builder("repeat")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        AbortedException.abort("aborted");
                    }
                }))
                .iterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("second rep skips, all still run")
    void secondRepSkipsAllStillRun() {
        var counter = new AtomicInteger();

        var action = Repeat.builder("repeat")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        SkipException.skip("skipped");
                    }
                }))
                .iterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("second rep runtime exception, all still run")
    void secondRepRuntimeExceptionAllStillRun() {
        var counter = new AtomicInteger();

        var action = Repeat.builder("repeat")
                .body(Step.of("step", context -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        throw new RuntimeException("runtime error");
                    }
                }))
                .iterations(3)
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }
}
