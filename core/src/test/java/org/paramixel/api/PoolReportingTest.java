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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;

/**
 * Pins the pool reporting in the summary and the warning for a pool key no {@code Parallel} uses.
 */
@DisplayName("Pool reporting")
@org.junit.jupiter.api.Timeout(60)
class PoolReportingTest {

    private static final class WarningListener implements Listener {

        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void onWarning(final String message) {
            warnings.add(message);
        }
    }

    private static String runCapturingOutput(final String pool, final int poolSize) {
        var action = Parallel.parallel("fan-out", pool)
                .children(IntStream.range(0, 4)
                        .mapToObj(index -> Step.step("child-" + index, context -> {}))
                        .toList())
                .build();
        var output = new ByteArrayOutputStream();
        var original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Runner.builder()
                    .configuration(Configuration.of(Map.of(Configuration.poolKey(pool), String.valueOf(poolSize))))
                    .build()
                    .run(action);
        } finally {
            System.setOut(original);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the summary prints each pool's peak usage and size")
    void summaryPrintsPeakUsage() {
        var output = runCapturingOutput("reporting", 2);

        assertThat(output).contains("Pools       : reporting");
        assertThat(output).contains("/2");
    }

    @Test
    @DisplayName("a saturated pool reports a peak equal to its size")
    void saturatedPoolReportsFullPeak() throws InterruptedException {
        // Both children must be in flight at once for the run to finish, which is what a size-two pool allows.
        var bothRunning = new CountDownLatch(2);
        var action = Parallel.parallel("fan-out", "saturated")
                .child(Step.step("a", context -> {
                    bothRunning.countDown();
                    bothRunning.await(30, TimeUnit.SECONDS);
                }))
                .child(Step.step("b", context -> {
                    bothRunning.countDown();
                    bothRunning.await(30, TimeUnit.SECONDS);
                }))
                .build();
        var result = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.poolKey("saturated"), "2")))
                .build()
                .run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        var stats = ((nonapi.org.paramixel.ConcreteResult) result).poolStats();
        assertThat(stats).anySatisfy(pool -> {
            assertThat(pool.name()).isEqualTo("saturated");
            assertThat(pool.size()).isEqualTo(2);
            assertThat(pool.peakInUse()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("a pool key no Parallel uses produces a warning")
    void unusedPoolKeyWarns() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.poolKey("unused"), "2")))
                .listener(listener)
                .build();

        runner.run(Step.step("step", context -> {}));

        assertThat(listener.warnings).hasSize(1);
        assertThat(listener.warnings.get(0))
                .contains("paramixel.parallelism.unused")
                .contains("no Parallel in this run uses it");
    }

    @Test
    @DisplayName("a used pool key produces no warning")
    void usedPoolKeyDoesNotWarn() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.poolKey("used"), "1")))
                .listener(listener)
                .build();

        runner.run(Parallel.parallel("fan-out", "used")
                .child(Step.step("step", context -> {}))
                .build());

        assertThat(listener.warnings).isEmpty();
    }

    @Test
    @DisplayName("the pool line is omitted when no pool is used")
    void noPoolLineWithoutPools() {
        var output = new ByteArrayOutputStream();
        var original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Runner.builder().configuration(Configuration.of(Map.of())).build().run(Step.step("step", context -> {}));
        } finally {
            System.setOut(original);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("Pools       :");
    }
}
