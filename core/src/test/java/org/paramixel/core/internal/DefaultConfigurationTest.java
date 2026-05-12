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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Configuration;
import org.paramixel.core.exception.ConfigurationException;

@DisplayName("DefaultConfiguration")
class DefaultConfigurationTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("wraps supplied configuration")
        void wrapsSuppliedConfiguration() {
            DefaultConfiguration configuration =
                    new DefaultConfiguration(Map.of(Configuration.RUNNER_PARALLELISM, "4"));

            assertThat(configuration.get(Configuration.RUNNER_PARALLELISM)).isEqualTo("4");
        }

        @Test
        @DisplayName("falls back to default properties when null is supplied")
        void fallsBackToDefaultPropertiesWhenNullIsSupplied() {
            DefaultConfiguration configuration = new DefaultConfiguration(null);

            assertThat(configuration.asMap()).containsKey(Configuration.RUNNER_PARALLELISM);
        }

        @Test
        @DisplayName("asMap returns unmodifiable map")
        void asMapReturnsUnmodifiableMap() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of());

            assertThatThrownBy(() -> configuration.asMap().put("key", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("returns value for existing key")
        void returnsValueForExistingKey() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of("paramixel.test", "value"));

            assertThat(configuration.get("paramixel.test")).isEqualTo("value");
        }

        @Test
        @DisplayName("returns null for missing key")
        void returnsNullForMissingKey() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of());

            assertThat(configuration.get("nonexistent")).isNull();
        }
    }

    @Nested
    @DisplayName("resolveParallelism")
    class ResolveParallelism {

        @Test
        @DisplayName("returns value from configuration")
        void returnsValueFromConfiguration() {
            DefaultConfiguration configuration =
                    new DefaultConfiguration(Map.of(Configuration.RUNNER_PARALLELISM, "4"));

            assertThat(configuration.resolveParallelism()).isEqualTo(4);
        }

        @Test
        @DisplayName("returns available processors when key is absent")
        void returnsAvailableProcessorsWhenKeyIsAbsent() {
            DefaultConfiguration configuration = new DefaultConfiguration(Map.of());

            assertThat(configuration.resolveParallelism())
                    .isEqualTo(Runtime.getRuntime().availableProcessors());
        }

        @Test
        @DisplayName("throws ConfigurationException for non-numeric value")
        void throwsConfigurationExceptionForNonNumericValue() {
            DefaultConfiguration configuration =
                    new DefaultConfiguration(Map.of(Configuration.RUNNER_PARALLELISM, "abc"));

            assertThatThrownBy(configuration::resolveParallelism)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected integer");
        }

        @Test
        @DisplayName("throws ConfigurationException for zero")
        void throwsConfigurationExceptionForZero() {
            DefaultConfiguration configuration =
                    new DefaultConfiguration(Map.of(Configuration.RUNNER_PARALLELISM, "0"));

            assertThatThrownBy(configuration::resolveParallelism)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer");
        }

        @Test
        @DisplayName("throws ConfigurationException for negative value")
        void throwsConfigurationExceptionForNegativeValue() {
            DefaultConfiguration configuration =
                    new DefaultConfiguration(Map.of(Configuration.RUNNER_PARALLELISM, "-1"));

            assertThatThrownBy(configuration::resolveParallelism)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer");
        }
    }

    @Nested
    @DisplayName("static factory methods")
    class StaticFactoryMethods {

        @Test
        @DisplayName("classpathProperties returns unmodifiable map")
        void classpathPropertiesReturnsUnmodifiableMap() {
            Map<String, String> props = DefaultConfiguration.classpathProperties();

            assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("systemProperties returns unmodifiable map")
        void systemPropertiesReturnsUnmodifiableMap() {
            Map<String, String> props = DefaultConfiguration.systemProperties();

            assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("defaultProperties returns unmodifiable map")
        void defaultPropertiesReturnsUnmodifiableMap() {
            Map<String, String> props = DefaultConfiguration.defaultProperties();

            assertThatThrownBy(() -> props.put("key", "value")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("defaultProperties returns cached result on repeated calls")
        void defaultPropertiesReturnsCachedResultOnRepeatedCalls() {
            Map<String, String> first = DefaultConfiguration.defaultProperties();
            Map<String, String> second = DefaultConfiguration.defaultProperties();

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("defaultProperties contains RUNNER_PARALLELISM")
        void defaultPropertiesContainsRunnerParallelism() {
            Map<String, String> props = DefaultConfiguration.defaultProperties();

            assertThat(props).containsKey(Configuration.RUNNER_PARALLELISM);
            assertThat(props.get(Configuration.RUNNER_PARALLELISM))
                    .isEqualTo(String.valueOf(Runtime.getRuntime().availableProcessors()));
        }
    }
}
