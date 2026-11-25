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

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates two steps exchanging state through the context scope.
 *
 * <p>The steps are siblings in a sequential chain, so they share one scope: what {@code setValue} stores is what
 * {@code validate} reads, with no fixture object and no captured variable in between. The value is dropped when the
 * scope ends — the framework does not close anything, it just stops holding the reference.
 */
public class CustomActionTest {

    private static final String ID_SET_VALUE = "setValue";
    private static final String ID_VALIDATE = "validate";

    private static final String VALUE = "value";

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
        return sequential(CustomActionTest.class, "CustomActionTest")
                .step(ID_SET_VALUE)
                .step(ID_VALIDATE)
                .build();
    }

    /**
     * Stores the value for the next sibling.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_VALUE)
    public static void setValue(final Context context) {
        context.put(VALUE, "set by a step");
    }

    /**
     * Reads the value stored by the previous sibling.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validate(final Context context) {
        assertThat(context.require(VALUE, String.class)).isEqualTo("set by a step");
    }
}
