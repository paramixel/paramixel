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

package examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.sequential;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;
import org.paramixel.api.exception.FailException;

/**
 * Exercises the builder vocabulary in one definition: named steps, a parallel group, repetition, a timeout, a
 * condition, an exclusive lock, lifecycle callbacks, and assertion steps.
 */
public class NamedBuilderExample {

    private static final AtomicInteger stepCounter = new AtomicInteger();
    private static final AtomicInteger parallelCounter = new AtomicInteger();
    private static final AtomicInteger repeatCounter = new AtomicInteger();

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        reset();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        reset();

        return sequential("NamedBuilderExample")
                .child(sequential("suite")
                        .step("step-demo", stepCounter::incrementAndGet)
                        .parallel(
                                "parallel-demo",
                                Pool.named("examples"),
                                group -> group.step("branch-1", parallelCounter::incrementAndGet)
                                        .step("branch-2", parallelCounter::incrementAndGet)
                                        .step("branch-3", parallelCounter::incrementAndGet))
                        .repeat(
                                "repeat-demo",
                                repeat -> repeat.maxIterations(3).step("repeated", repeatCounter::incrementAndGet))
                        .step("sleep-demo", () -> Thread.sleep(10L))
                        .timeout(Duration.ofSeconds(5))
                        .when(
                                "conditional-demo",
                                context -> true,
                                group -> group.step("runs-when-true", () -> {
                                    // Intentionally empty
                                }))
                        .isolated("test-lock")
                        .step("serialized", () -> {
                            // Intentionally empty
                        })
                        .before(() -> {
                            // Intentionally empty
                        })
                        .after(() -> {
                            // Intentionally empty
                        })
                        .step("true-is-true", () -> {
                            final boolean actual = 1 == 1;
                            if (!actual) {
                                throw FailException.fail("expected true");
                            }
                        }))
                .step("validate", () -> {
                    assertThat(stepCounter.get()).isGreaterThanOrEqualTo(1);
                    assertThat(parallelCounter.get()).isEqualTo(3);
                    assertThat(repeatCounter.get()).isEqualTo(3);
                })
                .build();
    }

    private static void reset() {
        stepCounter.set(0);
        parallelCounter.set(0);
        repeatCounter.set(0);
    }
}
