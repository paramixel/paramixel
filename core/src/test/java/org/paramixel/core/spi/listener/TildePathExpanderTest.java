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

package org.paramixel.core.spi.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TildePathExpander")
class TildePathExpanderTest {

    private static final String USER_HOME = System.getProperty("user.home");

    @Test
    @DisplayName("expands tilde to user home directory")
    void expandsTildeToUserHome() {
        Path result = TildePathExpander.expand("~");
        assertThat(result).isAbsolute();
        assertThat(result.toString()).isEqualTo(USER_HOME);
    }

    @Test
    @DisplayName("expands tilde slash path to path under user home")
    void expandsTildeSlashPath() {
        Path result = TildePathExpander.expand("~/sub/dir");
        assertThat(result).isAbsolute();
        assertThat(result.toString()).isEqualTo(USER_HOME + "/sub/dir");
    }

    @Test
    @DisplayName("passes through absolute path unchanged")
    void passesThroughAbsolutePath() {
        Path result = TildePathExpander.expand("/absolute/path");
        assertThat(result.toString()).isEqualTo("/absolute/path");
    }

    @Test
    @DisplayName("passes through relative path unchanged")
    void passesThroughRelativePath() {
        Path result = TildePathExpander.expand("relative/path");
        assertThat(result.toString()).isEqualTo("relative/path");
    }

    @Test
    @DisplayName("throws NullPointerException for null input")
    void throwsForNullInput() {
        assertThatThrownBy(() -> TildePathExpander.expand(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("input must not be null");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for blank input")
    void throwsForBlankInput() {
        assertThatThrownBy(() -> TildePathExpander.expand("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("input must not be blank");
    }

    @Test
    @DisplayName("throws UncheckedIOException for non-existent user")
    void throwsForNonExistentUser() {
        assertThatThrownBy(() -> TildePathExpander.expand("~nonexistentuser12345"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
