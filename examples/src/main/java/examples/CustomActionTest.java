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

import static org.paramixel.api.Tasks.forInstance;

import java.util.Objects;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates a stateful fixture whose steps exchange state through the instance.
 */
public class CustomActionTest {

    private String value;

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
        return forInstance(CustomActionTest.class.getName(), CustomActionTest::new)
                .step("setValue", CustomActionTest::setValue)
                .step("validate", CustomActionTest::validate)
                .build();
    }

    /**
     * No-argument constructor used by the framework.
     */
    public CustomActionTest() {
        // Intentionally empty
    }

    /**
     * Sets the shared state.
     */
    public void setValue() {
        value = "set by a step";
    }

    /**
     * Verifies the shared state.
     */
    public void validate() {
        if (!Objects.equals(value, "set by a step")) {
            throw new AssertionError("Expected <set by a step> but was <" + value + ">");
        }
    }
}
