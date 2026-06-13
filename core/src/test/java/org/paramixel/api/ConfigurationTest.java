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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadClasspathConfiguration() {
        var configuration = Configuration.classpathConfiguration();
        assertThat(configuration).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenPropertiesFileAbsent() {
        var configuration = Configuration.classpathConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(configuration.keySet()).isEmpty();
    }

    @Test
    void shouldIncludeParamixelSystemProperties() {
        var configuration = Configuration.systemConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    void shouldIncludeNonParamixelSystemProperties() {
        var configuration = Configuration.systemConfiguration();
        assertThat(configuration.getString("java.version")).isPresent();
        assertThat(configuration.getString("user.name")).isPresent();
        assertThat(configuration.getString("user.dir")).isPresent();
    }

    @Test
    void shouldApplyParallelismDefaultInSystemConfiguration() {
        var configuration = Configuration.systemConfiguration();
        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
        assertThat(configuration.getInteger(Configuration.RUNNER_PARALLELISM).orElseThrow())
                .isEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    void shouldMergeDefaultsCorrectly() {
        var configuration = Configuration.defaultConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
        assertThat(configuration.getInteger(Configuration.RUNNER_PARALLELISM).orElseThrow())
                .isEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    void shouldLoadClasspathConfigurationWithNullContextClassLoader() {
        var original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            var configuration = Configuration.classpathConfiguration();
            assertThat(configuration).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldLoadClasspathConfigurationFromExplicitClassLoaderWithoutUsingContextClassLoader() throws Exception {
        var explicitClasspath = tempDir.resolve("explicit");
        Files.createDirectories(explicitClasspath);
        Files.writeString(
                explicitClasspath.resolve(Configuration.CONFIGURATION_FILE_NAME), "paramixel.match.tag.regex=explicit");

        var original = Thread.currentThread().getContextClassLoader();
        var emptyContextClassLoader = new ClassLoader(null) {};
        try (URLClassLoader explicitClassLoader =
                new URLClassLoader(new URL[] {explicitClasspath.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(emptyContextClassLoader);

            var configuration = Configuration.classpathConfiguration(explicitClassLoader);

            assertThat(configuration.getString("paramixel.match.tag.regex")).contains("explicit");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(emptyContextClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldLoadDefaultConfigurationWithNullContextClassLoader() {
        var original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            var configuration = Configuration.defaultConfiguration();
            assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM))
                    .isPresent();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldContainFailureOnSkipKeyInDefaultConfigurationWhenPresentInSystemProperties() {
        var configuration = Configuration.defaultConfiguration();
        if (System.getProperty(Configuration.FAILURE_ON_SKIP) != null) {
            assertThat(configuration.getString(Configuration.FAILURE_ON_SKIP)).isPresent();
        }
    }

    @Test
    void shouldContainFailureOnAbortKeyInDefaultConfigurationWhenPresentInSystemProperties() {
        var configuration = Configuration.defaultConfiguration();
        if (System.getProperty(Configuration.FAILURE_ON_ABORT) != null) {
            assertThat(configuration.getString(Configuration.FAILURE_ON_ABORT)).isPresent();
        }
    }

    @Test
    void shouldReturnNewCopyOnEachDefaultConfigurationCall() {
        var first = Configuration.defaultConfiguration();
        var second = Configuration.defaultConfiguration();
        assertThat(first.getString(Configuration.RUNNER_PARALLELISM))
                .isEqualTo(second.getString(Configuration.RUNNER_PARALLELISM));
    }

    @Test
    @DisplayName("CONFIGURATION_FILE_NAME constant value")
    void configFileName() {
        assertThat(Configuration.CONFIGURATION_FILE_NAME).isEqualTo("paramixel.properties");
    }

    @Test
    @DisplayName("RUNNER_PARALLELISM constant value")
    void runnerParallelism() {
        assertThat(Configuration.RUNNER_PARALLELISM).isEqualTo("paramixel.parallelism");
    }

    @Test
    @DisplayName("SCHEDULER_QUEUE_CAPACITY constant value")
    void schedulerQueueCapacity() {
        assertThat(Configuration.SCHEDULER_QUEUE_CAPACITY).isEqualTo("paramixel.scheduler.queue.capacity");
    }

    @Test
    @DisplayName("FAILURE_ON_SKIP constant value")
    void failureOnSkip() {
        assertThat(Configuration.FAILURE_ON_SKIP).isEqualTo("paramixel.failureOnSkip");
    }

    @Test
    @DisplayName("FAILURE_ON_ABORT constant value")
    void failureOnAbort() {
        assertThat(Configuration.FAILURE_ON_ABORT).isEqualTo("paramixel.failureOnAbort");
    }

    @Test
    @DisplayName("ANSI constant value")
    void ansi() {
        assertThat(Configuration.ANSI).isEqualTo("paramixel.ansi");
    }

    @Test
    @DisplayName("REPORT_FILE constant value")
    void reportFile() {
        assertThat(Configuration.REPORT_FILE).isEqualTo("paramixel.report.file");
    }

    @Test
    @DisplayName("FAIL_IF_NO_TESTS constant value")
    void failIfNoTests() {
        assertThat(Configuration.FAIL_IF_NO_TESTS).isEqualTo("paramixel.failIfNoTests");
    }

    @Test
    @DisplayName("Configuration.of(Map) creates configuration from map")
    void ofMapCreatesConfiguration() {
        var configuration = Configuration.of(Map.of("key", "value"));
        assertThat(configuration.getString("key")).contains("value");
    }

    @Test
    @DisplayName("defaultConfiguration(ClassLoader) throws NullPointerException with null classloader")
    void defaultConfigurationWithNullClassloader() {
        assertThatThrownBy(() -> Configuration.defaultConfiguration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("classLoader is null");
    }

    @Test
    @DisplayName("defaultConfiguration(ClassLoader) loads with explicit classloader")
    void defaultConfigurationWithExplicitClassloader() {
        var configuration = Configuration.defaultConfiguration(getClass().getClassLoader());
        assertThat(configuration).isNotNull();
        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    @DisplayName("ConcreteConfiguration.toMap() returns backing properties")
    void concreteConfigurationToMapReturnsProperties() {
        Map<String, String> props = Map.of("key", "value");
        var configuration = new nonapi.org.paramixel.ConcreteConfiguration(props);
        assertThat(configuration.toMap()).containsEntry("key", "value");
    }
}
