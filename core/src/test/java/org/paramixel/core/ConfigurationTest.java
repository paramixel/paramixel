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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadClasspathProperties() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThat(props).isNotNull();
    }

    @Test
    void shouldReturnEmptyMapWhenPropertiesFileAbsent() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThat(props).isNotNull();
        assertThat(props).isEmpty();
    }

    @Test
    void shouldIncludeSystemProperties() {
        Map<String, String> props = Configuration.systemProperties();
        assertThat(props).isNotNull();
        assertThat(props).containsKey("java.version");
    }

    @Test
    void shouldApplyParallelismDefaultInSystemProperties() {
        Map<String, String> props = Configuration.systemProperties();
        assertThat(props).containsKey(Configuration.RUNNER_PARALLELISM);
        String parallelism = props.get(Configuration.RUNNER_PARALLELISM);
        assertThat(parallelism).isEqualTo(String.valueOf(Runtime.getRuntime().availableProcessors()));
    }

    @Test
    void shouldMergeDefaultsCorrectly() {
        Map<String, String> props = Configuration.defaultProperties();
        assertThat(props).isNotNull();
        assertThat(props).containsKey(Configuration.RUNNER_PARALLELISM);
        assertThat(props.get(Configuration.RUNNER_PARALLELISM))
                .isEqualTo(String.valueOf(Runtime.getRuntime().availableProcessors()));
    }

    @Test
    void shouldLoadClasspathPropertiesWithNullContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            Map<String, String> props = Configuration.classpathProperties();
            assertThat(props).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldLoadClasspathPropertiesFromExplicitClassLoaderWithoutUsingContextClassLoader() throws Exception {
        Path explicitClasspath = tempDir.resolve("explicit");
        Files.createDirectories(explicitClasspath);
        Files.writeString(
                explicitClasspath.resolve(Configuration.CONFIG_FILE_NAME), Configuration.TAG_MATCH + "=explicit");

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader emptyContextClassLoader = new ClassLoader(null) {};
        try (URLClassLoader explicitClassLoader =
                new URLClassLoader(new java.net.URL[] {explicitClasspath.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(emptyContextClassLoader);

            Map<String, String> props = Configuration.classpathProperties(explicitClassLoader);

            assertThat(props).containsEntry(Configuration.TAG_MATCH, "explicit");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(emptyContextClassLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldLoadDefaultPropertiesWithNullContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            Map<String, String> props = Configuration.defaultProperties();
            assertThat(props).containsKey(Configuration.RUNNER_PARALLELISM);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void shouldReturnUnmodifiableClasspathProperties() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableSystemProperties() {
        Map<String, String> props = Configuration.systemProperties();
        assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableDefaultProperties() {
        Map<String, String> props = Configuration.defaultProperties();
        assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnIndependentDefaultPropertiesOnRepeatedCalls() {
        Map<String, String> first = Configuration.defaultProperties();
        Map<String, String> second = Configuration.defaultProperties();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void shouldRejectRemoveOnDefaultProperties() {
        Map<String, String> props = Configuration.defaultProperties();
        assertThatThrownBy(props::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> props.remove(Configuration.RUNNER_PARALLELISM))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldContainFailureOnSkipKeyInDefaultPropertiesWhenPresentInSystemProperties() {
        Map<String, String> defaults = Configuration.defaultProperties();
        if (System.getProperty(Configuration.FAILURE_ON_SKIP) != null) {
            assertThat(defaults).containsKey(Configuration.FAILURE_ON_SKIP);
        }
    }

    @Test
    void shouldRejectPutOnClasspathProperties() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectClearOnClasspathProperties() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThatThrownBy(props::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectRemoveOnClasspathProperties() {
        Map<String, String> props = Configuration.classpathProperties();
        assertThatThrownBy(() -> props.remove("key")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectClearOnSystemProperties() {
        Map<String, String> props = Configuration.systemProperties();
        assertThatThrownBy(props::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectRemoveOnSystemProperties() {
        Map<String, String> props = Configuration.systemProperties();
        assertThatThrownBy(() -> props.remove("key")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnDeterministicDefaultPropertiesAcrossCalls() {
        Map<String, String> first = Configuration.defaultProperties();
        Map<String, String> second = Configuration.defaultProperties();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotSameAs(second);
        assertThat(first.get(Configuration.RUNNER_PARALLELISM)).isEqualTo(second.get(Configuration.RUNNER_PARALLELISM));
    }
}
