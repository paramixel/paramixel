/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Provides contextual information for individual test method invocations.
 *
 * <p>This interface represents the leaf node in the context hierarchy, encapsulating
 * all information pertinent to a single test method execution. Each invocation of a
 * test method receives its own {@code ArgumentContext} instance, which may optionally
 * contain a data argument provided by an {@link Paramixel.ArgumentsCollector}.</p>
 *
 * <p>The context hierarchy follows a parent-child relationship:</p>
 * <pre>
 * EngineContext (root)
 *   └─ ClassContext (per test class)
 *      └─ ArgumentContext (per argument invocation)
 * </pre>
 *
 * <p>Lifecycle methods annotated with framework annotations receive this context as a
 * parameter, enabling access to both the test data and the broader execution context.</p>
 *
 * <p><b>Lifecycle Method Integration:</b></p>
 * <p>This context is injected into the following lifecycle and test methods:</p>
 * <ul>
 *   <li>{@link Paramixel.BeforeAll} - invoked once per argument before any test methods</li>
 *   <li>{@link Paramixel.BeforeEach} - invoked before each test method</li>
 *   <li>{@link Paramixel.Test} - the test method itself</li>
 *   <li>{@link Paramixel.AfterEach} - invoked after each test method</li>
 *   <li>{@link Paramixel.AfterAll} - invoked once per argument after all test methods</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 *
 * @Paramixel.TestClass
 * public class ParameterizedTests {
 *
 *     @Paramixel.ArgumentsCollector
 *     public static void arguments(final ArgumentsCollector collector) {
 *         collector.addArguments("input1", "input2", "input3");
 *     }
 *
 *     @Paramixel.Test
 *     public void testWithArgument(ArgumentContext context) {
 *         String argument = context.getArgument(String.class);
 *         int index = context.getArgumentIndex();
 *         // Perform assertions using the argument
 *     }
 * }
 * }</pre>
 *
 * @see Paramixel.ArgumentsCollector
 * @see ClassContext
 * @see EngineContext
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public interface ArgumentContext {

    /**
     * Returns the parent {@link ClassContext} associated with this argument.
     *
     * <p>The returned context provides access to class-level information, including
     * the test class metadata and execution state. This context is never null.</p>
     *
     * @return the parent class context; never {@code null}
     * @since 0.0.1
     */
    ClassContext getClassContext();

    /**
     * Returns an argument-scoped {@link Store} for sharing state.
     *
     * <p>The returned store is scoped to the current argument invocation.
     *
     * @return the argument-scoped store; never {@code null}
     * @since 0.0.1
     */
    Store getStore();

    /**
     * Returns the argument provided for this test invocation.
     *
     * <p>If an {@link Paramixel.ArgumentsCollector} is defined for the test class, this method
     * returns the specific argument value supplied for this invocation. For lifecycle methods
     * such as {@link Paramixel.BeforeAll} and {@link Paramixel.AfterAll}, or when no argument
     * supplier is defined, this method returns {@code null}.</p>
     *
     * <p>The returned object must be cast to the appropriate type before use. Consider
     * using {@link #getArgument(Class)} for type-safe access.</p>
     *
     * @return the argument object for this invocation, or {@code null} if no argument
     *         was provided
     * @since 0.0.1
     */
    Object getArgument();

    /**
     * Returns the argument cast to the specified type.
     *
     * <p>This convenience method performs a type-safe cast of the argument to the
     * specified class type. If the argument is {@code null}, this method returns
     * {@code null} without performing any cast operation.</p>
     *
     * <p>If the argument cannot be cast to the specified type (i.e., the argument
     * is not an instance of the specified class or interface), a
     * {@link ClassCastException} is thrown.</p>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     *
     * @Paramixel.Test
     * public void testWithTypedArgument(ArgumentContext context) {
     *     // Type-safe access without manual casting
     *     MyArgumentType arg = context.getArgument(MyArgumentType.class);
     *     if (arg != null) {
     *         // Use the typed argument
     *     }
     * }
     * }</pre>
     *
     * @param <T> the expected type of the argument
     * @param type the {@link Class} object representing the target type;
     *             must not be {@code null}
     * @return the argument cast to type {@code T}, or {@code null} if no argument
     *         was provided
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws ClassCastException if the argument is not assignable to the specified type
     * @since 0.0.1
     */
    default <T> T getArgument(final @NonNull Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        Object argument = getArgument();
        if (argument == null) {
            return null;
        }
        return type.cast(argument);
    }

    /**
     * Returns the zero-based index of this invocation within the test class.
     *
     * <p>This index uniquely identifies the argument position when multiple arguments
     * are supplied by an {@link Paramixel.ArgumentsCollector}. The index begins at 0 for
     * the first argument and increments sequentially for each subsequent argument.</p>
     *
     * <p>For lifecycle methods annotated with {@link Paramixel.BeforeAll} and
     * {@link Paramixel.AfterAll}, which execute once per argument set, this method
     * returns the index of the current argument being processed.</p>
     *
     * @return the zero-based index of this invocation; always non-negative
     * @since 0.0.1
     */
    int getArgumentIndex();
}
