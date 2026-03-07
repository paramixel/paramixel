/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class EngineConfigurationUtilTest {

    @Test
    public void parseProvidedPositiveInt_trimsBeforeParsing() {
        assertThat(EngineConfigurationUtil.parseProvidedPositiveInt(
                        "paramixel.parallelism", " 4 ", EngineConfigurationUtil.Source.JUNIT_CONFIG, 1, 2147483647))
                .isEqualTo(4);
    }

    @Test
    public void parseProvidedPositiveInt_unescapesUnicodeAfterTrimming() {
        final String raw = "\\" + "u0034";
        assertThat(EngineConfigurationUtil.parseProvidedPositiveInt(
                        "paramixel.parallelism", raw, EngineConfigurationUtil.Source.JUNIT_CONFIG, 1, 2147483647))
                .isEqualTo(4);
    }

    @Test
    public void parseProvidedPositiveInt_failsIfUnicodeIntroducesLeadingWhitespace() {
        final String raw = "\\" + "u0020" + "4";
        assertThatThrownBy(() -> EngineConfigurationUtil.parseProvidedPositiveInt(
                        "paramixel.parallelism", raw, EngineConfigurationUtil.Source.JUNIT_CONFIG, 1, 2147483647))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(
                        "Invalid configuration: paramixel.parallelism: must not contain leading/trailing whitespace after Unicode unescape (source=junit raw='"
                                + raw
                                + "' normalized=' 4')");
    }
}
