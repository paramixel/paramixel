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
import java.util.Collections;
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
 *
 * <h3>Class-loader resolution</h3>
 * <p>Classpath resources are loaded using the thread context class loader first.
 * If the context class loader is unavailable ({@code null}) or cannot find the
 * resource, the defining class loader ({@code Configuration.class.getClassLoader()})
 * is tried as a fallback. This ensures reliable resource loading in containers,
 * plugins, and test runners that set a restricted or different context class loader.</p>
 */
public class Configuration {

    /**
     * The name of the classpath properties file loaded by
     * {@link #classpathProperties()} and {@link #defaultProperties()}.
     *
     * <p>The file is expected at the root of the classpath (for example,
     * {@code src/test/resources/paramixel.properties} or
     * {@code src/main/resources/paramixel.properties}). If the file is absent,
     * classpath loading silently returns an empty map.</p>
     *
     * <p>Values defined in this file have the lowest precedence; JVM system
     * properties and programmatic configuration override them.</p>
     *
     * @see #classpathProperties()
     * @see #defaultProperties()
     */
    public static final String CONFIG_FILE_NAME = "paramixel.properties";

    /**
     * Configuration key for the default runner parallelism.
     *
     * <p>Controls the maximum number of threads used by a {@link Runner} when
     * no explicit {@link java.util.concurrent.ExecutorService} is provided.
     * The value must be a positive integer represented as a string.</p>
     *
     * <h4>Precedence</h4>
     * <ol>
     *   <li>JVM system property {@code paramixel.parallelism}</li>
     *   <li>Entry in {@value #CONFIG_FILE_NAME}</li>
     *   <li>Built-in default: {@code Runtime.getRuntime().availableProcessors()}</li>
     * </ol>
     *
     * <p>This key is also read by the Paramixel Maven plugin, where it can be
     * set via POM {@code <properties>} or the {@code -Dparamixel.parallelism}
     * command-line flag.</p>
     *
     * @see Runner.Builder#configuration(Map)
     */
    public static final String RUNNER_PARALLELISM = "paramixel.parallelism";

    /**
     * Configuration key for controlling whether SKIP results are treated as
     * failures for exit-code purposes.
     *
     * <p>When set to {@code "true"} (case-insensitive), actions that complete
     * with a SKIP status produce exit code {@code 1} instead of {@code 0},
     * causing {@link ConsoleRunner#runAndReturnExitCode(Action, Map)} and
     * {@link ConsoleRunner#runAndReturnExitCode(org.paramixel.core.discovery.Selector, Map)}
     * to return {@code 1} for skipped actions. When set to {@code "false"} or
     * absent, SKIP produces exit code {@code 0}, the same as PASS.</p>
     *
     * <h4>Precedence</h4>
     * <ol>
     *   <li>JVM system property {@code paramixel.failureOnSkip}</li>
     *   <li>Entry in {@value #CONFIG_FILE_NAME}</li>
     *   <li>Built-in default: {@code false}</li>
     * </ol>
     *
     * <p>Note: the no-configuration overloads
     * {@link ConsoleRunner#runAndReturnExitCode(Action)} and
     * {@link ConsoleRunner#runAndReturnExitCode(org.paramixel.core.discovery.Selector)}
     * always treat SKIP as success (exit code {@code 0}). Only the
     * {@code Map}-accepting overloads consult this key.</p>
     *
     * <p>In the Maven plugin, this corresponds to the
     * {@code <failureOnSkip>} POM parameter or the
     * {@code -Dparamixel.failureOnSkip=true} command-line flag.</p>
     *
     * @see ConsoleRunner#runAndReturnExitCode(Action, Map)
     * @see ConsoleRunner#runAndReturnExitCode(org.paramixel.core.discovery.Selector, Map)
     */
    public static final String FAILURE_ON_SKIP = "paramixel.failureOnSkip";

    private Configuration() {
        // Intentionally empty
    }

    /**
     * Returns properties loaded from {@code paramixel.properties} at the root
     * of the classpath.
     *
     * <p>If the resource does not exist, an empty map is returned.
     * If an I/O error occurs, a {@link ConfigurationException} is thrown.</p>
     *
     * <p>The classpath resource is resolved using the thread context class loader
     * first. If the context class loader is unavailable or cannot find the resource,
     * the defining class loader is used as a fallback.</p>
     *
     * @return An unmodifiable map of property names to values, or an empty
     *     map when the resource is not found.
     * @throws ConfigurationException If the resource cannot be loaded.
     */
    public static Map<String, String> classpathProperties() {
        TreeMap<String, String> map = new TreeMap<>();
        loadClasspathProperties(map);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns all system properties as a sorted map.
     *
     * @return An unmodifiable map of system property names to values.
     */
    public static Map<String, String> systemProperties() {
        TreeMap<String, String> map = systemPropertiesRaw();
        configureDefaults(map);
        return Collections.unmodifiableMap(map);
    }

    private static TreeMap<String, String> systemPropertiesRaw() {
        TreeMap<String, String> map = new TreeMap<>();
        System.getProperties().forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
        return map;
    }

    private static void loadClasspathProperties(TreeMap<String, String> map) {
        InputStream inputStream = getResourceAsStream(CONFIG_FILE_NAME);
        if (inputStream == null) {
            return;
        }
        Properties properties = new Properties();
        try (inputStream) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw ConfigurationException.of("failed to load default properties resource: " + CONFIG_FILE_NAME, e);
        }
        properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
    }

    static InputStream getResourceAsStream(String name) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(name);
            if (stream != null) {
                return stream;
            }
        }
        ClassLoader definingClassLoader = Configuration.class.getClassLoader();
        if (definingClassLoader != null) {
            return definingClassLoader.getResourceAsStream(name);
        }
        return ClassLoader.getSystemResourceAsStream(name);
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
        return Collections.unmodifiableMap(map);
    }

    private static void configureDefaults(TreeMap<String, String> map) {
        map.putIfAbsent(RUNNER_PARALLELISM, String.valueOf(Runtime.getRuntime().availableProcessors()));
    }
}
