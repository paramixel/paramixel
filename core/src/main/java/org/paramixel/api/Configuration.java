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
import nonapi.org.paramixel.ConfigurationFactory;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.selector.Selector;

/**
 * Provides typed access to Paramixel configuration properties.
 *
 * <p>Configuration is resolved in three layers: classpath properties from
 * {@value #CONFIGURATION_FILE_NAME}, JVM system properties, and built-in framework defaults
 * for any remaining unset keys. Later layers override earlier ones.
 *
 * <p><strong>Security Note:</strong> When using {@link #defaultConfiguration()} or
 * {@link #systemConfiguration()}, all JVM system properties are copied into the returned
 * configuration. That includes values an application may consider sensitive, such as
 * {@code java.home} or a credential the application itself set as a system property. Use
 * {@link #classpathConfiguration()} or read a specific property with
 * {@link System#getProperty(String)} if this is a concern; Paramixel never reads credentials.
 *
 * <h2>Keys and defaults</h2>
 *
 * <table>
 *   <caption>Configuration keys</caption>
 *   <tr><th>Key</th><th>Purpose</th><th>Default when unset</th></tr>
 *   <tr><td>{@code paramixel.parallelism}</td><td>size of the runner's root pool</td>
 *       <td>number of available processors</td></tr>
 *   <tr><td>{@code paramixel.parallelism.<name>}</td><td>size of the user pool {@code <name>}</td>
 *       <td>none: an undefined pool fails validation</td></tr>
 *   <tr><td>{@code paramixel.ansi}</td><td>{@code true}, {@code false} or {@code auto}</td>
 *       <td>{@code auto}</td></tr>
 *   <tr><td>{@code paramixel.failureOnSkip}</td><td>promote {@code SKIPPED} to {@code FAILED}</td>
 *       <td>{@code false}</td></tr>
 *   <tr><td>{@code paramixel.failureOnAbort}</td><td>promote {@code ABORTED} to {@code FAILED}</td>
 *       <td>{@code true}</td></tr>
 *   <tr><td>{@code paramixel.failIfNoTests}</td><td>fail a run that discovers nothing</td>
 *       <td>{@code false}</td></tr>
 *   <tr><td>{@code paramixel.failFast}</td><td>skip the remaining direct children of the root after a
 *       failure</td><td>{@code false}</td></tr>
 *   <tr><td>{@code paramixel.report.file}</td><td>report file; the format follows the extension</td>
 *       <td>none: no report</td></tr>
 *   <tr><td>{@code paramixel.match.package.regex}</td><td>package filter for {@link Selector#configured()}</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.match.class.regex}</td><td>class filter for {@link Selector#configured()}</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.match.tag.regex}</td><td>tag filter for {@link Selector#configured()}</td>
 *       <td>none: unconstrained</td></tr>
 *   <tr><td>{@code paramixel.listener.exclude}</td><td>console sections to suppress</td>
 *       <td>none: print everything</td></tr>
 * </table>
 *
 * <p>An unrecognized key that starts with {@code paramixel.} is reported once through
 * {@link Listener#onWarning(String)} so a typo such as {@code paramixel.parallelism.enviroments} does not fail
 * silently.
 *
 * <p>All typed getters return {@link Optional}; absent keys produce empty optionals rather than
 * exceptions. Invalid values — for example, a non-numeric string for {@link #getInteger(String)} —
 * throw {@link ConfigurationException}.
 *
 * <p>Use the static factory methods to obtain a configuration instance:
 * <ul>
 *   <li>{@link #defaultConfiguration()} — classpath + system properties + defaults</li>
 *   <li>{@link #classpathConfiguration()} — classpath properties only</li>
 *   <li>{@link #systemConfiguration()} — system properties + defaults</li>
 * </ul>
 *
 * @see ConfigurationException
 */
public interface Configuration {

    /**
     * The classpath resource name searched for Paramixel configuration.
     *
     * <p>The default value is {@code "paramixel.properties"}. When this resource is absent,
     * classpath properties are empty.
     */
    String CONFIGURATION_FILE_NAME = "paramixel.properties";

    /**
     * Configuration key controlling runner parallelism.
     *
     * <p>The value must be a positive integer. When unset, the default is the number of available processors.
     */
    String RUNNER_PARALLELISM = "paramixel.parallelism";

    /**
     * Prefix of the configuration key that sizes a named user pool.
     *
     * <p>The key for pool {@code <name>} is {@code paramixel.parallelism.<name>}; use {@link #poolKey(String)}
     * instead of building the string by hand.
     */
    String PARALLELISM_POOL_PREFIX = RUNNER_PARALLELISM + ".";

    /**
     * Configuration key controlling ANSI escape code output in console listeners.
     *
     * <p>Valid values are {@code "true"} (force ANSI enabled), {@code "false"} (force ANSI disabled, plain text),
     * and {@code "auto"} (auto-detect based on terminal capabilities and environment). When unset,
     * the behavior matches {@code "auto"}: ANSI is enabled unless no console is attached, the
     * {@code NO_COLOR} environment variable is set, or the {@code TERM} environment variable is {@code "dumb"}.
     */
    String ANSI = "paramixel.ansi";

