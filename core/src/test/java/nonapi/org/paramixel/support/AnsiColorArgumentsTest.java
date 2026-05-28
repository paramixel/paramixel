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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnsiColor arguments")
class AnsiColorArgumentsTest {

    @Test
    @DisplayName("should throw NPE when text is null")
    void shouldThrowNpeWhenTextIsNull() {
        assertThatThrownBy(() -> AnsiColor.BOLD_GREEN_TEXT.format(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw IAE when text is empty")
    void shouldThrowIaeWhenTextIsEmpty() {
        assertThatThrownBy(() -> AnsiColor.BOLD_GREEN_TEXT.format(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    @DisplayName("should throw IAE when text is blank")
    void shouldThrowIaeWhenTextIsBlank() {
        assertThatThrownBy(() -> AnsiColor.BOLD_GREEN_TEXT.format(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }
}
