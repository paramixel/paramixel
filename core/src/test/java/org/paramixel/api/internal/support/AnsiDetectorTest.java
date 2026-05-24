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

package org.paramixel.api.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnsiDetector")
class AnsiDetectorTest {

    @Test
    @DisplayName("detect returns true when console is attached and no disabling env vars")
    void detectReturnsTrueWhenConsoleAttachedAndNoDisablingEnvVars() {
        assertThat(AnsiDetector.detect(true, null, "xterm")).isTrue();
    }

    @Test
    @DisplayName("detect returns true when console is attached and env vars are absent")
    void detectReturnsTrueWhenConsoleAttachedAndEnvVarsAbsent() {
        assertThat(AnsiDetector.detect(true, null, null)).isTrue();
    }

    @Test
    @DisplayName("detect returns false when no console is attached")
    void detectReturnsFalseWhenNoConsole() {
        assertThat(AnsiDetector.detect(false, null, null)).isFalse();
    }

    @Test
    @DisplayName("detect returns false when NO_COLOR is set")
    void detectReturnsFalseWhenNoColorIsSet() {
        assertThat(AnsiDetector.detect(true, "1", null)).isFalse();
    }

    @Test
    @DisplayName("detect returns false when NO_COLOR is empty string")
    void detectReturnsFalseWhenNoColorIsEmpty() {
        assertThat(AnsiDetector.detect(true, "", null)).isFalse();
    }

    @Test
    @DisplayName("detect returns false when TERM is dumb")
    void detectReturnsFalseWhenTermIsDumb() {
        assertThat(AnsiDetector.detect(true, null, "dumb")).isFalse();
    }

    @Test
    @DisplayName("detect returns true when TERM is not dumb")
    void detectReturnsTrueWhenTermIsNotDumb() {
        assertThat(AnsiDetector.detect(true, null, "xterm-256color")).isTrue();
    }

    @Test
    @DisplayName("detect returns false when both NO_COLOR set and TERM is dumb")
    void detectReturnsFalseWhenNoColorAndDumbTerm() {
        assertThat(AnsiDetector.detect(true, "1", "dumb")).isFalse();
    }

    @Test
    @DisplayName("detect returns false when no console even if NO_COLOR and TERM are fine")
    void detectReturnsFalseWhenNoConsoleOverridesEnvVars() {
        assertThat(AnsiDetector.detect(false, null, "xterm")).isFalse();
    }

    @Test
    @DisplayName("detect returns false when NO_COLOR is whitespace-only string")
    void detectReturnsFalseWhenNoColorIsWhitespaceOnly() {
        assertThat(AnsiDetector.detect(true, "   ", null)).isFalse();
    }
}
