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

package test;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates class-level disabling of Paramixel tests.
 */
@Paramixel.TestClass
@Paramixel.Disabled
public class DisabledTest {

    /**
     * Supplies arguments for parameterized execution.
     *
     * @param argumentSupplierContext the argument supplier context
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        System.out.println("[ARGUMENT_SUPPLIER] Providing arguments for test methods");
        argumentSupplierContext.setParallelism(1);
        argumentSupplierContext.addArguments("Argument 1", "Argument 2", "Argument 3");
    }

    /**
     * Initializes class-level resources.
     *
     * @param context the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        System.out.println("[INITIALIZE] Test class: " + context.getTestClass().getName());
        System.out.println("[INITIALIZE] Test instance: " + context.getTestInstance());
    }

    /**
     * Executes before all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        System.out.println("[BEFORE_ALL] Executing before all test methods");
        System.out.println("[BEFORE_ALL] Argument: " + context.getArgument());
    }

    /**
     * Executes before each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        System.out.println("[BEFORE_EACH] Before test execution");
        System.out.println("[BEFORE_EACH] Argument: " + context.getArgument());
    }

    /**
     * Executes the test method with a display name.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    @Paramixel.DisplayName("Display Name Test Method")
    public void testMethod(final @NonNull ArgumentContext context) {
        Object argument = context.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Executes after each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        System.out.println("[AFTER_EACH] After test execution");
        System.out.println("[AFTER_EACH] Argument: " + context.getArgument());
    }

    /**
     * Executes after all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        System.out.println("[AFTER_ALL] Executing after all test methods");
        System.out.println("[AFTER_ALL] Argument: " + context.getArgument());
    }

    /**
     * Finalizes class-level resources after all tests complete.
     *
     * @param context the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        System.out.println(
                "[FINALIZE] Test class completed: " + context.getTestClass().getName());
    }
}
