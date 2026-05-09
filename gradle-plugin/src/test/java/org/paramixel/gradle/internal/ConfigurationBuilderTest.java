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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.core.Configuration;

@DisplayName("ConfigurationBuilder tests")
class ConfigurationBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ambient paramixel system properties are not copied into Gradle task configuration")
    void ambientParamixelSystemPropertiesAreNotCopiedIntoGradleTaskConfiguration() {
        String originalMatchTag = System.getProperty(Configuration.TAG_MATCH);
        try {
            System.setProperty(Configuration.TAG_MATCH, "ambient-global-tag");

            Project project = ProjectBuilder.builder().build();
            var objects = project.getObjects();

            var configuration = ConfigurationBuilder.buildConfiguration(
                    Thread.currentThread().getContextClassLoader(),
                    objects.property(Integer.class),
                    objects.property(Boolean.class),
                    objects.property(String.class),
                    objects.property(String.class),
                    objects.property(String.class),
                    objects.property(String.class));

            assertThat(configuration).doesNotContainEntry(Configuration.TAG_MATCH, "ambient-global-tag");
        } finally {
            restoreSystemProperty(Configuration.TAG_MATCH, originalMatchTag);
        }
    }

    @Test
    @DisplayName("explicit classloader properties load without changing thread context classloader")
    void explicitClassLoaderPropertiesLoadWithoutChangingThreadContextClassLoader() throws Exception {
        Path explicitClasspath = tempDir.resolve("explicit");
        Files.createDirectories(explicitClasspath);
        Files.writeString(explicitClasspath.resolve(Configuration.CONFIG_FILE_NAME), Configuration.TAG_MATCH + "=explicit");

        Project project = ProjectBuilder.builder().build();
        var objects = project.getObjects();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader sentinel = new ClassLoader(null) {};

        try (URLClassLoader explicitClassLoader =
                new URLClassLoader(new java.net.URL[] {explicitClasspath.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(sentinel);

            var configuration = ConfigurationBuilder.buildConfiguration(
                    explicitClassLoader,
                    objects.property(Integer.class),
                    objects.property(Boolean.class),
                    objects.property(String.class),
                    objects.property(String.class),
                    objects.property(String.class),
                    objects.property(String.class));

            assertThat(configuration).containsEntry(Configuration.TAG_MATCH, "explicit");
            assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(sentinel);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
