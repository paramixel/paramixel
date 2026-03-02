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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Validates lifecycle ordering by recording and asserting invocation counts.
 */
@Paramixel.TestClass
public class LifecycleValidationTest {

    /**
     * Captures lifecycle events by annotation name.
     */
    private static final Map<String, List<String>> stateMap = new HashMap<>();

    /**
     * Supplies arguments for parameterized execution.
     *
     * @param argumentSupplierContext the argument supplier context
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        stateMap.computeIfAbsent("@Paramixel.ArgumentSupplier", k -> new ArrayList<>())
                .add("@Paramixel.ArgumentSupplier");
        System.out.println("[ARGUMENT_SUPPLIER] Providing arguments for test methods");
        argumentSupplierContext.addArguments("Argument 1", "Argument 2", "Argument 3");
    }

    /**
     * Initializes class-level resources.
     *
     * @param context the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        stateMap.computeIfAbsent("@Paramixel.Initialize", k -> new ArrayList<>())
                .add("@Paramixel.Initialize");
        System.out.println("[INITIALIZE] Test class: " + context.getTestClass().getName());
        System.out.println("[INITIALIZE] Test instance: " + context.getTestInstance());
    }

    /**
     * Records the before-all lifecycle event.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.BeforeAll", k -> new ArrayList<>()).add("@Paramixel.BeforeAll");
        System.out.println("[BEFORE_ALL] Executing before all test methods");
        System.out.println("[BEFORE_ALL] Argument: " + context.getArgument());
    }

    /**
     * Records the before-each lifecycle event.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.BeforeEach", k -> new ArrayList<>())
                .add("@Paramixel.BeforeEach");
        System.out.println("[BEFORE_EACH] Before test execution");
        System.out.println("[BEFORE_EACH] Argument: " + context.getArgument());
    }

    /**
     * Records the first test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testMethod1(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.Test", k -> new ArrayList<>()).add("@Paramixel.Test");
        Object argument = context.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Records the second test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testMethod2(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.Test", k -> new ArrayList<>()).add("@Paramixel.Test");
        Object argument = context.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Records the third test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void testMethod3(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.Test", k -> new ArrayList<>()).add("@Paramixel.Test");
        Object argument = context.getArgument();
        System.out.println("[TEST_METHOD] Executing test with argument: " + argument);
        if (argument != null) {
            System.out.println(
                    "[TEST_METHOD] Argument class: " + argument.getClass().getName());
        }
    }

    /**
     * Records the after-each lifecycle event.
     *
     * @param context the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.AfterEach", k -> new ArrayList<>()).add("@Paramixel.AfterEach");
        System.out.println("[AFTER_EACH] After test execution");
        System.out.println("[AFTER_EACH] Argument: " + context.getArgument());
    }

    /**
     * Records the after-all lifecycle event.
     *
     * @param context the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        stateMap.computeIfAbsent("@Paramixel.AfterAll", k -> new ArrayList<>()).add("@Paramixel.AfterAll");
        System.out.println("[AFTER_ALL] Executing after all test methods");
        System.out.println("[AFTER_ALL] Argument: " + context.getArgument());
    }

    /**
     * Asserts lifecycle counts after all execution completes.
     *
     * @param context the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        stateMap.computeIfAbsent("@Paramixel.Finalize", k -> new ArrayList<>()).add("@Paramixel.Finalize");
        System.out.println(
                "[FINALIZE] Test class completed: " + context.getTestClass().getName());

        assertThat(stateMap.get("@Paramixel.ArgumentSupplier"))
                .as("ArgumentSupplier list")
                .isNotNull()
                .hasSize(1);
        assertThat(stateMap.get("@Paramixel.Initialize"))
                .as("Initialize list")
                .isNotNull()
                .hasSize(1);
        assertThat(stateMap.get("@Paramixel.BeforeAll"))
                .as("BeforeAll list")
                .isNotNull()
                .hasSize(3);
        assertThat(stateMap.get("@Paramixel.BeforeEach"))
                .as("BeforeEach list")
                .isNotNull()
                .hasSize(9);
        assertThat(stateMap.get("@Paramixel.Test")).as("Test list").isNotNull().hasSize(9);
        assertThat(stateMap.get("@Paramixel.AfterEach"))
                .as("AfterEach list")
                .isNotNull()
                .hasSize(9);
        assertThat(stateMap.get("@Paramixel.AfterAll"))
                .as("AfterAll list")
                .isNotNull()
                .hasSize(3);
        assertThat(stateMap.get("@Paramixel.Finalize"))
                .as("Finalize list")
                .isNotNull()
                .hasSize(1);
    }
}
