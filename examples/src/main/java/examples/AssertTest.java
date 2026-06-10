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

import static org.paramixel.api.action.Assert.assertFalse;
import static org.paramixel.api.action.Assert.assertThat;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Sequential.sequential;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates the {@link org.paramixel.api.action.Assert} action with static boolean values, lazy boolean
 * suppliers, and message-bearing overloads. Verifies that each assert action
 * completes with PASSED status.
 */
public class AssertTest {

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree that exercises {@link org.paramixel.api.action.Assert#assertTrue},
     * {@link org.paramixel.api.action.Assert#assertFalse}, and {@link org.paramixel.api.action.Assert#assertThat} overloads
     * with passing values.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        return sequential("assert-example")
                .child(assertTrue("assert-true", true))
                .child(assertThat("assert-true-with-message", true, true, "should not appear"))
                .child(assertTrue("assert-true-supplier", () -> true))
                .child(assertThat("assert-true-supplier-with-message", true, () -> true, "should not appear"))
                .child(assertFalse("assert-false", false))
                .child(assertThat("assert-false-with-message", false, false, "should not appear"))
                .child(assertFalse("assert-false-supplier", () -> false))
                .child(assertThat("assert-false-supplier-with-message", false, () -> false, "should not appear"))
                .build();
    }
}
