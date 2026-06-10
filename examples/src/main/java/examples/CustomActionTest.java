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

import static org.paramixel.api.Context.withInstance;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.Objects;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates using provided {@link Step} actions with a fixture instance managed by {@link org.paramixel.api.action.Instance}.
 */
public class CustomActionTest {

    /**
     * The value set by the step and validated by the sibling step.
     */
    String value;

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree where one step sets a value on the fixture instance and a sibling step validates it.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        var testName = CustomActionTest.class.getName();

        return instance(testName, CustomActionTest::new)
                .body(sequential("body")
                        .child(step("test", withInstance(CustomActionTest.class, CustomActionTest::setValue)))
                        .child(step("validate", withInstance(CustomActionTest.class, CustomActionTest::validate))))
                .build();
    }

    /**
     * Sets the expected value on this fixture instance.
     */
    public void setValue() {
        value = "set by Step";
    }

    /**
     * Asserts that the step set the expected value on this fixture instance.
     */
    public void validate() {
        if (!Objects.equals(value, "set by Step")) {
            throw new AssertionError("Expected <set by Step> but was <" + value + ">");
        }
    }
}
