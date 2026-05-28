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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TildePathExpander")
class TildePathExpanderTest {

    private static final String USER_HOME = System.getProperty("user.home");

    private static final OsDetector WINDOWS_OS = new OsDetector() {
        @Override
        public boolean isWindows() {
            return true;
        }

        @Override
        public boolean isMac() {
            return false;
        }
    };

    private static final OsDetector LINUX_OS = new OsDetector() {
        @Override
        public boolean isWindows() {
            return false;
        }

        @Override
        public boolean isMac() {
            return false;
        }
    };

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
                .hasMessage("input is null");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for blank input")
    void throwsForBlankInput() {
        assertThatThrownBy(() -> TildePathExpander.expand("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("input is blank");
    }

    @Test
    @DisplayName("throws UncheckedIOException for non-existent user")
    void throwsForNonExistentUser() {
        assertThatThrownBy(() -> TildePathExpander.expand("~nonexistentuser12345"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("assumes Linux when os.name is unrecognizable")
    void assumesLinuxWhenOsNameUnrecognizable() {
        OsDetector unknownOs = LINUX_OS;

        Path result = TildePathExpander.expand("~testuser", unknownOs, user -> "/home/" + user);

        assertThat(result.toString()).isEqualTo("/home/testuser");
    }

    @Test
    @DisplayName("returns input as-is on Windows")
    void returnsInputAsIsOnWindows() {
        Path result = TildePathExpander.expand("~/some/path", WINDOWS_OS, user -> "/home/" + user);

        assertThat(result.toString()).isEqualTo("~/some/path");
    }

    @Test
    @DisplayName("expands ~user/path to home directory with subpath")
    void expandsUserPathToHomeWithSubpath() {
        Path result = TildePathExpander.expand("~testuser/docs", LINUX_OS, user -> "/home/" + user);

        assertThat(result.toString()).isEqualTo("/home/testuser/docs");
    }

    @Test
    @DisplayName("wraps IOException from resolver in UncheckedIOException")
    void wrapsIOExceptionFromResolver() {
        IOException cause = new IOException("lookup failed");

        assertThatThrownBy(() -> TildePathExpander.expand("~testuser", LINUX_OS, user -> {
                    throw cause;
                }))
                .isInstanceOf(UncheckedIOException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("throws UncheckedIOException when resolver returns null for ~user")
    void throwsWhenResolverReturnsNullForUser() {
        assertThatThrownBy(() -> TildePathExpander.expand("~unknownuser", LINUX_OS, user -> null))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Unknown user: unknownuser");
    }

    @Test
    @DisplayName("returns normal path when input does not start with tilde on Linux")
    void returnsNormalPathWithoutTildeOnLinux() {
        Path result = TildePathExpander.expand("plain/path", LINUX_OS, user -> "/home/" + user);

        assertThat(result.toString()).isEqualTo("plain/path");
    }
}
