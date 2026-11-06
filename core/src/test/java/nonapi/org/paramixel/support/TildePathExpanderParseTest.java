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

package nonapi.org.paramixel.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TildePathExpander output parsing")
class TildePathExpanderParseTest {

    @Test
    @DisplayName("extracts the home field from a getent passwd line")
    void extractsPasswdHome() {
        assertThat(TildePathExpander.parsePasswdLine("alice:x:1000:1000:Alice:/home/alice:/bin/bash"))
                .isEqualTo("/home/alice");
    }

    @Test
    @DisplayName("returns null for a truncated getent passwd line")
    void rejectsTruncatedPasswdLine() {
        assertThat(TildePathExpander.parsePasswdLine("alice:x:1000")).isNull();
    }

    @Test
    @DisplayName("extracts the home directory from a dscl line")
    void extractsDsclHome() {
        assertThat(TildePathExpander.parseDsclLine("NFSHomeDirectory: /Users/alice"))
                .isEqualTo("/Users/alice");
    }

    @Test
    @DisplayName("returns null for a dscl line without the separator")
    void rejectsDsclLineWithoutSeparator() {
        assertThat(TildePathExpander.parseDsclLine("/Users/alice - not found")).isNull();
    }
}
