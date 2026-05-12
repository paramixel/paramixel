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

package org.paramixel.core.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.paramixel.core.Configuration;
import org.paramixel.core.exception.ConfigurationException;

/**
 * Resolves Paramixel configuration from explicit values, classpath properties, and system properties.
 */
public final class DefaultConfiguration {

    private final Map<String, String> configuration;

    private static volatile Map<String, String> cachedDefaultProperties;

    /**
     * Creates a configuration wrapper.
     *
     * @param configuration the explicit configuration values, or {@code null} to use default properties
     */
    public DefaultConfiguration(Map<String, String> configuration) {
        this.configuration = configuration != null ? Map.copyOf(configuration) : Map.copyOf(defaultProperties());
    }

    /**
     * Returns a configured value by key.
     *
     * @param key the configuration key to read
     * @return the configured value, or {@code null} when absent
     */
    public String get(String key) {
        return configuration.get(key);
    }

    /**
     * Returns all effective configuration values.
     *
     * @return the immutable configuration map
     */
    public Map<String, String> asMap() {
        return configuration;
    }

    /**
     * Resolves the default scheduler parallelism.
     *
     * @return the positive worker concurrency configured by {@code paramixel.parallelism}
     * @throws ConfigurationException if the configured value is not a positive integer
     */
    public int resolveParallelism() {
        String configuredParallelism = configuration.getOrDefault(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors()));

        final int parallelism;
        try {
            parallelism = Integer.parseInt(configuredParallelism);
        } catch (NumberFormatException e) {
            throw ConfigurationException.of(
                    "Invalid configuration for '" + Configuration.RUNNER_PARALLELISM + "': expected integer but was '"
                            + configuredParallelism + "'",
                    e);
        }

        if (parallelism <= 0) {
            throw ConfigurationException.of("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                    + "': expected positive integer but was '"
                    + configuredParallelism + "'");
        }

        return parallelism;
    }

    /**
     * Loads configuration properties from the default classpath lookup.
     *
     * @return immutable properties loaded from {@code paramixel.properties}
     */
    public static Map<String, String> classpathProperties() {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, null);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Loads configuration properties with the supplied class loader.
     *
     * @param classLoader the class loader used to locate {@code paramixel.properties}
     * @return immutable properties loaded from the classpath
     */
    public static Map<String, String> classpathProperties(ClassLoader classLoader) {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, classLoader);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns system properties with Paramixel defaults applied.
     *
     * @return immutable system properties and default values
     */
    public static Map<String, String> systemProperties() {
        TreeMap<String, String> map = systemPropertiesRaw();
        configureDefaults(map);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the default configuration for the current process.
     *
     * @return immutable classpath and system properties with defaults applied
     */
    public static Map<String, String> defaultProperties() {
        Map<String, String> cached = cachedDefaultProperties;
        if (cached != null) {
            return cached;
        }
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, null);
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        Map<String, String> result = Collections.unmodifiableMap(map);
        cachedDefaultProperties = result;
        return result;
    }

    /**
     * Returns default configuration using the supplied class loader for classpath properties.
     *
     * @param classLoader the class loader used to locate {@code paramixel.properties}
     * @return immutable classpath and system properties with defaults applied
     */
    public static Map<String, String> defaultProperties(ClassLoader classLoader) {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, classLoader);
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        return Collections.unmodifiableMap(map);
    }

    static void configureDefaults(TreeMap<String, String> map) {
        map.putIfAbsent(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors()));
    }

    private static TreeMap<String, String> systemPropertiesRaw() {
        var map = new TreeMap<String, String>();
        System.getProperties().forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
        return map;
    }

    private static void loadClasspathProperties(TreeMap<String, String> map, ClassLoader classLoader) {
        InputStream inputStream = classLoader == null
                ? ResourceLoader.getResourceAsStream(Configuration.CONFIG_FILE_NAME)
                : ResourceLoader.getResourceAsStream(Configuration.CONFIG_FILE_NAME, classLoader);
        if (inputStream == null) {
            return;
        }
        var properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw ConfigurationException.of(
                    "failed to load default properties resource: " + Configuration.CONFIG_FILE_NAME, e);
        }
        properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
    }
}