    /**
     * Configuration key controlling whether a skipped run should produce a failing exit code.
     *
     * <p>The value is trimmed and compared case-insensitively; only {@code "true"} enables this option (see
     * {@link #parseBoolean(String)}). Any other value, including {@code null}, disables it, so a skipped run exits
     * {@code 0} by default. When {@code true}, a skipped root status is promoted to {@code FAILED} and
     * {@link Result#exitCode()} returns {@code 1}.
     */
    String FAILURE_ON_SKIP = "paramixel.failureOnSkip";

    /**
     * Configuration key controlling whether a run whose root status is aborted should produce a failing exit code.
     *
     * <p>The value is trimmed and compared case-insensitively; only {@code "false"} disables this option (see
     * {@link #parseBoolean(String)}). The default is {@code true}: an aborted root status is promoted to
     * {@code FAILED} and {@link Result#exitCode()} returns {@code 1}. Set it to {@code false} to let an aborted run
     * exit {@code 0}.
     */
    String FAILURE_ON_ABORT = "paramixel.failureOnAbort";

    /**
     * Configuration key controlling whether the absence of discovered action factories should produce a failing exit
     * code.
     *
     * <p>The value is trimmed and compared case-insensitively; only {@code "true"} enables this option (see
     * {@link #parseBoolean(String)}). Any other value, including {@code null}, disables it.
     * When {@code true}, a discovery run that finds no factory (through {@link Runner#run()} or
     * {@link Runner#run(Selector)}) reports a {@code FAILED} status and {@link Result#exitCode()} returns {@code 1}.
     * {@link Runner#run(Action)} always runs the supplied tree, so this key does not apply to it.
     */
    String FAIL_IF_NO_TESTS = "paramixel.failIfNoTests";

    /**
     * Configuration key controlling whether the engine skips the remaining direct children of the root after the
     * first failed or aborted action.
     *
     * <p>The value is trimmed and compared case-insensitively; only {@code "true"} enables this option (see
     * {@link #parseBoolean(String)}). Any other value, including {@code null}, disables it.
     * When {@code true}, a failure or abort stops new direct children of the root descriptor from starting;
     * already-running subtrees complete normally and skipped children are reported {@code SKIPPED}. The scope is
     * deliberately limited to the root's direct children, so a failure deep inside one branch does not cancel
     * unrelated branches.
     */
    String FAIL_FAST = "paramixel.failFast";

    /**
     * Configuration key controlling the file used for the summary report.
     *
     * <p>Recognized file extensions: {@code .json}, {@code .xml}, {@code .html}. Plain text is used for {@code .log},
     * {@code .txt}, unknown extensions, or missing extensions, and the supplied filename is preserved. When unset, no
     * report is written. Consumed by listeners in {@link Listener#defaultListener(Configuration)}.
     */
    String REPORT_FILE = "paramixel.report.file";

    /**
     * Configuration key for the package-name regular expression used to filter discovered action factories.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@code matches()} semantics
     * against candidate package names.
     */
    String MATCH_PACKAGE_REGEX = "paramixel.match.package.regex";

    /**
     * Configuration key for the fully qualified class-name regular expression used to filter discovered action factories.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@code matches()} semantics
     * against candidate fully qualified class names.
     */
    String MATCH_CLASS_REGEX = "paramixel.match.class.regex";

    /**
     * Configuration key for the tag regular expression used to filter discovered action factories.
     *
     * <p>The value is interpreted as a Java regular expression and matched with {@code matches()} semantics
     * against each declared {@link Paramixel.Tag} value.
     */
    String MATCH_TAG_REGEX = "paramixel.match.tag.regex";

    /**
     * Configuration key controlling exclusion of listener output sections.
     *
     * <p>The value is a comma-separated list of tokens. Supported tokens include
     * {@code "status.header"}, {@code "status.footer"}, {@code "summary.header"},
     * {@code "summary.tree"}, {@code "summary.footer"}, {@code "status"}
     * (shorthand for {@code status.header,status.footer}), {@code "quiet"}
     * (shorthand for {@code status,summary.tree}), and {@code "all"} (exclude all sections).
     * Unrecognized tokens are silently ignored.
     */
    String LISTENER_EXCLUDE = "paramixel.listener.exclude";

