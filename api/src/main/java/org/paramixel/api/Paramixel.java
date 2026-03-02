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

package org.paramixel.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the annotation-based programming model for the Paramixel testing framework.
 *
 * <p>This class serves as the central API entry point, providing annotations that enable
 * developers to define test classes, test methods, lifecycle hooks, and argument suppliers.
 * The annotations follow a lifecycle-based approach similar to JUnit Jupiter, with support
 * for parameterized testing through argument suppliers.</p>
 *
 * <p><b>Core Annotations:</b></p>
 * <ul>
 *   <li>{@link TestClass} - Marks a class as containing test methods</li>
 *   <li>{@link Test} - Marks a method as a test case</li>
 *   <li>{@link ArgumentSupplier} - Provides data for parameterized tests</li>
 * </ul>
 *
 * <p><b>Lifecycle Annotations:</b></p>
 * <p>The framework defines a clear lifecycle for test execution:</p>
 * <ol>
 *   <li>Class-Level Setup (once per test class):
 *     <ul>
 *       <li>{@link Initialize} - Initial class preparation</li>
 *       <li>{@link BeforeAll} - Setup before all test methods</li>
 *     </ul>
 *   </li>
 *   <li>Per-Invocation Setup (for each test method invocation):
 *     <ul>
 *       <li>{@link BeforeEach} - Setup before each test</li>
 *       <li>{@link Test} - The test method itself</li>
 *       <li>{@link AfterEach} - Cleanup after each test</li>
 *     </ul>
 *   </li>
 *   <li>Class-Level Teardown (once per test class):
 *     <ul>
 *       <li>{@link AfterAll} - Cleanup after all test methods</li>
 *       <li>{@link Finalize} - Final class cleanup (guaranteed)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Utility Annotations:</b></p>
 * <ul>
 *   <li>{@link Disabled} - Temporarily disables a test or class</li>
 *   <li>{@link DisplayName} - Specifies a custom display name</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Paramixel.TestClass
 * @Paramixel.DisplayName("User Authentication Tests")
 * public class AuthenticationTests {
 *
 *     @Paramixel.Initialize
 *     public void initialize(ClassContext context) {
 *         // One-time initialization
 *     }
 *
 *     @Paramixel.BeforeAll
 *     public void setupAll(ArgumentContext context) {
 *         // Setup shared resources
 *     }
 *
 *     @Paramixel.BeforeEach
 *     public void setupEach(ArgumentContext context) {
 *         // Per-test setup
 *     }
 *
 *     @Paramixel.Test
 *     @Paramixel.DisplayName("Valid user login")
 *     public void testValidLogin(ArgumentContext context) {
 *         // Test implementation
 *     }
 *
 *     @Paramixel.AfterEach
 *     public void teardownEach(ArgumentContext context) {
 *         // Per-test cleanup
 *     }
 *
 *     @Paramixel.AfterAll
 *     public void teardownAll(ArgumentContext context) {
 *         // Shared resource cleanup
 *     }
 *
 *     @Paramixel.Finalize
 *     public void finalize(ClassContext context) {
 *         // Guaranteed final cleanup
 *     }
 * }
 * }</pre>
 *
 * @see TestClass
 * @see Test
 * @see ArgumentContext
 * @see ClassContext
 * @see EngineContext
 * @since 0.0.1
 */
public class Paramixel {

