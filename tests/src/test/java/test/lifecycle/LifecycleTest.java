/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package test.lifecycle;

import java.util.Arrays;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates Paramixel lifecycle hooks with multiple test methods and arguments.
 */
@Paramixel.TestClass
public class LifecycleTest {

    /**
     * Supplies arguments for parameterized lifecycle execution.
     *
     * @return the argument collection for test invocations
     */
    @Paramixel.ArgumentSupplier(parallelism = 1)
    public static Collection<String> arguments() {
        System.out.println("[ARGUMENT_SUPPLIER] Providing arguments for test methods");
        return Arrays.asList("Argument 1", "Argument 2", "Argument 3");
    }

    /**
     * Initializes class-level resources.
     *
     * @param classContext the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext classContext) {
        System.out.println(
                "[INITIALIZE] Test class: " + classContext.getTestClass().getName());
        System.out.println("[INITIALIZE] Test instance: " + classContext.getTestInstance());
    }

    /**
     * Executes before all tests for each argument.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        System.out.println("[BEFORE_ALL] Executing before all test methods");
        System.out.println("[BEFORE_ALL] Argument: " + argumentContext.getArgument());
    }

    /**
     * Executes before each test invocation.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        System.out.println("[BEFORE_EACH] Before test execution");
        System.out.println("[BEFORE_EACH] Argument: " + argumentContext.getArgument());
    }

    /**
     * Test method variant 1.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void testMethod1(final @NonNull ArgumentContext argumentContext) {
        Object argument = argumentContext.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Test method variant 2.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void testMethod2(final @NonNull ArgumentContext argumentContext) {
        Object argument = argumentContext.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Test method variant 3.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void testMethod3(final @NonNull ArgumentContext argumentContext) {
        Object argument = argumentContext.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Executes after each test invocation.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        System.out.println("[AFTER_EACH] After test execution");
        System.out.println("[AFTER_EACH] Argument: " + argumentContext.getArgument());
    }

    /**
     * Executes after all tests for each argument.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        System.out.println("[AFTER_ALL] Executing after all test methods");
        System.out.println("[AFTER_ALL] Argument: " + argumentContext.getArgument());
    }

    /**
     * Finalizes class-level resources after all tests complete.
     *
     * @param classContext the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        System.out.println("[FINALIZE] Test class completed: "
                + classContext.getTestClass().getName());
    }
}
