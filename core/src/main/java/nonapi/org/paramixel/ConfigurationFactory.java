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

package nonapi.org.paramixel;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.WeakHashMap;
import nonapi.org.paramixel.support.ResourceLoader;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Builds Paramixel {@link Configuration} instances by layering classpath properties, system properties,
 * and built-in defaults.
 *
 * <p>Internal sorting uses {@link TreeMap} which is never exposed to callers.
 *
 * <p><strong>Security Note:</strong> This factory copies all JVM system properties into the returned
 * configuration. This includes potentially sensitive properties such as {@code java.home}, {@code user.name},
 * and {@code user.password}. If this is a concern, use {@link #classpathConfiguration()} or access system
 * properties directly via {@link System#getProperty(String)} instead.
 */
public final class ConfigurationFactory {

    private static final int DEFAULT_SCHEDULER_QUEUE_CAPACITY = 1_024;

    private static final Map<ClassLoader, Configuration> CLASSLOADER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

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
     * {@value Configuration#CONFIGURATION_FILE_NAME} using the defining ClassLoader.
     *
     * @return a configuration containing classpath properties; empty when the resource is absent
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration classpathConfiguration() {
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, ResourceLoader.class.getClassLoader());
        return new ConcreteConfiguration(map);
    }

    /**
     * Creates a configuration containing only classpath properties from
     * {@value Configuration#CONFIGURATION_FILE_NAME} using the supplied class loader.
     *
     * @param classLoader the class loader to use; must not be {@code null}
     * @return a configuration containing classpath properties; empty when the resource is absent
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     * @throws NullPointerException if {@code classLoader} is {@code null}
     */
    public static Configuration classpathConfiguration(final ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader is null");
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
     * <p>Built from classpath properties (loaded using the defining ClassLoader),
     * overlaid with JVM system properties, and supplemented with framework defaults.
     *
     * @return the default configuration
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Configuration defaultConfiguration() {
        var cached = CLASSLOADER_CACHE.get(ResourceLoader.class.getClassLoader());
        if (cached != null) {
            return cached;
        }
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, ResourceLoader.class.getClassLoader());
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        var configuration = new ConcreteConfiguration(Map.copyOf(map));
        CLASSLOADER_CACHE.putIfAbsent(ResourceLoader.class.getClassLoader(), configuration);
        return CLASSLOADER_CACHE.get(ResourceLoader.class.getClassLoader());
    }

    /**
     * Creates the default configuration using the supplied class loader first for classpath properties.
     *
     * @param classLoader the class loader to use for classpath lookup; must not be {@code null}
     * @return the default configuration
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     * @throws NullPointerException if {@code classLoader} is {@code null}
     */
    public static Configuration defaultConfiguration(final ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader is null");
        var cached = CLASSLOADER_CACHE.get(classLoader);
        if (cached != null) {
            return cached;
        }
        var map = new TreeMap<String, String>();
        loadClasspathProperties(map, classLoader);
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        var configuration = new ConcreteConfiguration(Map.copyOf(map));
        CLASSLOADER_CACHE.putIfAbsent(classLoader, configuration);
        return CLASSLOADER_CACHE.get(classLoader);
    }

    private static void configureDefaults(final TreeMap<String, String> map) {
        map.putIfAbsent(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(Runtime.getRuntime().availableProcessors()));
        map.putIfAbsent(Configuration.SCHEDULER_QUEUE_CAPACITY, String.valueOf(DEFAULT_SCHEDULER_QUEUE_CAPACITY));
        map.putIfAbsent(Configuration.FAILURE_ON_ABORT, "true");
    }

    /**
     * Copies all JVM system properties into a TreeMap.
     *
     * <p><strong>Warning:</strong> This includes all system properties, some of which may be sensitive.
     * Access system properties directly via {@link System#getProperty(String)} if you only need specific values.
     *
     * @return a map containing all system properties
     */
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
            throw new ConfigurationException(
                    "failed to load default properties resource: " + Configuration.CONFIGURATION_FILE_NAME + " ("
                            + e.getMessage() + ")",
                    e);
        }
        properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
    }
}
