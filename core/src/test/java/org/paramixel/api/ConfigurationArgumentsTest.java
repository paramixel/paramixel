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
        Configuration config = Configuration.classpathConfiguration();
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("defaultConfiguration() returns non-null configuration with RUNNER_PARALLELISM key")
    void defaultConfigurationReturnsNonNull() {
        Configuration config = Configuration.defaultConfiguration();
        assertThat(config).isNotNull();
        assertThat(config.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    @DisplayName("Configuration.of(Map) returns non-null configuration")
    void ofMapReturnsNonNull() {
        Configuration config = Configuration.of(Map.of());
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("getString returns empty optional for absent key")
    void getStringReturnsEmptyForAbsentKey() {
        Configuration config = Configuration.of(Map.of());
        assertThat(config.getString("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getString returns present optional for present key")
    void getStringReturnsPresentForPresentKey() {
        Configuration config = Configuration.of(Map.of("key", "value"));
        assertThat(config.getString("key")).contains("value");
    }

    @Test
    @DisplayName("getBoolean returns true for 'true' value")
    void getBooleanReturnsTrueForTrueValue() {
        Configuration config = Configuration.of(Map.of("key", "true"));
        assertThat(config.getBoolean("key")).contains(true);
    }

    @Test
    @DisplayName("getBoolean returns false for 'false' value")
    void getBooleanReturnsFalseForFalseValue() {
        Configuration config = Configuration.of(Map.of("key", "false"));
        assertThat(config.getBoolean("key")).contains(false);
    }

    @Test
    @DisplayName("getBoolean returns empty optional for absent key")
    void getBooleanReturnsEmptyForAbsentKey() {
        Configuration config = Configuration.of(Map.of());
        assertThat(config.getBoolean("key")).isEmpty();
    }

    @Test
    @DisplayName("getInteger returns present optional for valid integer")
    void getIntegerReturnsPresentForValidInteger() {
        Configuration config = Configuration.of(Map.of("key", "42"));
        assertThat(config.getInteger("key")).contains(42);
    }

    @Test
    @DisplayName("keySet returns all keys")
    void keySetReturnsAllKeys() {
        Configuration config = Configuration.of(Map.of("a", "1", "b", "2"));
        assertThat(config.keySet()).containsExactlyInAnyOrder("a", "b");
    }

    @Nested
    @DisplayName("getLong")
    class GetLong {

        @Test
        @DisplayName("returns present optional for valid long")
        void returnsPresentForValidLong() {
            Configuration config = Configuration.of(Map.of("key", "123456789012"));
            assertThat(config.getLong("key")).contains(123456789012L);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid long")
        void returnsPresentForWhitespacePaddedLong() {
            Configuration config = Configuration.of(Map.of("key", "  42  "));
            assertThat(config.getLong("key")).contains(42L);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            Configuration config = Configuration.of(Map.of());
            assertThat(config.getLong("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid long")
        void throwsForInvalidLong() {
            Configuration config = Configuration.of(Map.of("key", "not-a-long"));
            assertThatThrownBy(() -> config.getLong("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected long but was 'not-a-long'");
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            Configuration config = Configuration.of(Map.of());
            assertThatThrownBy(() -> config.getLong(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getFloat")
    class GetFloat {

        @Test
        @DisplayName("returns present optional for valid float")
        void returnsPresentForValidFloat() {
            Configuration config = Configuration.of(Map.of("key", "3.14"));
            assertThat(config.getFloat("key")).contains(3.14f);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid float")
        void returnsPresentForWhitespacePaddedFloat() {
            Configuration config = Configuration.of(Map.of("key", "  2.5  "));
            assertThat(config.getFloat("key")).contains(2.5f);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            Configuration config = Configuration.of(Map.of());
            assertThat(config.getFloat("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid float")
        void throwsForInvalidFloat() {
            Configuration config = Configuration.of(Map.of("key", "not-a-float"));
            assertThatThrownBy(() -> config.getFloat("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected float but was 'not-a-float'");
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            Configuration config = Configuration.of(Map.of());
            assertThatThrownBy(() -> config.getFloat(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getDouble")
    class GetDouble {

        @Test
        @DisplayName("returns present optional for valid double")
        void returnsPresentForValidDouble() {
            Configuration config = Configuration.of(Map.of("key", "2.718281828"));
            assertThat(config.getDouble("key")).contains(2.718281828);
        }

        @Test
        @DisplayName("returns present optional for whitespace-padded valid double")
        void returnsPresentForWhitespacePaddedDouble() {
            Configuration config = Configuration.of(Map.of("key", "  1.5  "));
            assertThat(config.getDouble("key")).contains(1.5);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            Configuration config = Configuration.of(Map.of());
            assertThat(config.getDouble("key")).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException for invalid double")
        void throwsForInvalidDouble() {
            Configuration config = Configuration.of(Map.of("key", "not-a-double"));
            assertThatThrownBy(() -> config.getDouble("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected double but was 'not-a-double'");
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            Configuration config = Configuration.of(Map.of());
            assertThatThrownBy(() -> config.getDouble(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("get(key, transformer)")
    class GetWithTransformer {

        @Test
        @DisplayName("returns transformed value for present key")
        void returnsTransformedValue() {
            Configuration config = Configuration.of(Map.of("key", "42"));
            Function<String, Integer> transformer = Integer::parseInt;
            assertThat(config.get("key", transformer)).contains(42);
        }

        @Test
        @DisplayName("returns empty optional for absent key")
        void returnsEmptyForAbsentKey() {
            Configuration config = Configuration.of(Map.of());
            assertThat(config.get("key", String::length)).isEmpty();
        }

        @Test
        @DisplayName("throws ConfigurationException when transformer throws")
        void throwsConfigurationExceptionWhenTransformerThrows() {
            Configuration config = Configuration.of(Map.of("key", "bad"));
            Function<String, Integer> transformer = Integer::parseInt;
            assertThatThrownBy(() -> config.get("key", transformer))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("Invalid configuration for 'key':");
        }

        @Test
        @DisplayName("ConfigurationException preserves original cause")
        void configurationExceptionPreservesCause() {
            Configuration config = Configuration.of(Map.of("key", "bad"));
            var cause = new RuntimeException("boom");
            Function<String, String> transformer = s -> {
                throw cause;
            };
            assertThatThrownBy(() -> config.get("key", transformer))
                    .isInstanceOf(ConfigurationException.class)
                    .hasCause(cause);
        }

        @Test
        @DisplayName("throws NullPointerException for null key")
        void throwsForNullKey() {
            Configuration config = Configuration.of(Map.of());
            assertThatThrownBy(() -> config.get(null, String::trim)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("throws NullPointerException for null transformer")
        void throwsForNullTransformer() {
            Configuration config = Configuration.of(Map.of());
            assertThatThrownBy(() -> config.get("key", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getInteger error path")
    class GetIntegerErrorPath {

        @Test
        @DisplayName("throws ConfigurationException for invalid integer")
        void throwsForInvalidInteger() {
            Configuration config = Configuration.of(Map.of("key", "not-an-int"));
            assertThatThrownBy(() -> config.getInteger("key"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessage("Invalid configuration for 'key': expected integer but was 'not-an-int'");
        }
    }
}
