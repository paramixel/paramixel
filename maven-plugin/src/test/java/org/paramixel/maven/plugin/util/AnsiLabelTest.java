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

package org.paramixel.maven.plugin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.support.AnsiColor;
import org.paramixel.maven.plugin.internal.util.AnsiLabel;

@DisplayName("AnsiLabel tests")
class AnsiLabelTest {

    @Nested
    @DisplayName("toString for bracketed labels")
    class BracketedToStringTests {

        @Test
        @DisplayName("BRACKETED_TEST should include brackets")
        void bracketedTestShouldIncludeBrackets() {
            var result = AnsiLabel.BRACKETED_TEST.toString();
            assertThat(result).contains("TEST");
            assertThat(AnsiLabel.BRACKETED_TEST.name()).startsWith("BRACKETED_");
        }

        @Test
        @DisplayName("BRACKETED_PASS should include brackets")
        void bracketedPassShouldIncludeBrackets() {
            var result = AnsiLabel.BRACKETED_PASS.toString();
            assertThat(result).contains("PASS");
            assertThat(AnsiLabel.BRACKETED_PASS.name()).startsWith("BRACKETED_");
        }

        @Test
        @DisplayName("BRACKETED_FAIL should include brackets")
        void bracketedFailShouldIncludeBrackets() {
            var result = AnsiLabel.BRACKETED_FAIL.toString();
            assertThat(result).contains("FAIL");
            assertThat(AnsiLabel.BRACKETED_FAIL.name()).startsWith("BRACKETED_");
        }

        @Test
        @DisplayName("BRACKETED_SKIP should include brackets")
        void bracketedSkipShouldIncludeBrackets() {
            var result = AnsiLabel.BRACKETED_SKIP.toString();
            assertThat(result).contains("SKIP");
            assertThat(AnsiLabel.BRACKETED_SKIP.name()).startsWith("BRACKETED_");
        }
    }

    @Nested
    @DisplayName("toString for non-bracketed labels")
    class NonBracketedToStringTests {

        @Test
        @DisplayName("INFO should include brackets")
        void infoShouldIncludeBrackets() {
            var result = AnsiLabel.INFO.toString();
            assertThat(result).contains("INFO");
        }

        @Test
        @DisplayName("TEST should not include brackets")
        void testShouldNotIncludeBrackets() {
            var result = AnsiLabel.TEST.toString();
            assertThat(result).contains("TEST");
            assertThat(AnsiLabel.TEST.name()).doesNotStartWith("BRACKETED_");
        }

        @Test
        @DisplayName("PASS should not include brackets")
        void passShouldNotIncludeBrackets() {
            var result = AnsiLabel.PASS.toString();
            assertThat(result).contains("PASS");
            assertThat(AnsiLabel.PASS.name()).doesNotStartWith("BRACKETED_");
        }

        @Test
        @DisplayName("FAIL should not include brackets")
        void failShouldNotIncludeBrackets() {
            var result = AnsiLabel.FAIL.toString();
            assertThat(result).contains("FAIL");
            assertThat(AnsiLabel.FAIL.name()).doesNotStartWith("BRACKETED_");
        }

        @Test
        @DisplayName("SKIP should not include brackets")
        void skipShouldNotIncludeBrackets() {
            var result = AnsiLabel.SKIP.toString();
            assertThat(result).contains("SKIP");
            assertThat(AnsiLabel.SKIP.name()).doesNotStartWith("BRACKETED_");
        }

        @Test
        @DisplayName("CHECK_MARK should contain checkmark character")
        void checkMarkShouldContainCheckmark() {
            assertThat(AnsiLabel.CHECK_MARK.toString()).contains("\u2713");
        }

        @Test
        @DisplayName("CROSS_MARK should contain cross mark character")
        void crossMarkShouldContainCrossMark() {
            assertThat(AnsiLabel.CROSS_MARK.toString()).contains("\u2717");
        }

        @Test
        @DisplayName("SKIP_MARK should contain skip mark character")
        void skipMarkShouldContainSkipMark() {
            assertThat(AnsiLabel.SKIP_MARK.toString()).contains("\u2298");
        }

        @Test
        @DisplayName("WARN_MARK should contain warning mark character")
        void warnMarkShouldContainWarnMark() {
            assertThat(AnsiLabel.WARN_MARK.toString()).contains("\u26A0");
        }

        @Test
        @DisplayName("UNKNOWN_MARK should contain question mark character")
        void unknownMarkShouldContainQuestionMark() {
            assertThat(AnsiLabel.UNKNOWN_MARK.toString()).contains("?");
        }

        @Test
        @DisplayName("MARK should contain checkmark character")
        void markShouldContainCheckmark() {
            assertThat(AnsiLabel.MARK.toString()).contains("\u2713");
        }
    }

    @Nested
    @DisplayName("getCode tests")
    class GetCodeTests {

        @Test
        @DisplayName("INFO getCode should return blue ANSI code")
        void infoGetCodeShouldReturnBlueCode() {
            assertThat(AnsiLabel.INFO.getCode()).isEqualTo(AnsiColor.BOLD_BLUE_TEXT.getCode());
        }

        @Test
        @DisplayName("PASS getCode should return green ANSI code")
        void passGetCodeShouldReturnGreenCode() {
            assertThat(AnsiLabel.PASS.getCode()).isEqualTo(AnsiColor.BOLD_GREEN_TEXT.getCode());
        }

        @Test
        @DisplayName("FAIL getCode should return red ANSI code")
        void failGetCodeShouldReturnRedCode() {
            assertThat(AnsiLabel.FAIL.getCode()).isEqualTo(AnsiColor.BOLD_RED_TEXT.getCode());
        }

        @Test
        @DisplayName("SKIP getCode should return orange ANSI code")
        void skipGetCodeShouldReturnOrangeCode() {
            assertThat(AnsiLabel.SKIP.getCode()).isEqualTo(AnsiColor.BOLD_ORANGE_TEXT.getCode());
        }

        @Test
        @DisplayName("TEST getCode should return white ANSI code")
        void testGetCodeShouldReturnWhiteCode() {
            assertThat(AnsiLabel.TEST.getCode()).isEqualTo(AnsiColor.BOLD_WHITE_TEXT.getCode());
        }

        @Test
        @DisplayName("BRACKETED_PASS getCode should return green ANSI code")
        void bracketedPassGetCodeShouldReturnGreenCode() {
            assertThat(AnsiLabel.BRACKETED_PASS.getCode()).isEqualTo(AnsiColor.BOLD_GREEN_TEXT.getCode());
        }

        @Test
        @DisplayName("BRACKETED_FAIL getCode should return red ANSI code")
        void bracketedFailGetCodeShouldReturnRedCode() {
            assertThat(AnsiLabel.BRACKETED_FAIL.getCode()).isEqualTo(AnsiColor.BOLD_RED_TEXT.getCode());
        }

        @Test
        @DisplayName("BRACKETED_SKIP getCode should return orange ANSI code")
        void bracketedSkipGetCodeShouldReturnOrangeCode() {
            assertThat(AnsiLabel.BRACKETED_SKIP.getCode()).isEqualTo(AnsiColor.BOLD_ORANGE_TEXT.getCode());
        }
    }
}
