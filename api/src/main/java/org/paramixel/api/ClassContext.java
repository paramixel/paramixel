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

/**
 * Provides contextual information for test class execution.
 *
 * <p>This interface represents the intermediate node in the context hierarchy,
 * encapsulating information specific to a single test class. The context is
 * created when the test class is instantiated and remains available throughout
 * the execution of all test methods within that class.</p>
 *
 * <p>The context hierarchy follows a parent-child relationship:</p>
 * <pre>
 * EngineContext (root)
 *   └─ ClassContext (per test class)
 *      └─ ArgumentContext (per test invocation)
 * </pre>
 *
 * <p>Implementations of this interface are responsible for maintaining the
 * lifecycle of the test class and providing access to the instantiated test
 * object and parent engine configuration.</p>
 *
 * <p><b>Lifecycle Method Integration:</b></p>
 * <p>This context is injected into the following lifecycle methods:</p>
 * <ul>
 *   <li>{@link Paramixel.Initialize} - invoked once before any test methods</li>
 *   <li>{@link Paramixel.Finalize} - invoked once after all test methods complete</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Paramixel.TestClass
 * public class MyTests {
 *
 *     private static final Logger LOGGER = Logger.getLogger(MyTests.class.getName());
 *
 *     @Paramixel.Initialize
 *     public void setup(ClassContext context) {
 *         Class<?> testClass = context.getTestClass();
 *         EngineContext engineContext = context.getEngineContext();
 *         LOGGER.info("Initializing test class: " + testClass.getName());
 *     }
 *
 *     @Paramixel.Test
 *     public void testMethod(ArgumentContext context) {
 *         // Access parent class context
 *         ClassContext classContext = context.getClassContext();
 *         Object testInstance = context.getTestInstance();
 *         // Perform test logic
 *     }
 * }
 * }</pre>
 *
 * @see EngineContext
 * @see ArgumentContext
 * @see Paramixel.Initialize
 * @see Paramixel.Finalize
 * @since 0.0.1
 */
public interface ClassContext {

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
     * Returns a class-scoped {@link Store} for sharing state.
     *
     * <p>The returned store is scoped to the current test class and shared across all
     * argument invocations for the class.
     *
     * @return the class-scoped store; never {@code null}
     */
    Store getStore();

    /**
     * Returns the {@link Class} object representing the test class being executed.
     *
     * <p>This method provides access to the runtime class metadata, including
     * class name, methods, annotations, and other reflection-based information.
     * The returned class object is never null.</p>
     *
     * @return the test class {@code Class} object; never {@code null}
     */
    Class<?> getTestClass();

    /**
     * Returns the instantiated test object for this test class.
     *
     * <p>This method returns the test class instance that was created for
     * executing test methods. If this method is invoked before the test class
     * is instantiated (e.g., during early lifecycle phases), it may return
     * {@code null}.</p>
     *
     * <p>The returned object can be used to access instance fields and methods
     * of the test class from within lifecycle methods.</p>
     *
     * @return the test class instance, or {@code null} if the test class
     *         has not yet been instantiated
     */
    Object getTestInstance();
}
