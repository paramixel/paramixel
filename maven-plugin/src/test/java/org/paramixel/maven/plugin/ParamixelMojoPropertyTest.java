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

package org.paramixel.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.core.Configuration;
import org.paramixel.maven.plugin.ParamixelMojo.Property;

@DisplayName("ParamixelMojo.Property tests")
class ParamixelMojoPropertyTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("default constructor tests")
    class DefaultConstructorTests {

        @Test
        @DisplayName("should create Property with null key and value")
        void shouldCreatePropertyWithNullKeyAndValue() {
            var property = new Property();
            assertThat(property.getKey()).isNull();
            assertThat(property.getValue()).isNull();
        }
    }

    @Nested
    @DisplayName("setKey validation tests")
    class SetKeyValidationTests {

        @Test
        @DisplayName("should throw NullPointerException for null key")
        void shouldThrowForNullKey() {
            var property = new Property();
            assertThatThrownBy(() -> property.setKey(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("key must not be null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank key")
        void shouldThrowForBlankKey() {
            var property = new Property();
            assertThatThrownBy(() -> property.setKey("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("key must not be blank");
        }

        @Test
        @DisplayName("should set valid key")
        void shouldSetValidKey() {
            var property = new Property();
            property.setKey("paramixel.engine.execution.parallelism");
            assertThat(property.getKey()).isEqualTo("paramixel.engine.execution.parallelism");
        }
    }

    @Nested
    @DisplayName("setValue validation tests")
    class SetValueValidationTests {

        @Test
        @DisplayName("should throw NullPointerException for null value")
        void shouldThrowForNullValue() {
            var property = new Property();
            assertThatThrownBy(() -> property.setValue(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("value must not be null");
        }

        @Test
        @DisplayName("should set valid value")
        void shouldSetValidValue() {
            var property = new Property();
            property.setValue("4");
            assertThat(property.getValue()).isEqualTo("4");
        }
    }

    @Nested
    @DisplayName("round-trip tests")
    class RoundTripTests {

        @Test
        @DisplayName("should get back what was set for key and value")
        void shouldGetBackWhatWasSet() {
            var property = new Property();
            property.setKey("some.key");
            property.setValue("some.value");
            assertThat(property.getKey()).isEqualTo("some.key");
            assertThat(property.getValue()).isEqualTo("some.value");
        }

        @Test
        @DisplayName("should allow updating key and value")
        void shouldAllowUpdatingKeyAndValue() {
            var property = new Property();
            property.setKey("first.key");
            property.setValue("first.value");
            property.setKey("second.key");
            property.setValue("second.value");
            assertThat(property.getKey()).isEqualTo("second.key");
            assertThat(property.getValue()).isEqualTo("second.value");
        }
    }

    @Nested
    @DisplayName("mojo configuration loading tests")
    class MojoConfigurationLoadingTests {

        @Test
        @DisplayName("system property paramixel.core.executor.parallelism should override defaults")
        void propertiesFileShouldBeUsedForMojoSideConfiguration() throws Exception {
            var originalParallelism = System.getProperty(Configuration.RUNNER_PARALLELISM);
            try {
                System.setProperty(Configuration.RUNNER_PARALLELISM, "6");

                var mojo = new ParamixelMojo();
                var configuration = invokeBuildConfiguration(mojo);

                assertThat(configuration.get(Configuration.RUNNER_PARALLELISM)).isEqualTo("6");
            } finally {
                if (originalParallelism != null) {
                    System.setProperty(Configuration.RUNNER_PARALLELISM, originalParallelism);
                } else {
                    System.clearProperty(Configuration.RUNNER_PARALLELISM);
                }
            }
        }

        @Test
        @DisplayName("test classpath paramixel.properties should be discovered")
        void testClasspathPropertiesShouldBeDiscovered() throws Exception {
            Files.writeString(
                    tempDir.resolve(Configuration.CONFIG_FILE_NAME),
                    Configuration.RUNNER_PARALLELISM + "=7\n",
                    StandardCharsets.UTF_8);

            var mojo = new ParamixelMojo();

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
                var configuration = invokeBuildConfiguration(mojo, classLoader);

                assertThat(configuration.get(Configuration.RUNNER_PARALLELISM)).isEqualTo("7");
            }
        }

        @Test
        @DisplayName("missing Maven property key should throw descriptive exception")
        void missingMavenPropertyKeyShouldThrowDescriptiveException() throws Exception {
            var property = new Property();
            property.setValue("value");
            var mojo = new ParamixelMojo();
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(() -> invokeBuildConfiguration(mojo))
                    .cause()
                    .isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class)
                    .hasMessage("Paramixel property key must not be null");
        }

        @Test
        @DisplayName("missing Maven property value should throw descriptive exception")
        void missingMavenPropertyValueShouldThrowDescriptiveException() throws Exception {
            var property = new Property();
            property.setKey("paramixel.custom");
            var mojo = new ParamixelMojo();
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(() -> invokeBuildConfiguration(mojo))
                    .cause()
                    .isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class)
                    .hasMessage("Paramixel property 'paramixel.custom' value must not be null");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> invokeBuildConfiguration(final ParamixelMojo mojo) throws Exception {
        Method method = ParamixelMojo.class.getDeclaredMethod("buildConfiguration");
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(mojo);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> invokeBuildConfiguration(final ParamixelMojo mojo, final ClassLoader classLoader)
            throws Exception {
        Method method = ParamixelMojo.class.getDeclaredMethod("buildConfiguration", ClassLoader.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(mojo, classLoader);
    }

    private static void setField(final ParamixelMojo mojo, final String name, final Object value) throws Exception {
        Field field = ParamixelMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}
