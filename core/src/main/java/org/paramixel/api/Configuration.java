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

package org.paramixel.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Typed access to Paramixel configuration properties.
 *
 * <p>Configuration is resolved in layers: classpath properties from {@value #CONFIGURATION_FILE_NAME}, then JVM system
 * properties, then built-in defaults. Later layers override earlier ones. A runner may add one more layer above all of
 * them: {@code Runner.Builder.pool(Pool, int)} overrides any configured capacity for that pool.
 *
 * <p><strong>Security note:</strong> {@link #defaultConfiguration()} and {@link #systemConfiguration()} copy every JVM
 * system property, including values an application may consider sensitive. Paramixel never reads credentials, redacts
 * secret-looking keys from diagnostics and reports, and does not write the full property set to a report by default.
 *
 * <h2>Keys and defaults</h2>
 *
 * <table>
 *   <caption>Configuration keys</caption>
 *   <tr><th>Key</th><th>Purpose</th><th>Default when unset</th></tr>
 *   <tr><td>{@code paramixel.parallelism}</td><td>admission budget for root occurrences of a discovery run</td>
 *       <td>number of available processors</td></tr>
 *   <tr><td>{@code paramixel.parallelism.<name>}</td><td>capacity of the named pool {@code <name>}</td>
 *       <td>none: an undefined pool fails validation</td></tr>
 *   <tr><td>{@code paramixel.workers}</td><td>maximum number of execution worker threads</td>
 *       <td>number of available processors</td></tr>
 *   <tr><td>{@code paramixel.shutdown.graceMillis}</td><td>how long a timed-out or canceled callback may take to
 *       quiesce before the run is reported non-quiescent</td><td>{@code 10000}</td></tr>
 *   <tr><td>{@code paramixel.listener.queue.capacity}</td><td>bounded listener event queue capacity</td>
 *       <td>{@code 8192}</td></tr>
 *   <tr><td>{@code paramixel.ansi}</td><td>{@code true}, {@code false} or {@code auto}</td><td>{@code auto}</td></tr>
 *   <tr><td>{@code paramixel.failureOnSkip}</td><td>map a skipped run to a failing exit code</td>
 *       <td>{@code true}</td></tr>
 *   <tr><td>{@code paramixel.failureOnAbort}</td><td>map an aborted run to a failing exit code</td>
 *       <td>{@code true}</td></tr>
 *   <tr><td>{@code paramixel.failIfNoTests}</td><td>fail a run that discovered nothing</td>
 *       <td>{@code true}</td></tr>
 *   <tr><td>{@code paramixel.failFast}</td><td>stop admitting new root occurrences after a root failure</td>
 *       <td>{@code false}</td></tr>
 *   <tr><td>{@code paramixel.report.file}</td><td>report file; the format follows the extension</td>
 *       <td>none: no report</td></tr>
 *   <tr><td>{@code paramixel.match.package.regex}</td><td>package filter used by discovery</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.match.class.regex}</td><td>class filter used by discovery</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.match.tag.regex}</td><td>tag filter used by discovery</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.listener.exclude}</td><td>console sections to suppress</td>
 *       <td>none: print everything</td></tr>
 * </table>
 *
 * <p>An unrecognized key that starts with {@code paramixel.} is reported once through
 * {@link Listener#onWarning(String)} so a typo such as {@code paramixel.parallelism.enviroments} does not fail
 * silently. Invalid values fail before any test code runs.
 *
 * <p>All typed getters return {@link Optional}; an invalid value throws {@link ConfigurationException}.
 */
public interface Configuration {

    /** The classpath resource searched for Paramixel configuration. */
    String CONFIGURATION_FILE_NAME = "paramixel.properties";

    /** Configuration key controlling the admission budget for root occurrences. */
    String RUNNER_PARALLELISM = "paramixel.parallelism";

    /** Prefix of the configuration key that sizes a named pool. */
    String PARALLELISM_POOL_PREFIX = RUNNER_PARALLELISM + ".";

    /** Configuration key controlling the maximum number of execution worker threads. */
    String WORKERS = "paramixel.workers";

    /** Configuration key controlling the bounded shutdown grace period, in milliseconds. */
    String SHUTDOWN_GRACE_MILLIS = "paramixel.shutdown.graceMillis";

    /** Configuration key controlling the bounded listener event queue capacity. */
    String LISTENER_QUEUE_CAPACITY = "paramixel.listener.queue.capacity";

    /** Configuration key controlling ANSI escape code output in console listeners. */
    String ANSI = "paramixel.ansi";

    /** Configuration key controlling whether a skipped run produces a failing exit code. */
    String FAILURE_ON_SKIP = "paramixel.failureOnSkip";

    /** Configuration key controlling whether an aborted run produces a failing exit code. */
    String FAILURE_ON_ABORT = "paramixel.failureOnAbort";

    /** Configuration key controlling whether discovering nothing produces a failing exit code. */
    String FAIL_IF_NO_TESTS = "paramixel.failIfNoTests";

    /** Configuration key controlling whether admission of new root occurrences stops after a root failure. */
    String FAIL_FAST = "paramixel.failFast";

    /** Configuration key controlling the report file. */
    String REPORT_FILE = "paramixel.report.file";

    /** Configuration key for the package-name regular expression used by discovery. */
    String MATCH_PACKAGE_REGEX = "paramixel.match.package.regex";

    /** Configuration key for the fully qualified class-name regular expression used by discovery. */
    String MATCH_CLASS_REGEX = "paramixel.match.class.regex";

    /** Configuration key for the tag regular expression used by discovery. */
    String MATCH_TAG_REGEX = "paramixel.match.tag.regex";

    /** Configuration key controlling exclusion of console listener output sections. */
    String LISTENER_EXCLUDE = "paramixel.listener.exclude";

    /**
     * Returns the configuration value for the supplied key.
     *
     * @param key the key; must not be {@code null}
     * @return the value, or an empty optional when absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<String> getString(String key);

    /**
     * Returns the value parsed as a boolean. Only {@code "true"} ignoring case and surrounding whitespace is true.
     *
     * @param key the key; must not be {@code null}
     * @return the value, or an empty optional when absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Returns the value parsed as an integer.
     *
     * @param key the key; must not be {@code null}
     * @return the value, or an empty optional when absent
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws ConfigurationException if the value is not an integer
     */
    Optional<Integer> getInteger(String key);

    /**
     * Returns the value parsed as a long.
     *
     * @param key the key; must not be {@code null}
     * @return the value, or an empty optional when absent
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws ConfigurationException if the value is not a long
     */
    Optional<Long> getLong(String key);

    /**
     * Returns the value transformed by the supplied function.
     *
     * @param <T> the result type
     * @param key the key; must not be {@code null}
     * @param transformer the transformer; must not be {@code null}
     * @return the transformed value, or an empty optional when absent
     * @throws NullPointerException if any argument is {@code null}
     * @throws ConfigurationException if the transformer throws
     */
    <T> Optional<T> get(String key, Function<String, T> transformer);

    /**
     * Returns all configuration keys present in this configuration.
     *
     * @return the immutable keys; never {@code null}
     */
    Set<String> keySet();

    /**
     * Parses a Paramixel boolean value.
     *
     * @param value the value, or {@code null}
     * @return {@code true} only when the trimmed value is {@code "true"} ignoring case
     */
    static boolean parseBoolean(final String value) {
        return value != null && "true".equalsIgnoreCase(value.strip());
    }

    /**
     * Parses a Paramixel boolean value with a default.
     *
     * @param value the value, or {@code null}
     * @param defaultValue the value to use when {@code value} is {@code null}
     * @return the parsed value, or {@code defaultValue}
     */
    static boolean parseBoolean(final String value, final boolean defaultValue) {
        return value == null ? defaultValue : parseBoolean(value);
    }

    /**
     * Returns the configuration key that sizes the named pool.
     *
     * @param pool the pool name; must not be {@code null}
     * @return {@code paramixel.parallelism.<pool>}; never {@code null}
     * @throws NullPointerException if {@code pool} is {@code null}
     */
    static String poolKey(final String pool) {
        return PARALLELISM_POOL_PREFIX + Objects.requireNonNull(pool, "pool is null");
    }

    /**
     * Returns the effective default configuration: classpath, then system properties, then defaults.
     *
     * @return the configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration defaultConfiguration() {
        return nonapi.org.paramixel.ConfigurationFactory.defaultConfiguration();
    }

    /**
     * Returns the effective default configuration using the supplied classloader first.
     *
     * @param classLoader the preferred classloader; must not be {@code null}
     * @return the configuration; never {@code null}
     * @throws NullPointerException if {@code classLoader} is {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration defaultConfiguration(final ClassLoader classLoader) {
        return nonapi.org.paramixel.ConfigurationFactory.defaultConfiguration(classLoader);
    }

    /**
     * Returns a configuration containing only classpath properties.
     *
     * @return the configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration classpathConfiguration() {
        return nonapi.org.paramixel.ConfigurationFactory.classpathConfiguration();
    }

    /**
     * Returns a configuration containing only classpath properties, using the supplied classloader first.
     *
     * @param classLoader the preferred classloader; must not be {@code null}
     * @return the configuration; never {@code null}
     * @throws NullPointerException if {@code classLoader} is {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration classpathConfiguration(final ClassLoader classLoader) {
        return nonapi.org.paramixel.ConfigurationFactory.classpathConfiguration(classLoader);
    }

    /**
     * Returns a configuration built from JVM system properties plus defaults.
     *
     * @return the configuration; never {@code null}
     */
    static Configuration systemConfiguration() {
        return nonapi.org.paramixel.ConfigurationFactory.systemConfiguration();
    }

    /**
     * Creates a configuration from the supplied map.
     *
     * <p>The map is defensively copied; null keys and values are rejected.
     *
     * @param properties the properties; must not be {@code null}
     * @return the configuration; never {@code null}
     * @throws NullPointerException if the map, a key, or a value is {@code null}
     */
    static Configuration of(final Map<String, String> properties) {
        return nonapi.org.paramixel.ConfigurationFactory.fromMap(properties);
    }
}
