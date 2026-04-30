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

package org.paramixel.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines annotations used to discover Paramixel actions.
 *
 * <p>This class provides annotations that enable automatic discovery and execution
 * of actions via classpath scanning. The {@link org.paramixel.core.discovery.Resolver}
 * scans the classpath for methods annotated with {@link ActionFactory} and creates
 * execution plans from them.</p>
 *
 * <h3>Discovery System</h3>
 * <p>The discovery system works by:
 * <ol>
 *   <li>Scanning the classpath for classes with annotated methods</li>
 *   <li>Identifying methods annotated with {@code @ActionFactory}</li>
 *   <li>Validating factory method requirements</li>
 *   <li>Invoking factory methods to obtain {@link Action} instances</li>
 *   <li>Combining discovered actions into a root action</li>
 * </ol>
 *
 * <h3>Factory Method Requirements</h3>
 * <p>Methods annotated with {@code @ActionFactory} must meet these requirements:
 * <ul>
 *   <li>Must be {@code public}</li>
 *   <li>Must be {@code static}</li>
 *   <li>Must have no parameters</li>
 *   <li>Must return {@link Action} or a subtype</li>
 *   <li>Must not return {@code null}</li>
 *   <li>Must not throw checked exceptions</li>
 * </ul>
 *
 * <p>Invalid factory methods throw {@link ResolverException} during resolution.</p>
 *
 * <h3>Package Scanning</h3>
 * <p>Actions can be discovered from:
 * <ul>
 *   <li>Entire classpath</li>
 *   <li>Specific packages via regex patterns</li>
 *   <li>Specific classes</li>
 *   <li>Custom {@link java.util.function.Predicate} filters</li>
 * </ul>
 *
 * <h3>Action Composition</h3>
 * <p>Discovered actions are combined using {@link org.paramixel.core.discovery.Resolver.Composition}:</p>
 * <ul>
 *   <li>{@code SEQUENTIAL} - Execute in alphabetical order by package and action name</li>
 *   <li>{@code PARALLEL} - Execute concurrently with default parallelism</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Defining Actions:</strong></p>
 * <pre>{@code
 * public class MyTests {
 *
 *     @ActionFactory
 *     public static Action createUser() {
 *         return Direct.of("createUser", ctx -> {
 *             // Test logic
 *         });
 *     }
 *
 *     @ActionFactory
 *     public static Action deleteUser() {
 *         return Direct.of("deleteUser", ctx -> {
 *             // Test logic
 *         });
 *     }
 *
 *     @ActionFactory
 *     @Disabled("Not implemented yet")
 *     public static Action updateUser() {
 *         return Direct.of("updateUser", ctx -> {
 *             // Test logic
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Resolving Actions:</strong></p>
 * <pre>{@code
 * // Resolve all actions from classpath
 * Optional<Action> root = Resolver.resolveActions();
 *
 * // Resolve from specific package
 * Optional<Action> root = Resolver.resolveActions("com.example.tests");
 *
 * // Resolve with custom selector
 * Optional<Action> root = Resolver.resolveActions(
 *     Selector.byPackageName(MyTests.class));
 *
 * // Resolve with specific composition mode
 * Optional<Action> root = Resolver.resolveActions(
 *     Resolver.Composition.PARALLEL);
 * }</pre>
 *
 * <p><strong>Executing Discovered Actions:</strong></p>
 * <pre>{@code
 * Optional<Action> root = Resolver.resolveActions();
 * if (root.isPresent()) {
 *     Runner.builder().build().run(root.get());
 * }
 * }</pre>
 *
 * <h3>Naming Conventions</h3>
 * <p>Action names should follow these conventions:
 * <ul>
 *   <li>Use descriptive names reflecting the test/action purpose</li>
 *   <li>Use present tense verbs (e.g., "createUser", "validateInput")</li>
 *   <li>Keep names concise but informative</li>
 *   <li>Avoid special characters</li>
 *   <li>Factory method names don't need to match action names</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>Invalid factory methods throw {@link ResolverException} with descriptive messages:
 * <ul>
 *   <li>"method must be public static"</li>
 *   <li>"method must have no parameters"</li>
 *   <li>"return type must be Action"</li>
 *   <li>"method returned null"</li>
 *   <li>Reflection invocation failures</li>
 * </ul>
 *
 * <h3>Performance Considerations</h3>
 * <ul>
 *   <li>Classpath scanning uses ClassGraph for efficient scanning</li>
 *   <li>Factory methods are invoked once during resolution</li>
 *   <li>Discovered actions are combined into a tree for efficient execution</li>
 *   <li>Use package filtering to reduce scanning scope</li>
 * </ul>
 *
 * @see ActionFactory
 * @see Disabled
 * @see org.paramixel.core.discovery.Resolver
 * @see org.paramixel.core.discovery.Selector
 * @see org.paramixel.core.discovery.Resolver.Composition
 */
public final class Paramixel {

    private Paramixel() {}

    /**
     * Marks a public static no-argument method as an action factory.
     *
     * <p>Methods annotated with {@code @ActionFactory} are automatically discovered
     * during classpath scanning and invoked to create {@link Action} instances.
     * The discovered actions are then combined into a root action for execution.</p>
     *
     * <h3>Requirements</h3>
     * <p>Factory methods must meet all of these requirements:</p>
     * <ul>
     *   <li>Visibility: Must be {@code public}</li>
     *   <li>Modifier: Must be {@code static}</li>
     *   <li>Parameters: Must have no parameters</li>
     *   <li>Return type: Must return {@link Action} or a subtype</li>
     *   <li>Return value: Must not return {@code null}</li>
     *   <li>Exceptions: Should not throw checked exceptions</li>
     * </ul>
     *
     * <p>Violating these requirements throws {@link ResolverException} during resolution.</p>
     *
     * <h3>Method Signature</h3>
     * <pre>{@code
     * @ActionFactory
     * public static Action myAction() {
     *     return Direct.of("myAction", ctx -> {
     *         // Action logic
     *     });
     * }
     * }</pre>
     *
     * <h3>Multiple Actions Per Class</h3>
     * <p>A single class can have multiple factory methods:
     * <pre>{@code
     * public class UserTests {
     *     @ActionFactory
     *     public static Action testCreate() { ... }
     *
     *     @ActionFactory
     *     public static Action testRead() { ... }
     *
     *     @ActionFactory
     *     public static Action testUpdate() { ... }
     *
     *     @ActionFactory
     *     public static Action testDelete() { ... }
     * }
     * }</pre>
     *
     * <h3>Return Types</h3>
     * <p>The return type can be {@link Action} or any subtype:</p>
     * <pre>{@code
     * @ActionFactory
     * public static Action asAction() {
     *     return Direct.of("test", ctx -> {});
     * }
     *
     * @ActionFactory
     * public static Direct asDirect() {
     *     return Direct.of("test", ctx -> {});
     * }
     *
     * @ActionFactory
     * public static Sequential asSequential() {
     *     return Sequential.of("tests", child1, child2);
     * }
     * }</pre>
     *
     * <h3>Exclusion from Discovery</h3>
     * <p>Combine with {@link Disabled} to temporarily exclude actions:</p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("Work in progress")
     * public static Action futureFeature() {
     *     return Direct.of("futureFeature", ctx -> {
     *         // Not yet implemented
     *     });
     * }
     * }</pre>
     *
     * <h3>Common Patterns</h3>
     *
     * <p><strong>Simple Test:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * public static Action validateEmail() {
     *     return Direct.of("validateEmail", ctx -> {
     *         String email = "test@example.com";
     *         assertTrue(email.contains("@"));
     *     });
     * }
     * }</pre>
     *
     * <p><strong>Test with Setup:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * public static Action databaseTest() {
     *     return Lifecycle.of("databaseTest",
     *         Direct.of("setup", this::setupDatabase),
     *         Direct.of("test", this::runTest),
     *         Direct.of("teardown", this::cleanupDatabase)
     *     );
     * }
     * }</pre>
     *
     * <p><strong>Test Suite:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * public static Action userCrudTests() {
     *     return Sequential.of("userCrudTests",
     *         createUser(),
     *         readUser(),
     *         updateUser(),
     *         deleteUser()
     *     );
     * }
     * }</pre>
     *
     * @see Disabled
     * @see org.paramixel.core.discovery.Resolver
     * @see Action
     * @see ResolverException
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ActionFactory {}

    /**
     * Excludes an action factory method from discovery.
     *
     * <p>When applied to an {@code @ActionFactory} method, this annotation prevents
     * the method from being discovered and included in the execution plan. This is
     * useful for temporarily disabling tests or features without deleting the code.</p>
     *
     * <h3>Usage</h3>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("Feature not yet implemented")
     * public static Action futureFeature() {
     *     return Direct.of("futureFeature", ctx -> {
     *         // TODO: implement later
     *     });
     * }
     * }</pre>
     *
     * <h3>Message Purpose</h3>
     * <p>The optional value parameter provides context for why the action is disabled:
     * <ul>
     *   <li>Explains the reason for disabling</li>
     *   <li>Documents TODO items</li>
     *   <li>Indicates temporary conditions</li>
     *   <li>Helps with code review and maintenance</li>
     * </ul>
     *
     * <h3>Common Use Cases</h3>
     * <p><strong>Feature Under Development:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("New feature in development, ETA: Q2 2026")
     * public static Action newFeature() { ... }
     * }</pre>
     *
     * <p><strong>Known Issue:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("Bug #123: Race condition in parallel execution")
     * public static Action flakyTest() { ... }
     * }</pre>
     *
     * <p><strong>Environment-Specific:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("Test requires Linux, running on Windows")
     * public static Action linuxOnlyTest() { ... }
     * }</pre>
     *
     * <p><strong>Performance:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled("Performance test takes 30 minutes, run manually")
     * public static Action performanceTest() { ... }
     * }</pre>
     *
     * <p><strong>No Message:</strong></p>
     * <pre>{@code
     * @ActionFactory
     * @Disabled
     * public static Action disabled() {
     *     return Direct.of("disabled", ctx -> {});
     * }
     * }</pre>
     *
     * <h3>Discovery Behavior</h3>
     * <p>Methods annotated with {@code @Disabled} are:
     * <ul>
     *   <li>Scanned during classpath discovery</li>
     *   <li>Filtered out before factory method invocation</li>
     *   <li>Not included in the execution plan</li>
     *   <li>Not counted in statistics</li>
     * </ul>
     *
     * <h3>Re-enabling</h3>
     * <p>To re-enable a disabled action, simply remove the {@code @Disabled} annotation:
     * <pre>{@code
     * // Disabled
     * @ActionFactory
     * @Disabled("Work in progress")
     * public static Action feature() { ... }
     *
     * // Enabled
     * @ActionFactory
     * public static Action feature() { ... }
     * }</pre>
     *
     * @see ActionFactory
     * @see org.paramixel.core.discovery.Resolver
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Disabled {
        /**
         * Optional message explaining why the action is disabled.
         *
         * <p>This value is not used programmatically but serves as documentation
         * for developers and reviewers. It explains the context, reason, or
         * conditions for the disablement.</p>
         *
         * <p><strong>Guidelines:</strong></p>
         * <ul>
         *       <li>Be concise but informative</li>
         *       <li>Include relevant context (issue numbers, ETAs, conditions)</li>
         *       <li>Use present tense</li>
         *       <li>Avoid blaming language</li>
         *     </ul>
         *
         * @return the reason for disabling; empty string by default
         */
        String value() default "";
    }
}
