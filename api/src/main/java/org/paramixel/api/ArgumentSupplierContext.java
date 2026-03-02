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

import java.util.List;

/**
 * Collector-style context for building argument sets for Paramixel test execution.
 *
 * <p>An {@code ArgumentSupplierContext} is intended for use by argument suppliers that want to
 * programmatically register one or more argument values rather than returning an argument
 * container (for example, a {@link java.util.stream.Stream}, {@link java.util.Collection},
 * {@link Iterable}, array, or single object).
 *
 * <p>The context acts as an append-only sink: suppliers call one of the {@code add*} methods to
 * contribute argument values. The Paramixel engine (or a higher-level integration) is then
 * responsible for consuming the collected arguments and scheduling test invocations.
 *
 * <h2>Argument Semantics</h2>
 * <ul>
 *   <li><b>Argument value</b>: Each call to {@link #addArgument(Object)} registers exactly one
 *       argument value.
 *   <li><b>Null values</b>: This API does not prohibit {@code null}. Whether {@code null} is
 *       treated as a valid argument value or filtered/rejected is implementation-defined.
 *   <li><b>Order</b>: Implementations typically preserve insertion order; ordering matters because it
 *       affects argument indices and can affect display names and execution reporting.
 *   <li><b>Mutation</b>: Prefer immutable argument objects. If you pass mutable instances, treat them
 *       as effectively read-only once added, especially under parallel execution.
 * </ul>
 *
 * <h2>Threading</h2>
 * <p>Unless explicitly documented by a concrete implementation, callers should assume this
 * context is not thread-safe. Populate arguments from a single thread during supplier execution.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Paramixel.TestClass
 * public final class MyTest {
 *
 *     @Paramixel.ArgumentSupplier
 *     public static void arguments(ArgumentSupplierContext context) {
 *         context.addArgument(1);
 *         context.addArguments(2, 3, 4);
 *         context.addArguments(List.of(5, 6));
 *     }
 *
 *     @Paramixel.Test
 *     public void test(ArgumentContext argumentContext) {
 *         // context.getArgument() is 1, then 2, then 3, etc.
 *     }
 * }
 * }</pre>
 *
 * <p><b>Compatibility note:</b> The signature shown in the example ({@code arguments(ArgumentSupplierContext)})
 * depends on whether/where the context-driven supplier pattern is supported by the calling
 * integration. This interface defines the contract for the collector itself.
 */
public interface ArgumentSupplierContext {

    /**
     * Returns the parent {@link EngineContext} associated with this test class.
     *
     * <p>The returned context provides access to engine-level configuration
     * and settings, including parallelism settings and global configuration
     * properties. This context is never null.</p>
     *
     * @return the parent engine context; never {@code null}
     */
    EngineContext getEngineContext();

    /**
     * Adds a single argument value to the supplier output.
     *
     * <p>Each argument value registered through this method should result in at most one
     * invocation for each test method in the enclosing test class (subject to the engine's
     * execution model).
     *
     * @param argument the argument value to register; may be {@code null} if supported by the
     *     implementation
     * @return this argument supplier context
     */
    ArgumentSupplierContext addArgument(Object argument);

    /**
     * Adds multiple argument values to the supplier output.
     *
     * <p>This is a convenience method equivalent to calling {@link #addArgument(Object)} for each
     * element in {@code arguments}, in the given order.
     *
     * @param arguments the argument values to register; may be empty; may contain {@code null}
     *     values if supported by the implementation
     * @return this argument supplier context
     */
    ArgumentSupplierContext addArguments(Object... arguments);

    /**
     * Adds multiple argument values to the supplier output.
     *
     * <p>This is a convenience method equivalent to calling {@link #addArgument(Object)} for each
     * element in {@code arguments}, in the list's iteration order.
     *
     * @param arguments the argument values to register; may be empty; may contain {@code null}
     *     values if supported by the implementation
     * @return this argument supplier context
     * @throws NullPointerException if {@code arguments} is {@code null}
     */
    ArgumentSupplierContext addArguments(List<?> arguments);

    /**
     * Sets the parallelism level for test execution based on the arguments supplied by this
     * context.
     *
     * <p>Calling this method overrides any default parallelism settings from the engine context
     * for tests using these arguments.</p>
     *
     * @param parallelism the desired parallelism level; must be a positive integer ({@code >= 1})
     * @return this argument supplier context
     * @throws IllegalArgumentException if {@code parallelism} is not a positive integer
     */
    ArgumentSupplierContext setParallelism(int parallelism);
}
