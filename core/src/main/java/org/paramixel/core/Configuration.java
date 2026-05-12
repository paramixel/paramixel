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

import java.util.Map;
import org.paramixel.core.internal.DefaultConfiguration;

/**
 * Provides the effective configuration for a Paramixel run by layering classpath defaults, system properties, and
 * caller-supplied overrides.
 *
 * <p>This utility exposes configuration from classpath resources and JVM system properties, and applies framework
 * defaults when needed.
 *
 * <p>Public API.
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
     * {@code .find()} semantics against fully qualified class names discovered by {@link Resolver}.
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
     * Configuration key controlling the file used for the summary report.
     *
     * <p>Expected file extensions: {@code .txt}, {@code .json}, {@code .xml}, {@code .html}. When unset, no report
     * is written. Consumed by listeners in {@link Factory#defaultListener(Map)}.
     */
    public static final String REPORT_FILE = "paramixel.report.file";

    /**
     * Configuration key formerly used to control the output format of per-run summary report files.
     *
     * @deprecated Report format is inferred from {@link #REPORT_FILE}. This compatibility key will
     *     be removed in a future breaking release.
     */
    @Deprecated(since = "3.0.0", forRemoval = true)
    public static final String REPORT_FORMAT = "paramixel.report.format";

    private Configuration() {
        // Intentionally empty
    }

    /**
     * Loads properties from {@value #CONFIG_FILE_NAME} on the classpath.
     *
     * @return an unmodifiable map containing only classpath-provided properties
     * @throws org.paramixel.core.exception.ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Map<String, String> classpathProperties() {
        return DefaultConfiguration.classpathProperties();
    }

    /**
     * Loads properties from {@value #CONFIG_FILE_NAME} using the supplied classloader first.
     *
     * @param classLoader the preferred classloader, or {@code null} to use the default fallback
     *     strategy
     * @return an unmodifiable map containing only classpath-provided properties
     * @throws org.paramixel.core.exception.ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Map<String, String> classpathProperties(ClassLoader classLoader) {
        return DefaultConfiguration.classpathProperties(classLoader);
    }

    /**
     * Returns system properties augmented with Paramixel defaults.
     *
     * <p>System properties override classpath defaults in the configuration layering used by
     * {@link #defaultProperties()}.
     *
     * @return an unmodifiable map containing JVM system properties and any missing framework defaults
     */
    public static Map<String, String> systemProperties() {
        return DefaultConfiguration.systemProperties();
    }

    /**
     * Returns the effective default configuration for a Paramixel run.
     *
     * <p>The returned map is built from classpath properties, then overlaid with JVM system properties, and finally
     * supplemented with built-in defaults for any missing keys.
     *
     * @return an unmodifiable map containing the effective default configuration
     * @throws org.paramixel.core.exception.ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Map<String, String> defaultProperties() {
        return DefaultConfiguration.defaultProperties();
    }

    /**
     * Returns the effective default configuration for a Paramixel run using the supplied classloader
     * first when loading {@value #CONFIG_FILE_NAME}.
     *
     * <p>The returned map is built from classpath properties, then overlaid with JVM system
     * properties, and finally supplemented with built-in defaults for any missing keys.
     *
     * @param classLoader the preferred classloader, or {@code null} to use the default fallback
     *     strategy
     * @return an unmodifiable map containing the effective default configuration
     * @throws org.paramixel.core.exception.ConfigurationException if the classpath resource exists but cannot be loaded
     */
    public static Map<String, String> defaultProperties(ClassLoader classLoader) {
        return DefaultConfiguration.defaultProperties(classLoader);
    }
}
