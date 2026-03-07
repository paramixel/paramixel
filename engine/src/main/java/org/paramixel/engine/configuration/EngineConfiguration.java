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

package org.paramixel.engine.configuration;

import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.NonNull;

/**
 * Immutable resolved engine configuration for a single execution.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EngineConfiguration {

    /**
     * Whether this engine execution is running in Maven invocation mode.
     */
    private final boolean mavenInvocationMode;

    /**
     * Resolved global parallelism for the engine execution.
     */
    private final int parallelism;

    /**
     * Resolved maximum rendered class-name length for the Maven summary table.
     */
    private final int summaryClassNameMaxLength;

    /**
     * Resolved normalized configuration properties for this execution.
     */
    private final Properties normalizedProperties;

    /**
     * Creates a resolved engine configuration.
     *
     * @param mavenInvocationMode whether Maven invocation mode is enabled
     * @param parallelism the resolved global parallelism
     * @param summaryClassNameMaxLength the resolved summary class-name max length
     * @param normalizedProperties the resolved normalized configuration properties
     */
    public EngineConfiguration(
            final boolean mavenInvocationMode,
            final int parallelism,
            final int summaryClassNameMaxLength,
            final @NonNull Properties normalizedProperties) {
        Objects.requireNonNull(normalizedProperties, "normalizedProperties must not be null");
        this.mavenInvocationMode = mavenInvocationMode;
        this.parallelism = parallelism;
        this.summaryClassNameMaxLength = summaryClassNameMaxLength;
        this.normalizedProperties = new Properties();
        this.normalizedProperties.putAll(normalizedProperties);
    }

    /**
     * Returns whether this execution is running in Maven invocation mode.
     *
     * @return {@code true} if Maven invocation mode is enabled, otherwise {@code false}
     */
    public boolean mavenInvocationMode() {
        return mavenInvocationMode;
    }

    /**
     * Returns the resolved global parallelism.
     *
     * @return the resolved parallelism
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the resolved maximum rendered class-name length for the Maven summary table.
     *
     * @return the maximum rendered class-name length
     */
    public int summaryClassNameMaxLength() {
        return summaryClassNameMaxLength;
    }

    /**
     * Returns a defensive copy of the normalized configuration properties.
     *
     * @return a defensive copy of the normalized properties
     */
    public Properties normalizedProperties() {
        final Properties copy = new Properties();
        copy.putAll(normalizedProperties);
        return copy;
    }
}
