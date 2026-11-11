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
 * Demonstrates repetition with an explicit stop predicate.
 *
 * <p>The predicate is evaluated after each iteration with the iteration status, and the iteration bound still applies,
 * so a predicate that never becomes true cannot create unbounded work.
 */
public class LoopWithPredicateTest {

    private static final AtomicInteger pollCount = new AtomicInteger();

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
        return sequential("loop-predicate-example")
                .repeat(
                        "poll",
                        repeat -> repeat.maxIterations(10)
                                .until((context, status) -> pollCount.get() >= 4)
                                .step("check", pollCount::incrementAndGet))
                .step("validate", () -> assertThat(pollCount.get()).isEqualTo(4))
                .build();
    }

    private static void resetCounts() {
        pollCount.set(0);
    }
}
