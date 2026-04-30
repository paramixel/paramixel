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

package org.paramixel.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Loads configuration from classpath resources and system properties.
 *
 * <p>Configuration parameters are loaded from multiple sources, with later
 * sources taking precedence over earlier ones:
 *
 * <ol>
 *   <li>Default classpath config file: {@code paramixel.properties} at the
 *       root of the classpath (optional)</li>
 *   <li>JVM system properties</li>
 * </ol>
 */
public class Configuration {

    public static final String CONFIG_FILE_NAME = "paramixel.properties";

    public static final String RUNNER_PARALLELISM = "paramixel.parallelism";

    private Configuration() {
        // Intentionally empty
    }

    /**
     * Returns properties loaded from {@code paramixel.properties} at the root
     * of the classpath.
     *
     * <p>If the resource does not exist, an empty map is returned.
     * If an I/O error occurs, a {@link ConfigurationException} is thrown.
     *
     * @return An unmodifiable map of property names to values, or an empty
     *     map when the resource is not found.
     * @throws ConfigurationException If the resource cannot be loaded.
     */
    public static Map<String, String> classpathProperties() {
        TreeMap<String, String> map = new TreeMap<>();
        loadClasspathProperties(map);
        return map;
    }

    /**
     * Returns all system properties as a sorted map.
     *
     * @return An unmodifiable map of system property names to values.
     */
    public static Map<String, String> systemProperties() {
        TreeMap<String, String> map = systemPropertiesRaw();
        configureDefaults(map);
        return map;
    }

    private static TreeMap<String, String> systemPropertiesRaw() {
        TreeMap<String, String> map = new TreeMap<>();
        System.getProperties().forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
        return map;
    }

    private static void loadClasspathProperties(TreeMap<String, String> map) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(CONFIG_FILE_NAME);
        if (inputStream == null) {
            return;
        }
        Properties properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new ConfigurationException("failed to load default properties resource: " + CONFIG_FILE_NAME, e);
        }
        properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
    }

    /**
     * Returns default properties by loading from multiple sources, with later
     * sources taking precedence over earlier ones:
     *
     * <ol>
     *   <li>Default classpath config file: {@code paramixel.properties} at the
     *       root of the classpath (optional)</li>
     *   <li>JVM system properties</li>
     * </ol>
     *
     * @return An unmodifiable map of merged property names to values.
     * @throws ConfigurationException If the properties file cannot be loaded.
     */
    public static Map<String, String> defaultProperties() {
        TreeMap<String, String> map = new TreeMap<>();
        loadClasspathProperties(map);
        map.putAll(systemPropertiesRaw());
        configureDefaults(map);
        return map;
    }

    private static void configureDefaults(TreeMap<String, String> map) {
        map.putIfAbsent(RUNNER_PARALLELISM, String.valueOf(Runtime.getRuntime().availableProcessors()));
    }
}
