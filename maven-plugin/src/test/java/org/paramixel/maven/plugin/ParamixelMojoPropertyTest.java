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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.Configuration;
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
                    .hasMessage("key is null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank key")
        void shouldThrowForBlankKey() {
            var property = new Property();
            assertThatThrownBy(() -> property.setKey("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("key is blank");
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
                    .hasMessage("value is null");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank value")
        void shouldThrowForBlankValue() {
            var property = new Property();
            assertThatThrownBy(() -> property.setValue("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("value is blank");
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
    @DisplayName("equals and hashCode tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("properties with same key and value are equal")
        void propertiesWithSameKeyAndValueAreEqual() {
            var a = new Property();
            a.setKey("key");
            a.setValue("value");
            var b = new Property();
            b.setKey("key");
            b.setValue("value");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("properties with same key but different values are not equal")
        void propertiesWithSameKeyButDifferentValuesAreNotEqual() {
            var a = new Property();
            a.setKey("key");
            a.setValue("value1");
            var b = new Property();
            b.setKey("key");
            b.setValue("value2");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("properties with different keys but same value are not equal")
        void propertiesWithDifferentKeysButSameValueAreNotEqual() {
            var a = new Property();
            a.setKey("key1");
            a.setValue("value");
            var b = new Property();
            b.setKey("key2");
            b.setValue("value");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("equals returns true for same reference")
        void equalsReturnsTrueForSameReference() {
            var property = new Property();
            property.setKey("key");
            property.setValue("value");

            assertThat(property.equals(property)).isTrue();
        }

        @Test
        @DisplayName("equals returns false for non-Property object")
        void equalsReturnsFalseForNonPropertyObject() {
            var property = new Property();
            property.setKey("key");
            property.setValue("value");

            assertThat(property.equals("not-a-property")).isFalse();
            assertThat(property.equals(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("toString tests")
    class ToStringTests {

        @Test
        @DisplayName("toString returns key and value")
        void toStringReturnsKeyAndValue() {
            var property = new Property();
            property.setKey("my.key");
            property.setValue("my.value");

            assertThat(property.toString()).isEqualTo("Property{key='my.key', value='my.value'}");
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
                var configuration =
                        invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());

                assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM))
                        .contains("6");
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
                    tempDir.resolve(Configuration.CONFIGURATION_FILE_NAME),
                    Configuration.RUNNER_PARALLELISM + "=7\n",
                    StandardCharsets.UTF_8);

            var mojo = new ParamixelMojo();

            try (var classLoader = new URLClassLoader(
                    new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
                var configuration = invokeBuildConfiguration(mojo, classLoader);

                assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM))
                        .contains("7");
            }
        }

        @Test
        @DisplayName("report file is included in built configuration")
        void reportFileShouldBeIncludedInConfiguration() throws Exception {
            var originalReportFile = System.getProperty(Configuration.REPORT_FILE);
            var mojo = new ParamixelMojo();
            try {
                System.clearProperty(Configuration.REPORT_FILE);

                setField(mojo, "reportFile", "target/custom-paramixel/paramixel.json");

                var configuration =
                        invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());

                assertThat(configuration.getString(Configuration.REPORT_FILE))
                        .contains("target/custom-paramixel/paramixel.json");
            } finally {
                restoreSystemProperty(Configuration.REPORT_FILE, originalReportFile);
            }
        }

        @Test
        @DisplayName("system report file should override mojo report configuration")
        void systemReportFileShouldOverrideMojoReportConfiguration() throws Exception {
            var originalReportFile = System.getProperty(Configuration.REPORT_FILE);
            var mojo = new ParamixelMojo();
            try {
                System.setProperty(Configuration.REPORT_FILE, "target/system-paramixel/paramixel.xml");

                setField(mojo, "reportFile", "target/custom-paramixel/paramixel.json");

                var configuration =
                        invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());

                assertThat(configuration.getString(Configuration.REPORT_FILE))
                        .contains("target/system-paramixel/paramixel.xml");
            } finally {
                restoreSystemProperty(Configuration.REPORT_FILE, originalReportFile);
            }
        }

        @Test
        @DisplayName("reportFile element overrides POM property paramixel.report.file")
        void reportFileElementOverridesPomProperty() throws Exception {
            var originalReportFile = System.getProperty(Configuration.REPORT_FILE);
            try {
                System.clearProperty(Configuration.REPORT_FILE);

                var mojo = new ParamixelMojo();

                var property = new Property();
                property.setKey(Configuration.REPORT_FILE);
                property.setValue("pom-property-path.txt");
                setField(mojo, "properties", List.of(property));
                setField(mojo, "reportFile", "config-element-path.txt");

                var configuration =
                        invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());

                assertThat(configuration.getString(Configuration.REPORT_FILE)).contains("config-element-path.txt");
            } finally {
                restoreSystemProperty(Configuration.REPORT_FILE, originalReportFile);
            }
        }

        @Test
        @DisplayName("missing Maven property key should throw descriptive exception")
        void missingMavenPropertyKeyShouldThrowDescriptiveException() throws Exception {
            var property = new Property();
            property.setValue("value");
            var mojo = new ParamixelMojo();
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(() -> invokeBuildConfiguration(mojo, getClass().getClassLoader()))
                    .cause()
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Paramixel property key is null");
        }

        @Test
        @DisplayName("missing Maven property value should throw descriptive exception")
        void missingMavenPropertyValueShouldThrowDescriptiveException() throws Exception {
            var property = new Property();
            property.setKey("paramixel.custom");
            var mojo = new ParamixelMojo();
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(() -> invokeBuildConfiguration(mojo, getClass().getClassLoader()))
                    .cause()
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Paramixel property 'paramixel.custom' value is null");
        }

        @Test
        @DisplayName("blank Maven property value should throw descriptive exception")
        void blankMavenPropertyValueShouldThrowDescriptiveException() {
            var property = new Property();
            property.setKey("paramixel.custom");

            assertThatThrownBy(() -> property.setValue("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("value is blank");
        }

        @Test
        @DisplayName("blank Maven property key through buildConfiguration throws descriptive exception")
        void blankMavenPropertyKeyThroughBuildConfigurationThrowsDescriptiveException() throws Exception {
            var property = new Property();
            var keyField = Property.class.getDeclaredField("key");
            keyField.setAccessible(true);
            keyField.set(property, "   ");
            var valueField = Property.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(property, "some-value");
            var mojo = new ParamixelMojo();
            setField(mojo, "properties", List.of(property));

            assertThatThrownBy(() -> invokeBuildConfiguration(mojo, getClass().getClassLoader()))
                    .cause()
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Paramixel property key is blank");
        }

        @Test
        @DisplayName("duplicate property key logs warning and later value overrides")
        void duplicatePropertyKeyLogsWarningAndLaterValueOverrides() throws Exception {
            var originalReportFile = System.getProperty(Configuration.REPORT_FILE);
            try {
                System.clearProperty(Configuration.REPORT_FILE);

                var p1 = new Property();
                p1.setKey("paramixel.custom.key");
                p1.setValue("first");
                var p2 = new Property();
                p2.setKey("paramixel.custom.key");
                p2.setValue("second");

                var mojo = new ParamixelMojo();
                setField(mojo, "properties", List.of(p1, p2));

                var configuration =
                        invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());

                assertThat(configuration.getString("paramixel.custom.key")).contains("second");
            } finally {
                restoreSystemProperty(Configuration.REPORT_FILE, originalReportFile);
            }
        }

        @Test
        @DisplayName("blank reportFile is not set in configuration")
        void blankReportFileIsNotSetInConfiguration() throws Exception {
            var originalReportFile = System.getProperty(Configuration.REPORT_FILE);
            try {
                System.clearProperty(Configuration.REPORT_FILE);

                var mojo = new ParamixelMojo();
                setField(mojo, "reportFile", "   ");

                try (var classLoader =
                        new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader())) {
                    var configuration = invokeBuildConfiguration(mojo, classLoader);

                    assertThat(configuration.getString(Configuration.REPORT_FILE))
                            .isEmpty();
                }
            } finally {
                restoreSystemProperty(Configuration.REPORT_FILE, originalReportFile);
            }
        }

        @Test
        @DisplayName("failureOnAbort is always written to configuration")
        void failureOnAbortIsAlwaysWritten() throws Exception {
            var mojo = new ParamixelMojo();
            setField(mojo, "failureOnAbort", true);
            var configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAILURE_ON_ABORT)).contains("true");

            setField(mojo, "failureOnAbort", false);
            configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAILURE_ON_ABORT)).contains("false");
        }

        @Test
        @DisplayName("failureOnSkip is written to configuration only when true")
        void failureOnSkipIsWrittenOnlyWhenTrue() throws Exception {
            var mojo = new ParamixelMojo();
            setField(mojo, "failureOnSkip", true);
            var configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAILURE_ON_SKIP)).contains("true");

            setField(mojo, "failureOnSkip", false);
            configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAILURE_ON_SKIP)).isEmpty();
        }

        @Test
        @DisplayName("failIfNoTests is written to configuration only when true")
        void failIfNoTestsIsWrittenOnlyWhenTrue() throws Exception {
            var mojo = new ParamixelMojo();
            setField(mojo, "failIfNoTests", true);
            var configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAIL_IF_NO_TESTS)).contains("true");

            setField(mojo, "failIfNoTests", false);
            configuration =
                    invokeBuildConfiguration(mojo, Thread.currentThread().getContextClassLoader());
            assertThat(configuration.getString(Configuration.FAIL_IF_NO_TESTS)).isEmpty();
        }
    }

    private static Configuration invokeBuildConfiguration(final ParamixelMojo mojo, final ClassLoader classLoader)
            throws Exception {
        var method = ParamixelMojo.class.getDeclaredMethod("buildConfiguration", ClassLoader.class);
        method.setAccessible(true);
        return (Configuration) method.invoke(mojo, classLoader);
    }

    private static void setField(final ParamixelMojo mojo, final String name, final Object value) throws Exception {
        var field = ParamixelMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private static void restoreSystemProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