    /**
     * Returns the configuration value for the supplied key as a string.
     *
     * @param key the configuration key; must not be {@code null}
     * @return the string value, or an empty {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    Optional<String> getString(String key);

    /**
     * Returns the configuration value for the supplied key parsed as a boolean.
     *
     * <p>Only the trimmed, case-insensitive string {@code "true"} returns {@code true}.
     * Every other present value returns {@code false}. Absent keys return an empty optional.
     *
     * @param key the configuration key; must not be {@code null}
     * @return the parsed boolean, or an empty {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} is {@code null}
     * @see #parseBoolean(String)
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Returns the configuration value for the supplied key parsed as an integer.
     *
     * @param key the configuration key; must not be {@code null}
     * @return the parsed integer, or an empty {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws ConfigurationException if the value is present but cannot be parsed as an integer
     */
    Optional<Integer> getInteger(String key);

    /**
     * Returns the configuration value for the supplied key parsed as a long.
     *
     * @param key the configuration key; must not be {@code null}
     * @return the parsed long, or an empty {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws ConfigurationException if the value is present but cannot be parsed as a long
     */
    Optional<Long> getLong(String key);

    /**
     * Returns the configuration value for the supplied key transformed by the supplied function.
     *
     * <p>When the key is absent, returns an empty {@link Optional}. When the key is present, the
     * string value is passed to {@code transformer} and the result is wrapped in an {@link Optional}.
     * If the transformer throws an exception, it is wrapped in a {@link ConfigurationException}.
     *
     * @param <T> the type produced by the transformer
     * @param key the configuration key; must not be {@code null}
     * @param transformer the function that converts the string value to the desired type;
     *     must not be {@code null}
     * @return the transformed value, or an empty {@link Optional} when the key is absent
     * @throws NullPointerException if {@code key} or {@code transformer} is {@code null}
     * @throws ConfigurationException if the transformer throws an exception
     */
    <T> Optional<T> get(String key, Function<String, T> transformer);

    /**
     * Returns all configuration keys present in this configuration.
     *
     * @return the immutable set of configuration keys; never {@code null}
     */
    Set<String> keySet();

    /**
     * Parses a Paramixel boolean configuration value.
     *
     * <p>Only the trimmed, case-insensitive string {@code "true"} returns {@code true}. Every other value, including
     * {@code null}, returns {@code false}.
     *
     * @param value the configuration value to parse, or {@code null}
     * @return {@code true} only when the trimmed value equals {@code "true"} ignoring case
     */
    static boolean parseBoolean(final String value) {
        return value != null && "true".equalsIgnoreCase(value.strip());
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
     * Returns the effective default configuration for a Paramixel run.
     *
     * <p>The returned configuration is built from classpath properties, overlaid with JVM system
     * properties, and supplemented with built-in defaults for any missing keys.
     *
     * @return the default configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration defaultConfiguration() {
        return ConfigurationFactory.defaultConfiguration();
    }

    /**
     * Returns the effective default configuration using the supplied classloader first when loading
     * {@value #CONFIGURATION_FILE_NAME}.
     *
     * <p>The returned configuration is built from classpath properties, overlaid with JVM system
     * properties, and supplemented with built-in defaults for any missing keys.
     *
     * @param classLoader the preferred classloader; must not be {@code null}
     * @return the default configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     * @throws NullPointerException if {@code classLoader} is {@code null}
     */
    static Configuration defaultConfiguration(ClassLoader classLoader) {
        return ConfigurationFactory.defaultConfiguration(classLoader);
    }

    /**
     * Returns a configuration containing only classpath properties from
     * {@value #CONFIGURATION_FILE_NAME} using the default classloader strategy.
     *
     * @return the classpath configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     */
    static Configuration classpathConfiguration() {
        return ConfigurationFactory.classpathConfiguration();
    }

    /**
     * Returns a configuration containing only classpath properties from
     * {@value #CONFIGURATION_FILE_NAME} using the supplied classloader first.
     *
     * @param classLoader the preferred classloader; must not be {@code null}
     * @return the classpath configuration; never {@code null}
     * @throws ConfigurationException if the classpath resource exists but cannot be loaded
     * @throws NullPointerException if {@code classLoader} is {@code null}
     */
    static Configuration classpathConfiguration(ClassLoader classLoader) {
        return ConfigurationFactory.classpathConfiguration(classLoader);
    }

    /**
     * Returns a configuration built from JVM system properties supplemented with built-in defaults.
     *
     * <p><strong>Security Note:</strong> All JVM system properties are copied into the returned
     * configuration, including potentially sensitive ones. Use {@link System#getProperty(String)}
     * directly if you only need specific system properties.
     *
     * @return the system configuration; never {@code null}
     */
    static Configuration systemConfiguration() {
        return ConfigurationFactory.systemConfiguration();
    }

    /**
     * Creates a configuration from the supplied map.
     *
     * <p>The supplied map is defensively copied; subsequent changes to the map are not reflected
     * in the returned configuration. Null keys and null values are rejected.
     *
     * @param properties the configuration properties; must not be {@code null}
     * @return a new configuration backed by the supplied properties; never {@code null}
     * @throws NullPointerException if {@code properties}, any key, or any value is {@code null}
     */
    static Configuration of(Map<String, String> properties) {
        return ConfigurationFactory.fromMap(properties);
    }
}
