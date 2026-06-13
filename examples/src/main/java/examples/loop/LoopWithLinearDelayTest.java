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

package examples.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Loop.loop;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Loop;

/**
 * Demonstrates the {@link org.paramixel.api.action.Loop} action with a
 * constant inter-iteration delay ({@link org.paramixel.api.action.Loop.DelayPolicy.Linear}).
 * Verifies that the loop execution accounts for the configured delay between iterations.
 */
public class LoopWithLinearDelayTest {

    private static final AtomicInteger iterationCount = new AtomicInteger();

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree that exercises loop with constant inter-iteration delay.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        return sequential("loop-linear-delay-example")
                .child(loop("poll")
                        .body(step("check", context -> iterationCount.incrementAndGet()))
                        .maxIterations(3)
                        .delay(new Loop.DelayPolicy.Linear(Duration.ofMillis(100))))
                .child(step("validate", context -> {
                    assertThat(iterationCount.get()).isEqualTo(3);
                }))
                .build();
    }

    private static void resetCounts() {
        iterationCount.set(0);
    }
}
