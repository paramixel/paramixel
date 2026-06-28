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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.ConfigurationException;

@DisplayName("Configuration arguments")
class ConfigurationArgumentsTest {

    @Test
    @DisplayName("returns true for 'true'")
    void returnsTrueForTrue() {
        assertThat(Configuration.parseBoolean("true")).isTrue();
    }

    @Test
    @DisplayName("returns true for 'TRUE'")
    void returnsTrueForUpperCase() {
        assertThat(Configuration.parseBoolean("TRUE")).isTrue();
    }

    @Test
    @DisplayName("returns true for mixed case")
    void returnsTrueForMixedCase() {
        assertThat(Configuration.parseBoolean("TrUe")).isTrue();
    }

    @Test
    @DisplayName("returns true for whitespace-padded 'true'")
    void returnsTrueForWhitespacePadded() {
        assertThat(Configuration.parseBoolean(" true ")).isTrue();
        assertThat(Configuration.parseBoolean("  TRUE  ")).isTrue();
    }

    @Test
    @DisplayName("returns false for 'false'")
    void returnsFalseForFalse() {
        assertThat(Configuration.parseBoolean("false")).isFalse();
    }

    @Test
    @DisplayName("returns false for 'FALSE'")
    void returnsFalseForUpperCaseFalse() {
        assertThat(Configuration.parseBoolean("FALSE")).isFalse();
    }

    @Test
    @DisplayName("returns false for whitespace-padded 'false'")
    void returnsFalseForWhitespacePaddedFalse() {
        assertThat(Configuration.parseBoolean(" FALSE ")).isFalse();
    }

    @Test
    @DisplayName("returns false for typo values")
    void returnsFalseForTypos() {
        assertThat(Configuration.parseBoolean("treu")).isFalse();
        assertThat(Configuration.parseBoolean("ture")).isFalse();
    }

    @Test
    @DisplayName("returns false for common aliases")
    void returnsFalseForAliases() {
        assertThat(Configuration.parseBoolean("yes")).isFalse();
        assertThat(Configuration.parseBoolean("on")).isFalse();
        assertThat(Configuration.parseBoolean("1")).isFalse();
    }

    @Test
    @DisplayName("returns false for blank string")
    void returnsFalseForBlank() {
        assertThat(Configuration.parseBoolean("")).isFalse();
    }

    @Test
    @DisplayName("returns false for whitespace-only string")
    void returnsFalseForWhitespaceOnly() {
        assertThat(Configuration.parseBoolean("   ")).isFalse();
    }

    @Test
    @DisplayName("returns false for null")
    void returnsFalseForNull() {
        assertThat(Configuration.parseBoolean(null)).isFalse();
    }

    @Test
    @DisplayName("is an interface")
    void isInterface() {
        assertThat(Configuration.class.isInterface()).isTrue();
    }

    @Test
    @DisplayName("classpathConfiguration() returns non-null configuration")
    void classpathConfigurationReturnsNonNull() {
        var configuration = Configuration.classpathConfiguration();
        assertThat(configuration).isNotNull();
    }

