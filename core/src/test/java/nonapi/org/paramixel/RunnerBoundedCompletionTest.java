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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

/**
 * Verifies that the {@link Timeout} action properly bounds long-running subtrees.
 */
@DisplayName("Runner Timeout action")
class RunnerBoundedCompletionTest {

    @Test
    @DisplayName("Timeout action aborts a non-cooperative leaf and the runner returns")
    void timeoutActionAbortsNonCooperativeLeaf() {
        var stop = new AtomicBoolean(false);
        var stuckStep = Step.of("stuck", ctx -> {
            while (!stop.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // ignored — non-cooperative
                }
            }
        });
        var action = Timeout.builder("timeout")
                .body(Sequential.builder("root")
                        .child(stuckStep)
                        .child(Step.of("after", ctx -> {}))
                        .build())
                .timeout(Duration.ofSeconds(2))
                .build();

        var start = System.nanoTime();
        var result = Runner.builder().build().run(action);
        var elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

        assertThat(result).isNotNull();
        assertThat(result.descriptor().orElseThrow().isFailed()).isTrue();
        assertThat(elapsedSeconds)
                .as("runner should return shortly after the Timeout fires")
                .isLessThan(15.0);
        stop.set(true);
    }
}
