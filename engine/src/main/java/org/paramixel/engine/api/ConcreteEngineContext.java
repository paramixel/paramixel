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

package org.paramixel.engine.api;

import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.EngineContext;
import org.paramixel.api.Store;

/**
 * Concrete implementation of {@link EngineContext}.
 *
 * <p>This class provides the implementation details for engine-level context
 * information used during test execution. It maintains the engine configuration
 * and provides thread-safe access to configuration properties.</p>
 *
 * <p>Instances of this class are immutable. All configuration properties are
 * copied at construction time to prevent external modification, ensuring
 * consistent behavior throughout the test execution lifecycle.</p>
 *
 * @see EngineContext
 * @since 0.0.1
 * @author Douglas Hoard <doug.hoard@gmail.com>
 */
public final class ConcreteEngineContext implements EngineContext {

    /**
     * Immutable engine identifier.
     */
    private final String engineId;

    /**
     * Defensive copy of engine configuration properties.
     */
    private final Properties configuration;

    /**
     * Maximum number of test classes to execute concurrently.
     */
    private final int classParallelism;

    /**
     * Engine-scoped store.
     */
    private final Store store;

    /**
     * Creates a new instance.
     *
     * @param engineId the engineId
     * @param configuration the configuration
     * @param classParallelism the classParallelism
     * @since 0.0.1
     */
    public ConcreteEngineContext(
            final @NonNull String engineId, final @NonNull Properties configuration, final int classParallelism) {
        this.engineId = Objects.requireNonNull(engineId, "engineId must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        this.configuration = new Properties();
        this.configuration.putAll(configuration);

        if (classParallelism <= 0) {
            throw new IllegalArgumentException("classParallelism must be positive");
        }

        this.classParallelism = classParallelism;
        this.store = new ConcreteStore();
    }

    @Override
    public String getEngineId() {
        return engineId;
    }

    @Override
    public Properties getConfiguration() {
        return new Properties(configuration);
    }

    @Override
    public String getConfigurationValue(final @NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        return configuration.getProperty(key);
    }

    @Override
    public String getConfigurationValue(final @NonNull String key, final String defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        return configuration.getProperty(key, defaultValue);
    }

    @Override
    public Store getStore() {
        return store;
    }

    /**
     * Returns the maximum number of test classes that can execute concurrently.
     *
     * <p>This value controls the degree of parallelism at the class level.
     * Test classes will be scheduled to execute in parallel up to this limit.
     * A value of {@code 1} indicates sequential execution of test classes.</p>
     *
     * @return the maximum number of concurrent test classes; always positive
     * @since 0.0.1
     */
    public int getClassParallelism() {
        return classParallelism;
    }

    @Override
    public String toString() {
        return "ConcreteEngineContext{" + "engineId='" + engineId + '\'' + ", classParallelism=" + classParallelism
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(engineId, configuration, classParallelism);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ConcreteEngineContext other = (ConcreteEngineContext) obj;
        return classParallelism == other.classParallelism
                && Objects.equals(engineId, other.engineId)
                && Objects.equals(configuration, other.configuration);
    }
}
