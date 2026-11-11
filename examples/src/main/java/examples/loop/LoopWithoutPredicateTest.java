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
import static org.paramixel.api.Tasks.sequential;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates bounded repetition: the subtree runs exactly the configured number of iterations.
 *
 * <p>Repeat is not retry. Replay is intentional, the bound is mandatory, and every iteration is a distinct attempt in
 * the result tree.
 */
public class LoopWithoutPredicateTest {

    private static final AtomicInteger dependentCount = new AtomicInteger();
    private static final AtomicInteger independentCount = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();
        return sequential("loop-example")
                .repeat(
                        "dependent-loop",
                        repeat -> repeat.maxIterations(3).step("step", dependentCount::incrementAndGet))
                .repeat(
                        "independent-loop",
                        repeat -> repeat.maxIterations(3).step("step", independentCount::incrementAndGet))
                .step("validate", () -> {
                    assertThat(dependentCount.get()).isEqualTo(3);
                    assertThat(independentCount.get()).isEqualTo(3);
                })
                .build();
    }

    private static void resetCounts() {
        dependentCount.set(0);
        independentCount.set(0);
    }
}