    /**
     * Marks a method to be invoked once after all test methods within a test class have completed.
     *
     * <p>This annotation designates a teardown method that executes exactly once after all test
     * methods in the class have finished. The annotated method receives an {@link ArgumentContext}
     * parameter providing access to the current execution context.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ArgumentContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>May be {@code static} or instance method (instance recommended for resource cleanup)</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes exactly once per test class</li>
     *   <li>Executes after all test method invocations complete</li>
     *   <li>Executes after all {@link AfterEach} methods for all invocations</li>
     *   <li>Always executes if {@link BeforeAll} executed (lifecycle pairing guarantee)</li>
     *   <li>Exceptions are logged but not propagated to ensure cleanup completion</li>
     * </ul>
     *
     * <p><b>Lifecycle Ordering:</b></p>
     * <ol>
     *   <li>{@link Initialize}</li>
     *   <li>{@link BeforeAll}</li>
     *   <li>Test method execution ({@link BeforeEach} → {@link Test} → {@link AfterEach})</li>
     *   <li>{@code @AfterAll}</li>
     *   <li>{@link Finalize}</li>
     * </ol>
     *
     * @see BeforeAll
     * @see Initialize
     * @see Finalize
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterAll {}

    /**
     * Marks a method to be invoked after each individual test method invocation.
     *
     * <p>This annotation designates a teardown method that executes immediately after each
     * test method invocation. The annotated method receives an {@link ArgumentContext}
     * parameter providing access to the current invocation's context and argument.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ArgumentContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>Must be an instance method (cannot be {@code static})</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes after every test method invocation</li>
     *   <li>Always executes if {@link BeforeEach} executed for the same invocation</li>
     *   <li>Executes even if the test method throws an exception</li>
     *   <li>Receives the {@link ArgumentContext} with the current invocation's argument</li>
     *   <li>Exceptions are logged but not propagated</li>
     * </ul>
     *
     * <p><b>Per-Invocation Lifecycle:</b></p>
     * <ol>
     *   <li>{@link BeforeEach}</li>
     *   <li>{@link Test}</li>
     *   <li>{@code @AfterEach}</li>
     * </ol>
     *
     * @see BeforeEach
     * @see Test
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterEach {}

    /**
     * Marks a method as a supplier of test arguments for parameterized test execution.
     *
     * <p>This annotation enables data-driven testing by designating a method that provides
     * arguments for test method invocations. Each argument returned by the supplier results
     * in a separate invocation of all test methods in the class.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public static}</li>
     *   <li>Must accept zero parameters</li>
     *   <li>Return type must be one of:
     *     <ul>
     *       <li>{@link java.util.stream.Stream}</li>
     *       <li>{@link java.util.Collection}</li>
     *       <li>{@link java.lang.Iterable}</li>
     *       <li>Array ({@code Object[]})</li>
     *       <li>Single {@code Object} (treated as a single-element collection)</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><b>Constraints:</b></p>
     * <ul>
     *   <li>Only one argument supplier allowed per test class</li>
     *   <li>Invoked during the preparation phase before test execution</li>
     * </ul>
     *
     * @see Test
     * @see Named
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ArgumentSupplier {}

    /**
     * Marks a method to be invoked once before all test methods within a test class.
     *
     * <p>This annotation designates a setup method that executes exactly once before any
     * test methods in the class. The annotated method receives an {@link ArgumentContext}
     * parameter providing access to the execution context.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ArgumentContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>May be {@code static} or instance method</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes exactly once per test class</li>
     *   <li>Executes after {@link Initialize} but before any test methods</li>
     *   <li>Exceptions cause immediate test failure</li>
     *   <li>Paired with {@link AfterAll} (always executes if this method executes)</li>
     * </ul>
     *
     * @see AfterAll
     * @see Initialize
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BeforeAll {}

    /**
     * Marks a method to be invoked before each individual test method invocation.
     *
     * <p>This annotation designates a setup method that executes immediately before each
     * test method invocation. The annotated method receives an {@link ArgumentContext}
     * parameter providing access to the current invocation's context and argument.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ArgumentContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>Must be an instance method (cannot be {@code static})</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes before every test method invocation</li>
     *   <li>Executes after arguments are provided (if {@link ArgumentSupplier} present)</li>
     *   <li>Receives the {@link ArgumentContext} with the current invocation's argument</li>
     *   <li>Exceptions cause the corresponding test invocation to fail</li>
     *   <li>Paired with {@link AfterEach} (always executes if this method executes)</li>
     * </ul>
     *
     * @see AfterEach
     * @see Test
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BeforeEach {}

    /**
     * Disables a test class or test method from execution.
     *
     * <p>This annotation marks test elements as disabled, preventing their execution while
     * maintaining them in the codebase. Useful for temporarily skipping tests that are
     * broken, incomplete, or require specific environmental conditions.</p>
     *
     * <p>When applied to a class, all test methods within that class are disabled.
     * When applied to a method, only that specific method is disabled.</p>
     *
     * @see TestClass
     * @see Test
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Disabled {

        /**
         * The reason for disabling the test element.
         *
         * <p>This optional value may be displayed in test reports and should explain
         * why the test is disabled (e.g., "Pending bug fix", "Requires database").</p>
         *
         * @return the reason for disabling, or empty string if not specified
         */
        String value() default "";
    }

    /**
     * Specifies a custom display name for a test class or test method.
     *
     * <p>This annotation overrides the default display name (class or method name) that
     * appears in test reports and IDE test runners. Custom display names can include
     * spaces and special characters for improved readability.</p>
     *
     * <p>When applied to a class, the display name represents the test class.
     * When applied to a method, the display name represents that specific test.</p>
     *
     * @see TestClass
     * @see Test
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface DisplayName {

        /**
         * The custom display name.
         *
         * @return the display name to use in test reports
         */
        String value();
    }

    /**
     * Marks a method to be invoked once after all test execution has completed.
     *
     * <p>This annotation designates a finalization method that executes exactly once
     * after all test methods and lifecycle hooks have completed. Unlike {@link AfterAll},
     * this method is guaranteed to execute even if test execution fails or is aborted.</p>
     *
     * <p>The annotated method receives a {@link ClassContext} parameter providing access
     * to class-level information and execution results.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ClassContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>Must be an instance method (cannot be {@code static})</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes exactly once per test class</li>
     *   <li>Executes after {@link AfterAll}</li>
     *   <li>Always executes if class instantiation began (guaranteed)</li>
     *   <li>Exceptions are logged but not propagated</li>
     * </ul>
     *
     * @see Initialize
     * @see BeforeAll
     * @see AfterAll
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Finalize {}

    /**
     * Specifies an explicit execution order for test methods within a test class.
     *
     * <p>This annotation allows developers to define a deterministic ordering for
     * test methods when the natural discovery order is insufficient. Lower values
     * are executed first. When multiple methods have the same order value, their
     * relative execution order is not guaranteed.</p>
     *
     * <p><b>Usage Guidelines:</b></p>
     * <ul>
     *   <li>Use sparingly; prefer isolated tests without ordering dependencies.</li>
     *   <li>Applies only to methods annotated with {@link Test}.</li>
     *   <li>Does not affect lifecycle methods such as {@link BeforeEach} or {@link AfterEach}.</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Paramixel.Test
     * @Paramixel.Order(1)
     * public void shouldCreateEntity(ArgumentContext context) { ... }
     *
     * @Paramixel.Test
     * @Paramixel.Order(2)
     * public void shouldDeleteEntity(ArgumentContext context) { ... }
     * }</pre>
     *
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Order {

        /**
         * The order value used to sort test methods.
         *
         * @return the order value
         */
        int value();
    }

    /**
     * Marks a method to be invoked once before any test methods or lifecycle hooks.
     *
     * <p>This annotation designates an initialization method that executes exactly once
     * before any other lifecycle methods or test methods. The annotated method receives
     * a {@link ClassContext} parameter providing access to class-level information.</p>
     *
     * <p>This is the first lifecycle method to execute and is suitable for one-time
     * setup that must occur before {@link BeforeAll}.</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ClassContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>Must be an instance method (cannot be {@code static})</li>
     * </ul>
     *
     * <p><b>Execution Guarantees:</b></p>
     * <ul>
     *   <li>Executes exactly once per test class</li>
     *   <li>Executes before {@link BeforeAll}</li>
     *   <li>Exceptions cause immediate test failure</li>
     * </ul>
     *
     * @see Finalize
     * @see BeforeAll
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Initialize {}

    /**
     * Marks a method as a test case to be executed by the framework.
     *
     * <p>This annotation designates a method as a test case. The framework discovers
     * and executes all methods annotated with {@code @Test}. Each test method receives
     * an {@link ArgumentContext} parameter providing access to the current invocation's
     * context and argument (if provided by an {@link ArgumentSupplier}).</p>
     *
     * <p><b>Method Signature Requirements:</b></p>
     * <ul>
     *   <li>Must be declared {@code public}</li>
     *   <li>Must accept exactly one parameter of type {@link ArgumentContext}</li>
     *   <li>Return type must be {@code void}</li>
     *   <li>Must be an instance method (cannot be {@code static})</li>
     * </ul>
     *
     * <p><b>Execution Behavior:</b></p>
     * <ul>
     *   <li>Discovered during the test discovery phase</li>
     *   <li>Executed according to the test class lifecycle</li>
     *   <li>With {@link ArgumentSupplier}: executed once per argument</li>
     *   <li>Without {@link ArgumentSupplier}: executed once with null argument</li>
     *   <li>Exceptions indicate test failure</li>
     * </ul>
     *
     * @see TestClass
     * @see ArgumentSupplier
     * @see BeforeEach
     * @see AfterEach
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @org.junit.platform.commons.annotation.Testable
    public @interface Test {}

    /**
     * Marks a class as containing test methods to be executed by the framework.
     *
     * <p>This annotation identifies classes that contain test methods and lifecycle hooks.
     * Only classes annotated with {@code @TestClass} are discovered and executed by the
     * Paramixel test engine.</p>
     *
     * <p>The annotation serves as the entry point for test discovery, enabling the
     * framework to distinguish test classes from support classes and utilities.</p>
     *
     * @see Test
     * @since 0.0.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TestClass {}
}
