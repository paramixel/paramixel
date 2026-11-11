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
import static org.paramixel.api.action.Conditional.conditional;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Loop.loop;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;
import static org.paramixel.api.exception.FailException.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates the named builder static factories for constructing action trees.
 *
 * <p>Each composite action provides a public static method named after the action
 * (e.g., {@code scope("name")}, {@code sequential("name")}) that returns the
 * corresponding {@code Builder}.
 *
 * <p>Contrast with {@link CustomActionTest} and {@link SequentialIterationExample}
 * to see the same patterns expressed with typed builders.
 */
public class NamedBuilderExample {

    private static final AtomicInteger stepCounter = new AtomicInteger();
    private static final AtomicInteger parallelCounter = new AtomicInteger();
    private static final AtomicInteger repeatCounter = new AtomicInteger();

    /**
     * Entry point.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        reset();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds an action tree using the named builder factories.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        reset();
        var testName = NamedBuilderExample.class.getName();

        return scope(testName)
                .body(sequential("tests")
                        .child(sequential("suite")
                                .child(step("step-demo", context -> stepCounter.incrementAndGet()))
                                .child(parallel("parallel-demo", "examples")
                                        .child(step("branch-1", context -> parallelCounter.incrementAndGet()))
                                        .child(step("branch-2", context -> parallelCounter.incrementAndGet()))
                                        .child(step("branch-3", context -> parallelCounter.incrementAndGet())))
                                .child(loop("repeat-demo")
                                        .body(step("repeated", context -> repeatCounter.incrementAndGet()))
                                        .maxIterations(3))
                                .child(step("sleep-demo", context -> Thread.sleep(10)))
                                .child(timeout("timeout-demo")
                                        .timeout(Duration.ofSeconds(1))
                                        .body(step("within-timeout", context -> {})))
                                .child(conditional("conditional-demo", context -> true)
                                        .body(step("runs-when-true", context -> {})))
                                .child(isolated("isolated-demo", "test-lock").body(step("serialized", context -> {})))
                                .child(scope("lifecycle-demo")
                                        .before(step("init", context -> {}))
                                        .body(step("body", context -> {}))
                                        .after(step("cleanup", context -> {})))
                                .child(sequential("assert-demo")
                                        .child(step("true-is-true", context -> {
                                            boolean actual = 1 == 1;
                                            if (!actual) {
                                                throw fail("expected true");
                                            }
                                        }))
                                        .child(step("false-is-false", context -> {
                                            boolean actual = 1 == 2;
                                            if (actual) {
                                                throw fail("expected false");
                                            }
                                        }))))
                        .child(step("validate", context -> {
                            assertThat(stepCounter.get()).isGreaterThanOrEqualTo(1);
                            assertThat(parallelCounter.get()).isEqualTo(3);
                            assertThat(repeatCounter.get()).isEqualTo(3);
                        })))
                .build();
    }

    private static void reset() {
        stepCounter.set(0);
        parallelCounter.set(0);
        repeatCounter.set(0);
    }
}
