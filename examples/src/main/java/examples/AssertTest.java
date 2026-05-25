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

import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.AssertFalse;
import org.paramixel.api.action.AssertTrue;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Spec;

/**
 * Demonstrates the {@link AssertTrue} and {@link AssertFalse} actions with
 * static boolean conditions, lazy boolean suppliers, and message-bearing
 * overloads. Verifies that each assert action completes with PASSED status.
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
     * Builds an action tree that exercises all valid {@code AssertTrue.of} and
     * {@code AssertFalse.of} overloads with passing conditions.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Spec<?> factory() {
        return Sequential.of("assert-example")
                .child(AssertTrue.of("assert-true", true))
                .child(AssertTrue.of("assert-true-with-message", true, "should not appear"))
                .child(AssertTrue.of("assert-true-supplier", () -> true))
                .child(AssertTrue.of("assert-true-supplier-with-message", () -> true, "should not appear"))
                .child(AssertFalse.of("assert-false", false))
                .child(AssertFalse.of("assert-false-with-message", false, "should not appear"))
                .child(AssertFalse.of("assert-false-supplier", () -> false))
                .child(AssertFalse.of("assert-false-supplier-with-message", () -> false, "should not appear"));
    }
}
