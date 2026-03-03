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

import java.util.Properties;

/**
 * Provides engine-level context and configuration for test execution.
 *
 * <p>This interface represents the root node in the context hierarchy, encapsulating
 * global configuration and state for the Paramixel test engine. It is created during
 * engine initialization and serves as the parent context for all test classes
 * and invocations.</p>
 *
 * <p>The context hierarchy follows a parent-child relationship:</p>
 * <pre>
 * EngineContext (root)
 *   └─ ClassContext (per test class)
 *      └─ ArgumentContext (per test invocation)
 * </pre>
 *
 * <p>Implementations of this interface are responsible for maintaining the
 * engine configuration and providing thread-safe access to configuration properties.</p>
 *
 * <p><b>Lifecycle Method Integration:</b></p>
 * <p>This context is accessible via {@link ClassContext#getEngineContext()} and
 * is typically used in lifecycle methods annotated with:</p>
 * <ul>
 *   <li>{@link Paramixel.Initialize} - for accessing engine configuration during setup</li>
 *   <li>{@link Paramixel.Finalize} - for accessing execution summary during teardown</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Paramixel.TestClass
 * public class ConfigurationAwareTests {
 *
 *     @Paramixel.Initialize
 *     public void setup(ClassContext context) {
 *         EngineContext engineContext = context.getEngineContext();
 *
 *         // Access engine configuration
 *         String customProperty = context.getConfigurationValue("custom.property");
 *         LOGGER.info("Engine ID: " + context.getEngineId());
 *     }
 * }
 * }</pre>
 *
 * @see ClassContext
 * @see ArgumentContext
 * @since 0.0.1
 */
public interface EngineContext {

    /**
     * Returns the unique identifier of this engine instance.
     *
     * <p>The engine identifier is used to register the engine with the
     * JUnit Platform and uniquely identifies this engine among all available
     * test engines in the system.</p>
     *
     * @return the engine identifier; never {@code null} or empty
     */
    String getEngineId();

    /**
     * Returns a copy of the engine configuration properties.
     *
     * <p>This method returns a defensive copy of the configuration properties
     * to maintain the immutability of this context. Modifications to the
     * returned {@link Properties} object will not affect this context.</p>
     *
     * @return a copy of the configuration properties; never {@code null};
     *         may be empty
     */
    Properties getConfiguration();

    /**
     * Returns the configuration value associated with the specified key.
     *
     * <p>This method looks up the specified key in the engine configuration
     * properties and returns the associated value. If the key is not found,
     * this method returns {@code null}.</p>
     *
     * @param key the configuration key to look up; must not be {@code null}
     * @return the configuration value associated with the key, or {@code null}
     *         if the key is not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    String getConfigurationValue(final String key);

    /**
     * Returns the configuration value associated with the specified key,
     * or a default value if the key is not found.
     *
     * <p>This method looks up the specified key in the engine configuration
     * properties. If the key exists, the associated value is returned.
     * Otherwise, the specified {@code defaultValue} is returned.</p>
     *
     * @param key the configuration key to look up; must not be {@code null}
     * @param defaultValue the default value to return if the key is not found;
     *                     may be {@code null}
     * @return the configuration value associated with the key, or
     *         {@code defaultValue} if the key is not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    String getConfigurationValue(final String key, final String defaultValue);

    /**
     * Returns an engine-scoped {@link Store} for sharing state.
     *
     * <p>The returned store is shared across all test classes and argument invocations within
     * the current engine execution.
     *
     * @return the engine-scoped store; never {@code null}
     */
    Store getStore();
}
