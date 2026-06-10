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

package examples.repeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Repeat.repeat;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates the {@link org.paramixel.api.action.Repeat} action with dependent and independent
 * repetitions. Verifies that the child action executes exactly the
 * configured number of times.
 */
public class RepeatTest {

    private static final AtomicInteger dependentCount = new AtomicInteger();
    private static final AtomicInteger independentCount = new AtomicInteger();

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
     * Builds an action tree that exercises dependent and independent repeat.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        resetCounts();
        return sequential("repeat-example")
                .child(repeat("dependent-repeat")
                        .body(step("step", context -> dependentCount.incrementAndGet()))
                        .iterations(3))
                .child(repeat("independent-repeat")
                        .body(step("step", context -> independentCount.incrementAndGet()))
                        .iterations(3))
                .child(step("validate", context -> {
                    assertThat(dependentCount.get()).isEqualTo(3);
                    assertThat(independentCount.get()).isEqualTo(3);
                }))
                .build();
    }

    private static void resetCounts() {
        dependentCount.set(0);
        independentCount.set(0);
    }
}
