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

package org.paramixel.core.internal.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnsiColor tests")
class AnsiColorTest {

    @Nested
    @DisplayName("format() argument validation")
    class FormatValidationTests {

        @Test
        @DisplayName("should throw NPE when text is null")
        void shouldThrowNpeWhenTextIsNull() {
            assertThatThrownBy(() -> AnsiColor.BOLD_GREEN_TEXT.format(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("text");
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

    @Nested
    @DisplayName("format() with valid text")
    class FormatValidInputTests {

        @Test
        @DisplayName("RESET format should wrap text with reset code")
        void resetFormatShouldWrapText() {
            var result = AnsiColor.RESET.format("test");
            assertThat(result).startsWith(AnsiColor.RESET.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
            assertThat(result).contains("test");
        }

        @Test
        @DisplayName("BOLD_GREEN_TEXT format should wrap text and append reset")
        void boldGreenTextFormatShouldWrapAndAppendReset() {
            var result = AnsiColor.BOLD_GREEN_TEXT.format("Success");
            assertThat(result).startsWith(AnsiColor.BOLD_GREEN_TEXT.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
            assertThat(result).contains("Success");
        }

        @Test
        @DisplayName("BOLD_RED_TEXT format should wrap text and append reset")
        void boldRedTextFormatShouldWrapAndAppendReset() {
            var result = AnsiColor.BOLD_RED_TEXT.format("Error");
            assertThat(result).startsWith(AnsiColor.BOLD_RED_TEXT.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
        }

        @Test
        @DisplayName("BOLD_BLUE_TEXT format should wrap text and append reset")
        void boldBlueTextFormatShouldWrapAndAppendReset() {
            var result = AnsiColor.BOLD_BLUE_TEXT.format("Info");
            assertThat(result).startsWith(AnsiColor.BOLD_BLUE_TEXT.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
        }

        @Test
        @DisplayName("BOLD_ORANGE_TEXT format should wrap text and append reset")
        void boldOrangeTextFormatShouldWrapAndAppendReset() {
            var result = AnsiColor.BOLD_ORANGE_TEXT.format("Warning");
            assertThat(result).startsWith(AnsiColor.BOLD_ORANGE_TEXT.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
        }

        @Test
        @DisplayName("BOLD_WHITE_TEXT format should wrap text and append reset")
        void boldWhiteTextFormatShouldWrapAndAppendReset() {
            var result = AnsiColor.BOLD_WHITE_TEXT.format("Neutral");
            assertThat(result).startsWith(AnsiColor.BOLD_WHITE_TEXT.getCode());
            assertThat(result).endsWith(AnsiColor.RESET.getCode());
        }
    }

    @Nested
    @DisplayName("getCode() tests")
    class GetCodeTests {

        @Test
        @DisplayName("RESET getCode should return reset escape sequence")
        void resetGetCodeShouldReturnResetSequence() {
            assertThat(AnsiColor.RESET.getCode()).isEqualTo("\033[0m");
        }

        @Test
        @DisplayName("BOLD_GREEN_TEXT getCode should return green escape sequence")
        void boldGreenGetCodeShouldReturnGreenSequence() {
            assertThat(AnsiColor.BOLD_GREEN_TEXT.getCode()).isEqualTo("\033[1;32m");
        }

        @Test
        @DisplayName("BOLD_RED_TEXT getCode should return red escape sequence")
        void boldRedGetCodeShouldReturnRedSequence() {
            assertThat(AnsiColor.BOLD_RED_TEXT.getCode()).isEqualTo("\033[1;31m");
        }

        @Test
        @DisplayName("BOLD_BLUE_TEXT getCode should return blue escape sequence")
        void boldBlueGetCodeShouldReturnBlueSequence() {
            assertThat(AnsiColor.BOLD_BLUE_TEXT.getCode()).isEqualTo("\033[1;34m");
        }

        @Test
        @DisplayName("BOLD_ORANGE_TEXT getCode should return orange escape sequence")
        void boldOrangeGetCodeShouldReturnOrangeSequence() {
            assertThat(AnsiColor.BOLD_ORANGE_TEXT.getCode()).isEqualTo("\033[1;33m");
        }

        @Test
        @DisplayName("BOLD_WHITE_TEXT getCode should return white escape sequence")
        void boldWhiteGetCodeShouldReturnWhiteSequence() {
            assertThat(AnsiColor.BOLD_WHITE_TEXT.getCode()).isEqualTo("\033[1;37m");
        }
    }
}
