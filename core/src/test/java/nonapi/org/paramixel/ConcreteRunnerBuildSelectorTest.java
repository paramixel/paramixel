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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

@DisplayName("ConcreteRunner buildSelector")
class ConcreteRunnerBuildSelectorTest {

    @Test
    @DisplayName("invalid package regex throws ConfigurationException with descriptive message")
    void invalidPackageRegexThrowsConfigurationExceptionWithDescriptiveMessage() throws Exception {
        var configuration = Configuration.of(Map.of(Configuration.MATCH_PACKAGE_REGEX, "[invalid"));
        var exception = callBuildSelector(configuration);
        assertThat(exception)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Invalid package regex pattern")
                .hasMessageContaining("[invalid");
    }

    @Test
    @DisplayName("invalid class regex throws ConfigurationException with descriptive message")
    void invalidClassRegexThrowsConfigurationExceptionWithDescriptiveMessage() throws Exception {
        var configuration = Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, "[invalid"));
        var exception = callBuildSelector(configuration);
        assertThat(exception)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Invalid class regex pattern")
                .hasMessageContaining("[invalid");
    }

    @Test
    @DisplayName("invalid tag regex throws ConfigurationException with descriptive message")
    void invalidTagRegexThrowsConfigurationExceptionWithDescriptiveMessage() throws Exception {
        var configuration = Configuration.of(Map.of(Configuration.MATCH_TAG_REGEX, "[invalid"));
        var exception = callBuildSelector(configuration);
        assertThat(exception)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Invalid tag regex pattern")
                .hasMessageContaining("[invalid");
    }

    @Test
    @DisplayName("valid regex patterns do not throw")
    void validRegexPatternsDoNotThrow() throws Exception {
        var configuration = Configuration.of(Map.of(
                Configuration.MATCH_PACKAGE_REGEX, "org\\.paramixel\\.api",
                Configuration.MATCH_CLASS_REGEX, ".*Test.*",
                Configuration.MATCH_TAG_REGEX, "smoke"));
        var exception = callBuildSelector(configuration);
        assertThat(exception).isNull();
    }

    private static ConfigurationException callBuildSelector(final Configuration configuration) {
        try {
            var method = ConcreteRunner.class.getDeclaredMethod("buildSelector", Configuration.class);
            method.setAccessible(true);
            method.invoke(null, configuration);
            return null;
        } catch (Exception e) {
            Throwable current = e;
            while (current != null) {
                if (current instanceof ConfigurationException ce) {
                    return ce;
                }
                current = current.getCause();
            }
            throw new AssertionError(
                    "Expected ConfigurationException but got: " + e.getClass().getName() + " - " + e.getMessage(), e);
        }
    }
}
