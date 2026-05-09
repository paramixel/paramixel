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

package org.paramixel.gradle.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.provider.Property;
import org.paramixel.core.Configuration;

/**
 * Builds the effective Paramixel configuration map for a Gradle task execution.
 *
 * <p>Configuration precedence:
 *
 * <ol>
 *   <li>Classpath {@code paramixel.properties} + built-in defaults
 *   <li>Extension/DSL properties &mdash; overlay only when {@link Property#isPresent()}
 *   <li>Explicit Gradle provider properties ({@code -Dparamixel.*} and {@code -Pparamixel.*}) &mdash; mapped to task
 *       properties by the plugin and therefore overlay extension properties
 * </ol>
 */
public final class ConfigurationBuilder {

    private ConfigurationBuilder() {}

    /**
     * Builds the effective Paramixel configuration map by merging classpath defaults and task properties.
     *
     * <p>The method passes the given classloader directly to {@link Configuration#classpathProperties(ClassLoader)} so
     * that {@code paramixel.properties} can be loaded from the test classpath without mutating thread context state. The
     * Gradle plugin maps explicit
     * {@code -Dparamixel.*} and {@code -Pparamixel.*} values to task properties before this method is called; this method
     * intentionally does not scan global JVM system properties.</p>
     *
     * @param classLoader the classloader that provides test-classpath resources
     * @param parallelism the runner parallelism property, or unset to use the framework default
     * @param failureOnSkip whether skipped results should be treated as failures
     * @param matchPackage the package-name regex filter, or unset to include all packages
     * @param matchClass the class-name regex filter, or unset to include all classes
     * @param matchTag the tag regex filter, or unset to include all tags
     * @param reportFile the report file, or unset to disable report output
     * @return the effective configuration map with precedence applied
     */
    public static Map<String, String> buildConfiguration(
            ClassLoader classLoader,
            Property<Integer> parallelism,
            Property<Boolean> failureOnSkip,
            Property<String> matchPackage,
            Property<String> matchClass,
            Property<String> matchTag,
            Property<String> reportFile) {
        Map<String, String> config = new LinkedHashMap<>(Configuration.classpathProperties(classLoader));
        config.putIfAbsent(
                Configuration.RUNNER_PARALLELISM, String.valueOf(Runtime.getRuntime().availableProcessors()));

        if (parallelism.isPresent()) {
            config.put(Configuration.RUNNER_PARALLELISM, String.valueOf(parallelism.get()));
        }
        if (failureOnSkip.isPresent()) {
            config.put(Configuration.FAILURE_ON_SKIP, String.valueOf(failureOnSkip.get()));
        }
        if (matchPackage.isPresent()) {
            config.put(Configuration.PACKAGE_MATCH, matchPackage.get());
        }
        if (matchClass.isPresent()) {
            config.put(Configuration.CLASS_MATCH, matchClass.get());
        }
        if (matchTag.isPresent()) {
            config.put(Configuration.TAG_MATCH, matchTag.get());
        }
        if (reportFile.isPresent() && !reportFile.get().isBlank()) {
            config.put(Configuration.REPORT_FILE, reportFile.get());
        }

        return config;
    }
}
