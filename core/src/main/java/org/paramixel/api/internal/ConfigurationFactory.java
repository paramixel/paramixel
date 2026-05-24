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

package org.paramixel.api.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.internal.support.ResourceLoader;

/**
 * Builds Paramixel {@link Configuration} instances by layering classpath properties, system properties,
 * and built-in defaults.
 *
 * <p>Internal sorting uses {@link TreeMap} which is never exposed to callers.
 */
public final class ConfigurationFactory {

    private static final int DEFAULT_SCHEDULER_QUEUE_CAPACITY = 1024;

    private static final ClassLoader NULL_CLASSLOADER_KEY = new ClassLoader() {};

    private static final ConcurrentHashMap<ClassLoader, Configuration> CLASSLOADER_CACHE = new ConcurrentHashMap<>();

    private ConfigurationFactory() {
        // Intentionally empty
    }

    /**
     * Creates a configuration from the supplied map.
     *
     * @param properties the property map; must not be {@code null}
     * @return a new configuration backed by the supplied map
     */
    public static Configuration fromMap(final Map<String, String> properties) {
        return new ConcreteConfiguration(properties);
    }

    /**
     * Creates a configuration containing only classpath properties from
     * {@value Configuration#CONFIGURATION_FILE_NAME} using the default classloader strategy.
     *
     * @return a configuration containing classpath properties; empty when the resource is absent
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration classpathConfiguration() {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, null);
        return new ConcreteConfiguration(map);
    }

    /**
     * Creates a configuration containing only classpath properties from
     * {@value Configuration#CONFIGURATION_FILE_NAME} using the supplied class loader.
     *
     * @param classLoader the class loader to use, or {@code null} for the default fallback strategy
     * @return a configuration containing classpath properties; empty when the resource is absent
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration classpathConfiguration(final ClassLoader classLoader) {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, classLoader);
        return new ConcreteConfiguration(map);
    }

    /**
     * Creates a configuration from JVM system properties supplemented with built-in defaults.
     *
     * @return a configuration containing system properties and framework defaults
     */
    public static Configuration systemConfiguration() {
        var map = systemPropertiesRaw();
        configureDefaults(map);
        return new ConcreteConfiguration(map);
    }

    /**
     * Creates the default configuration for a Paramixel run.
     *
     * <p>Built from classpath properties, overlaid with JVM system properties,
     * and supplemented with framework defaults.
     *
     * @return the default configuration
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration defaultConfiguration() {
        return defaultConfiguration(null);
    }

    /**
     * Creates the default configuration using the supplied class loader first for classpath properties.
     *
     * @param classLoader the class loader to use for classpath lookup, or {@code null}
     * @return the default configuration
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration defaultConfiguration(final ClassLoader classLoader) {
        var cacheKey = classLoader != null ? classLoader : NULL_CLASSLOADER_KEY;
        var cached = CLASSLOADER_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, classLoader);
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        var configuration = new ConcreteConfiguration(Map.copyOf(map));
        CLASSLOADER_CACHE.putIfAbsent(cacheKey, configuration);
        return CLASSLOADER_CACHE.get(cacheKey);
    }

    private static void configureDefaults(final TreeMap<String, String> map) {
        map.putIfAbsent(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors()));
        map.putIfAbsent(Configuration.SCHEDULER_QUEUE_CAPACITY, String.valueOf(DEFAULT_SCHEDULER_QUEUE_CAPACITY));
        map.putIfAbsent(Configuration.FAILURE_ON_ABORT, "true");
    }

    private static TreeMap<String, String> systemPropertiesRaw() {
        var map = new TreeMap<String, String>();
        System.getProperties().forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
        return map;
    }

    private static void loadClasspathProperties(final TreeMap<String, String> map, final ClassLoader classLoader) {
        final var inputStream = classLoader == null
                ? ResourceLoader.getResourceAsStream(Configuration.CONFIGURATION_FILE_NAME)
                : ResourceLoader.getResourceAsStream(Configuration.CONFIGURATION_FILE_NAME, classLoader);
        if (inputStream == null) {
            return;
        }
        var properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new ConfigurationException("failed to load default properties resource: "
                    + Configuration.CONFIGURATION_FILE_NAME + " (" + e.getMessage() + ")");
        }
        properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
    }
}
