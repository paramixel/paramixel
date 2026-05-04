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
import org.paramixel.core.exception.ConfigurationException;

/**
 * Loads and merges Paramixel configuration properties.
 *
 * <p>This utility exposes configuration from classpath resources and JVM system properties, and applies framework
 * defaults when needed.
 *
 * @apiNote {@link #defaultProperties()} loads classpath properties first, overlays JVM system properties, and then
 *     applies built-in defaults for any remaining unset keys.
 */
public class Configuration {

    /**
     * The classpath resource name searched for Paramixel configuration.
     */
    public static final String CONFIG_FILE_NAME = "paramixel.properties";

    /**
     * Configuration key controlling runner parallelism.
     *
     * <p>The value must be a positive integer. When unset, the default is the number of available processors.
     */
    public static final String RUNNER_PARALLELISM = "paramixel.parallelism";

    /**
     * Configuration key controlling whether skipped results should produce a failing exit code.
     *
     * <p>The value is interpreted with {@link Boolean#parseBoolean(String)}. When {@code true}, skipped results are
     * treated as failures by {@link Runner#runAndReturnExitCode(Action)} and related methods.
     */
    public static final String FAILURE_ON_SKIP = "paramixel.failureOnSkip";

    /**
     * Configuration key controlling package-name based discovery filtering.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@link java.util.regex.Pattern#matcher}
     * {@code .find()} semantics against package names discovered by {@link Resolver}.
     */
    public static final String PACKAGE_MATCH = "paramixel.match.package";

    /**
     * Configuration key controlling fully qualified class-name based discovery filtering.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@link java.util.regex.Pattern#matcher}
     * {@code .find()} semantics against package names discovered by {@link Resolver}.
     */
    public static final String CLASS_MATCH = "paramixel.match.class";

    /**
     * Configuration key controlling tag-based discovery filtering.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@link java.util.regex.Pattern#matcher}
     * {@code .find()} semantics against {@link Paramixel.Tag} values discovered by {@link Resolver}.
     */
    public static final String TAG_MATCH = "paramixel.match.tag";

    /**
     * Configuration key controlling whether per-run summary report files are written.
     */
    public static final String REPORT_ENABLED = "paramixel.report.enabled";

    /**
     * Configuration key controlling the directory used for per-run summary report files.
     */
    public static final String REPORT_DIRECTORY = "paramixel.report.directory";

    private Configuration() {
        // Intentionally empty
    }

    /**
     * Loads properties from {@value #CONFIG_FILE_NAME} on the classpath.
     *
     * @return an unmodifiable map containing only classpath-provided properties
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Map<String, String> classpathProperties() {
        TreeMap<String, String> map = new TreeMap<>();
        loadClasspathProperties(map);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns system properties augmented with Paramixel defaults.
     *
     * @return an unmodifiable map containing JVM system properties and any missing framework defaults
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
     * Returns the effective default configuration for a Paramixel run.
     *
     * <p>The returned map is built from classpath properties, then overlaid with JVM system properties, and finally
     * supplemented with built-in defaults for any missing keys.
     *
     * @return an unmodifiable map containing the effective default configuration
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
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
