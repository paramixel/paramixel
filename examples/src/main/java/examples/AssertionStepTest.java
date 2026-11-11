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
import static org.paramixel.api.exception.FailException.fail;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates assertion-style steps that fail through the framework failure signal.
 *
 * <p>A failing step records a {@code FAILED} status with its cause. The result tree is the source of truth, so a
 * reporter never has to inspect exception messages to decide what happened.
 */
public class AssertionStepTest {

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        return sequential("assertion-steps")
                .step("one-equals-one", () -> {
                    if (1 != 1) {
                        throw fail("one is not equal to one");
                    }
                })
                .step("two-is-even", () -> {
                    final var value = 2;
                    if (value % 2 != 0) {
                        throw fail("expected an even value but was " + value);
                    }
                })
                .step("assertj-style", () -> assertThat("paramixel").startsWith("param"))
                .build();
    }
}
