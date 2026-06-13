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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

@DisplayName("ConfigurationFactory classpath edge cases")
class ConfigurationFactoryClasspathTest {

    @Test
    @DisplayName("with custom ClassLoader that has no properties returns empty configuration")
    void withCustomClassLoaderNoPropertiesReturnsEmptyConfiguration() {
        var emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        var configuration = ConfigurationFactory.classpathConfiguration(emptyLoader);

        assertThat(configuration.keySet()).isEmpty();
    }

    @Test
    @DisplayName("with null ClassLoader throws NullPointerException")
    void withNullClassLoaderThrowsNullPointerException() {
        assertThatThrownBy(() -> ConfigurationFactory.classpathConfiguration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("classLoader is null");
    }

    @Test
    @DisplayName("with custom ClassLoader returns configuration with defaults")
    void withCustomClassLoaderReturnsConfigurationWithDefaults() {
        var emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        var configuration = ConfigurationFactory.defaultConfiguration(emptyLoader);

        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    @DisplayName("throws ConfigurationException when InputStream throws IOException on load")
    void throwsConfigurationExceptionWhenInputStreamThrowsIOException() {
        var brokenLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                if (Configuration.CONFIGURATION_FILE_NAME.equals(name)) {
                    return new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("simulated read failure");
                        }
                    };
                }
                return super.getResourceAsStream(name);
            }
        };

        assertThatThrownBy(() -> ConfigurationFactory.classpathConfiguration(brokenLoader))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining(Configuration.CONFIGURATION_FILE_NAME);
    }

    @Test
    @DisplayName("defaultConfiguration(ClassLoader) returns consistent content on repeated calls")
    void defaultConfigurationClassLoaderReturnsConsistentContentOnRepeatedCalls() {
        var emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        var first = ConfigurationFactory.defaultConfiguration(emptyLoader);
        var second = ConfigurationFactory.defaultConfiguration(emptyLoader);

        assertThat(first.getString(Configuration.RUNNER_PARALLELISM))
                .isEqualTo(second.getString(Configuration.RUNNER_PARALLELISM));
    }

    @Test
    @DisplayName("defaultConfiguration(null) throws NullPointerException")
    void defaultConfigurationNullThrowsNullPointerException() {
        assertThatThrownBy(() -> ConfigurationFactory.defaultConfiguration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("classLoader is null");
    }

    @Test
    @DisplayName("different classloaders produce different cached entries")
    void differentClassloadersProduceDifferentCachedEntries() {
        var loader1 = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };
        var loader2 = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        var result1 = ConfigurationFactory.defaultConfiguration(loader1);
        var result2 = ConfigurationFactory.defaultConfiguration(loader2);

        assertThat(result1).isNotSameAs(result2);
    }
}