    @Test
    @DisplayName("defaultConfiguration() returns non-null configuration with RUNNER_PARALLELISM key")
    void defaultConfigurationReturnsNonNull() {
        var configuration = Configuration.defaultConfiguration();
        assertThat(configuration).isNotNull();
        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    @DisplayName("Configuration.of(Map) returns non-null configuration")
    void ofMapReturnsNonNull() {
        var configuration = Configuration.of(Map.of());
        assertThat(configuration).isNotNull();
    }

    @Test
    @DisplayName("Configuration.of(Map) rejects null key")
    void ofMapRejectsNullKey() {
        var properties = new HashMap<String, String>();
        properties.put(null, "value");

        assertThatThrownBy(() -> Configuration.of(properties)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Configuration.of(Map) rejects null value")
    void ofMapRejectsNullValue() {
        var properties = new HashMap<String, String>();
        properties.put("key", null);

        assertThatThrownBy(() -> Configuration.of(properties)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getString returns empty optional for absent key")
    void getStringReturnsEmptyForAbsentKey() {
        var configuration = Configuration.of(Map.of());
        assertThat(configuration.getString("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getString returns present optional for present key")
    void getStringReturnsPresentForPresentKey() {
        var configuration = Configuration.of(Map.of("key", "value"));
        assertThat(configuration.getString("key")).contains("value");
    }

    @Test
    @DisplayName("getBoolean returns true for 'true' value")
    void getBooleanReturnsTrueForTrueValue() {
        var configuration = Configuration.of(Map.of("key", "true"));
        assertThat(configuration.getBoolean("key")).contains(true);
    }

    @Test
    @DisplayName("getBoolean returns false for 'false' value")
    void getBooleanReturnsFalseForFalseValue() {
        var configuration = Configuration.of(Map.of("key", "false"));
        assertThat(configuration.getBoolean("key")).contains(false);
    }

    @Test
    @DisplayName("getBoolean returns empty optional for absent key")
    void getBooleanReturnsEmptyForAbsentKey() {
        var configuration = Configuration.of(Map.of());
        assertThat(configuration.getBoolean("key")).isEmpty();
    }

    @Test
    @DisplayName("getInteger returns present optional for valid integer")
    void getIntegerReturnsPresentForValidInteger() {
        var configuration = Configuration.of(Map.of("key", "42"));
        assertThat(configuration.getInteger("key")).contains(42);
    }

    @Test
    @DisplayName("keySet returns all keys")
    void keySetReturnsAllKeys() {
        var configuration = Configuration.of(Map.of("a", "1", "b", "2"));
        assertThat(configuration.keySet()).containsExactlyInAnyOrder("a", "b");
    }

    @Nested
    @DisplayName("getLong")
    class GetLong {

        @Test
        @DisplayName("returns present optional for valid long")
        void returnsPresentForValidLong() {
            var configuration = Configuration.of(Map.of("key", "123456789012"));
            assertThat(configuration.getLong("key")).contains(123_456_789_012L);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid long")
        void returnsPresentForWhitespacePaddedLong() {
            var configuration = Configuration.of(Map.of("key", "  42  "));
            assertThat(configuration.getLong("key")).contains(42L);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            var configuration = Configuration.of(Map.of());
            assertThat(configuration.getLong("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid long")
        void throwsForInvalidLong() {
            var configuration = Configuration.of(Map.of("key", "not-a-long"));
            assertThatThrownBy(() -> configuration.getLong("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected long but was 'not-a-long'")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            var configuration = Configuration.of(Map.of());
            assertThatThrownBy(() -> configuration.getLong(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getFloat")
    class GetFloat {

        @Test
        @DisplayName("returns present optional for valid float")
        void returnsPresentForValidFloat() {
            var configuration = Configuration.of(Map.of("key", "3.14"));
            assertThat(configuration.getFloat("key")).contains(3.14F);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid float")
        void returnsPresentForWhitespacePaddedFloat() {
            var configuration = Configuration.of(Map.of("key", "  2.5  "));
            assertThat(configuration.getFloat("key")).contains(2.5F);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            var configuration = Configuration.of(Map.of());
            assertThat(configuration.getFloat("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid float")
        void throwsForInvalidFloat() {
            var configuration = Configuration.of(Map.of("key", "not-a-float"));
            assertThatThrownBy(() -> configuration.getFloat("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected float but was 'not-a-float'")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            var configuration = Configuration.of(Map.of());
            assertThatThrownBy(() -> configuration.getFloat(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getDouble")
    class GetDouble {

        @Test
        @DisplayName("returns present optional for valid double")
        void returnsPresentForValidDouble() {
            var configuration = Configuration.of(Map.of("key", "2.718281828"));
            assertThat(configuration.getDouble("key")).contains(2.718_281_828);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid double")
        void returnsPresentForWhitespacePaddedDouble() {
            var configuration = Configuration.of(Map.of("key", "  1.5  "));
            assertThat(configuration.getDouble("key")).contains(1.5);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            var configuration = Configuration.of(Map.of());
            assertThat(configuration.getDouble("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid double")
        void throwsForInvalidDouble() {
            var configuration = Configuration.of(Map.of("key", "not-a-double"));
            assertThatThrownBy(() -> configuration.getDouble("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected double but was 'not-a-double'")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            var configuration = Configuration.of(Map.of());
            assertThatThrownBy(() -> configuration.getDouble(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("get(key, transformer)")
    class GetWithTransformer {

        @Test
        @DisplayName("returns transformed value for present key")
        void returnsTransformedValue() {
            var configuration = Configuration.of(Map.of("key", "42"));
            Function<String, Integer> transformer = Integer::parseInt;
            assertThat(configuration.get("key", transformer)).contains(42);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            var configuration = Configuration.of(Map.of());
            assertThat(configuration.get("key", String::length)).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException when transformer throws")
        void throwsConfigurationExceptionWhenTransformerThrows() {
            var configuration = Configuration.of(Map.of("key", "bad"));
            Function<String, Integer> transformer = Integer::parseInt;
            assertThatThrownBy(() -> configuration.get("key", transformer))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("Invalid configuration for 'key':");
        }

        @Test
        @DisplayName("ConfigurationException preserves original cause")
        void configurationExceptionPreservesCause() {
            var configuration = Configuration.of(Map.of("key", "bad"));
            var cause = new RuntimeException("boom");
            Function<String, Integer> transformer = s -> {
                throw cause;
            };
            assertThatThrownBy(() -> configuration.get("key", transformer))
                    .isInstanceOf(ConfigurationException.class)
                    .hasCause(cause);
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            var configuration = Configuration.of(Map.of());
            assertThatThrownBy(() -> configuration.get(null, String::trim)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("throws NullPointerException for null transformer")
        void throwsForNullTransformer() {
            var configuration = Configuration.of(Map.of());
            assertThatThrownBy(() -> configuration.get("key", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getInteger error path")
    class GetIntegerErrorPath {

        @Test
        @DisplayName("throws ConfigurationException for invalid integer")
        void throwsForInvalidInteger() {
            var configuration = Configuration.of(Map.of("key", "not-an-int"));
            assertThatThrownBy(() -> configuration.getInteger("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected integer but was 'not-an-int'")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }
    }
}
