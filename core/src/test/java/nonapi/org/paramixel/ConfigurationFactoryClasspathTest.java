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
        ClassLoader emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        Configuration config = ConfigurationFactory.classpathConfiguration(emptyLoader);

        assertThat(config.keySet()).isEmpty();
    }

    @Test
    @DisplayName("with null ClassLoader delegates to default strategy")
    void withNullClassLoaderDelegatesToDefaultStrategy() {
        Configuration config = ConfigurationFactory.classpathConfiguration(null);

        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("with custom ClassLoader returns configuration with defaults")
    void withCustomClassLoaderReturnsConfigurationWithDefaults() {
        ClassLoader emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        Configuration config = ConfigurationFactory.defaultConfiguration(emptyLoader);

        assertThat(config.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
    }

    @Test
    @DisplayName("throws ConfigurationException when InputStream throws IOException on load")
    void throwsConfigurationExceptionWhenInputStreamThrowsIOException() {
        ClassLoader brokenLoader = new ClassLoader() {
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
        ClassLoader emptyLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        Configuration first = ConfigurationFactory.defaultConfiguration(emptyLoader);
        Configuration second = ConfigurationFactory.defaultConfiguration(emptyLoader);

        assertThat(first.getString(Configuration.RUNNER_PARALLELISM))
                .isEqualTo(second.getString(Configuration.RUNNER_PARALLELISM));
    }

    @Test
    @DisplayName("defaultConfiguration(null) returns same content as no-arg defaultConfiguration()")
    void defaultConfigurationNullReturnsSameContentAsNoArg() {
        Configuration fromNoArg = ConfigurationFactory.defaultConfiguration();
        Configuration fromNull = ConfigurationFactory.defaultConfiguration(null);

        assertThat(fromNoArg.getString(Configuration.RUNNER_PARALLELISM))
                .isEqualTo(fromNull.getString(Configuration.RUNNER_PARALLELISM));
    }

    @Test
    @DisplayName("different classloaders produce different cached entries")
    void differentClassloadersProduceDifferentCachedEntries() {
        ClassLoader loader1 = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };
        ClassLoader loader2 = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {
                return null;
            }
        };

        Configuration result1 = ConfigurationFactory.defaultConfiguration(loader1);
        Configuration result2 = ConfigurationFactory.defaultConfiguration(loader2);

        assertThat(result1).isNotSameAs(result2);
    }
}
