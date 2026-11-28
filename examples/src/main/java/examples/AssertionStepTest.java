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
import static org.paramixel.api.exception.FailException.fail;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;

/**
 * Demonstrates assertion-style steps that fail through the framework failure signal.
 *
 * <p>Each step is an ordinary static method named by a stable identifier. A failing step records a {@code FAILED}
 * status with its cause, and the report labels the failure with the identifier, so a reporter never has to inspect
 * exception messages to decide what happened.
 */
public class AssertionStepTest {

    private static final String ID_ONE_EQUALS_ONE = "one-equals-one";
    private static final String ID_TWO_IS_EVEN = "two-is-even";
    private static final String ID_ASSERTJ_STYLE = "assertj-style";

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
    @Paramixel.TaskFactory
    public static Task factory() {
        return Sequential.builder(AssertionStepTest.class, "assertion-steps")
                .step(ID_ONE_EQUALS_ONE)
                .step(ID_TWO_IS_EVEN)
                .step(ID_ASSERTJ_STYLE)
                .build();
    }

    /**
     * A comparison that holds.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_ONE_EQUALS_ONE)
    public static void oneEqualsOne(final Context context) {
        if (1 != 1) {
            throw fail("one is not equal to one");
        }
    }

    /**
     * A computed assertion that holds.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TWO_IS_EVEN)
    public static void twoIsEven(final Context context) {
        final var value = 2;
        if (value % 2 != 0) {
            throw fail("expected an even value but was " + value);
        }
    }

    /**
     * An AssertJ-style assertion: AssertJ throws, and the framework records the failure.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_ASSERTJ_STYLE)
    public static void assertjStyle(final Context context) {
        assertThat("paramixel").startsWith("param");
    }
}
