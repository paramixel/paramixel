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
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.ConfigurationParameters;
import org.paramixel.engine.util.EngineConfigurationUtil;

/**
 * Resolves configuration from supported sources, enforcing fail-fast validation.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EngineConfigurationResolver {

    /**
     * Internal configuration key used to gate Maven invocation mode.
     */
    public static final String INTERNAL_INVOKER_KEY = "paramixel.internal.invoker";

    /**
     * Required marker value for enabling Maven invocation mode.
     */
    public static final String INTERNAL_INVOKER_VALUE = "paramixe-maven-plugin";

    /**
     * Configuration key for the global parallelism setting.
     */
    public static final String PARALLELISM_KEY = "paramixel.parallelism";

    /**
     * Configuration key for the Maven summary class-name max length.
     */
    public static final String SUMMARY_CLASS_NAME_MAX_LENGTH_KEY = "paramixel.summary.classNameMaxLength";

    /**
     * Creates a new resolver.
     */
    private EngineConfigurationResolver() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Resolves the effective engine configuration for a single execution.
     *
     * @param configParameters the JUnit Platform configuration parameters
     * @param normalizedProperties the normalized properties loaded from {@code paramixel.properties}
     * @param defaultParallelism the default parallelism used when not configured
     * @return the resolved configuration
     */
    public static EngineConfiguration resolveForExecution(
            final @NonNull ConfigurationParameters configParameters,
            final @NonNull Properties normalizedProperties,
            final int defaultParallelism) {
        Objects.requireNonNull(configParameters, "configParameters must not be null");
        Objects.requireNonNull(normalizedProperties, "normalizedProperties must not be null");

        final boolean markerPresent = configParameters.get(INTERNAL_INVOKER_KEY).isPresent();
        final boolean mavenInvocationMode;
        if (markerPresent) {
            EngineConfigurationUtil.requireAllowedValue(
                    INTERNAL_INVOKER_KEY,
                    configParameters.get(INTERNAL_INVOKER_KEY).orElse(""),
                    EngineConfigurationUtil.Source.JUNIT_CONFIG,
                    Set.of(INTERNAL_INVOKER_VALUE));
            mavenInvocationMode = true;
        } else {
            mavenInvocationMode = false;
        }

        final int parallelism = resolvePositiveInt(
                configParameters,
                normalizedProperties,
                PARALLELISM_KEY,
                defaultParallelism,
                EngineConfigurationUtil.Source.JUNIT_CONFIG,
                EngineConfigurationUtil.Source.PROPERTIES_FILE);

        final int summaryMaxLength = resolvePositiveInt(
                configParameters,
                normalizedProperties,
                SUMMARY_CLASS_NAME_MAX_LENGTH_KEY,
                Integer.MAX_VALUE,
                EngineConfigurationUtil.Source.JUNIT_CONFIG,
                EngineConfigurationUtil.Source.PROPERTIES_FILE);

        final Properties effective = new Properties();
        effective.putAll(normalizedProperties);

        effective.setProperty(PARALLELISM_KEY, String.valueOf(parallelism));
        effective.setProperty(SUMMARY_CLASS_NAME_MAX_LENGTH_KEY, String.valueOf(summaryMaxLength));
        return new EngineConfiguration(mavenInvocationMode, parallelism, summaryMaxLength, effective);
    }

    /**
     * Resolves a positive integer configuration value.
     *
     * @param configParameters the configuration parameters
     * @param properties the normalized properties
     * @param key the key to resolve
     * @param defaultValue the default value to use when key is absent
     * @param configSource the source to report for configuration parameters
     * @param propertiesSource the source to report for properties
     * @return the resolved integer value
     */
    private static int resolvePositiveInt(
            final ConfigurationParameters configParameters,
            final Properties properties,
            final String key,
            final int defaultValue,
            final EngineConfigurationUtil.Source configSource,
            final EngineConfigurationUtil.Source propertiesSource) {
        final var configValue = configParameters.get(key);
        if (configValue.isPresent()) {
            return EngineConfigurationUtil.parseProvidedPositiveInt(
                    key, configValue.get(), configSource, 1, Integer.MAX_VALUE);
        }

        if (properties.containsKey(key)) {
            final String raw = properties.getProperty(key, "");
            return EngineConfigurationUtil.parseProvidedPositiveInt(key, raw, propertiesSource, 1, Integer.MAX_VALUE);
        }

        return defaultValue;
    }
}
