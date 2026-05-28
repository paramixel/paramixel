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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Expands tilde-prefixed paths to absolute paths.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>{@code ~} — current user's home directory
 *   <li>{@code ~/path} — path relative to current user's home
 *   <li>{@code ~user} — another user's home directory (Unix/macOS only)
 *   <li>{@code ~user/path} — path relative to another user's home (Unix/macOS only)
 * </ul>
 *
 * <p>On Windows, tilde expansion is a no-op and the input is returned as-is.
 */
public class TildePathExpander {

    private static final OsDetector DEFAULT_OS_DETECTOR = new ConcreteOsDetector();
    private static final long PROCESS_TIMEOUT_SECONDS = 5;
    private static final Pattern USER_PATTERN = Pattern.compile("^~([^/]+)(/.*)?$");

    private TildePathExpander() {
        // Intentionally empty
    }

    /**
     * Expands a tilde-prefixed path to an absolute path.
     *
     * @param input the path to expand
     * @return the expanded path, or the original path on Windows
     * @throws UncheckedIOException if the user in a {@code ~user} expression does not exist
     * @throws NullPointerException if {@code input} is {@code null}
     * @throws IllegalArgumentException if {@code input} is blank
     */
    public static Path expand(final String input) {
        OsDetector osDetector = DEFAULT_OS_DETECTOR;
        return expand(
                input,
                osDetector,
                osDetector.isMac()
                        ? TildePathExpander::lookupHomeDirectoryMac
                        : TildePathExpander::lookupHomeDirectoryLinux);
    }

    /**
     * Expands a tilde-prefixed path using the supplied OS detector and user-home resolver.
     *
     * @param input the path to expand
     * @param osDetector the OS detector used to decide whether tilde expansion applies
     * @param resolver the strategy for looking up another user's home directory
     * @return the expanded path, or the original path on Windows
     * @throws NullPointerException if {@code input} or {@code osDetector} is {@code null}
     * @throws IllegalArgumentException if {@code input} is blank
     * @throws UncheckedIOException if the user in a {@code ~user} expression does not exist
     */
    static Path expand(final String input, final OsDetector osDetector, final UserHomeResolver resolver) {
        Objects.requireNonNull(input, "input is null");
        Arguments.requireNonBlank(input, "input is blank");

        if (osDetector.isWindows()) {
            return Paths.get(input);
        }

        // Handle "~" and "~/..."
        if ("~".equals(input) || input.startsWith("~/")) {

            String home = System.getProperty("user.home");

            if ("~".equals(input)) {
                return Paths.get(home);
            }

            return Paths.get(home + input.substring(1));
        }

        // Handle "~user" and "~user/..."
        var m = USER_PATTERN.matcher(input);

        if (m.matches()) {
            String user = m.group(1);
            String rest = m.group(2);

            String home;
            try {
                home = resolver.lookupHome(user);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            if (home == null) {
                throw new UncheckedIOException(new IOException("Unknown user: " + user));
            }

            if (rest == null) {
                return Paths.get(home);
            }

            return Paths.get(home + rest);
        }

        // Normal path
        return Paths.get(input);
    }

    /**
     * Looks up a user's home directory on Linux using {@code getent passwd}.
     *
     * <p>This works with local users as well as LDAP, NIS, and SSSD.
     *
     * @param user the username to look up
     * @return the home directory path, or {@code null} if the user does not exist
     * @throws IOException if the lookup fails
     */
    private static String lookupHomeDirectoryLinux(final String user) throws IOException {
        var process = new ProcessBuilder("getent", "passwd", user)
                .redirectErrorStream(true)
                .start();

        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                return null;
            }

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();

                if (line == null || line.isEmpty()) {
                    return null;
                }

                // passwd format:
                // name:x:uid:gid:gecos:home:shell
                String[] parts = line.split(":");

                if (parts.length < 6) {
                    return null;
                }

                return parts[5];
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for getent", e);
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Looks up a user's home directory on macOS using {@code dscl}.
     *
     * <p>This works with local users as well as Directory Services (LDAP, Open Directory, etc.).
     *
     * @param user the username to look up
     * @return the home directory path, or {@code null} if the user does not exist
     * @throws IOException if the lookup fails
     */
    private static String lookupHomeDirectoryMac(final String user) throws IOException {
        var process = new ProcessBuilder("dscl", ".", "-read", "/Users/" + user, "NFSHomeDirectory")
                .redirectErrorStream(true)
                .start();

        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                return null;
            }

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();

                if (line == null || line.isEmpty()) {
                    return null;
                }

                // dscl output format:
                // NFSHomeDirectory: /Users/username
                int colonIndex = line.indexOf(": ");

                if (colonIndex < 0) {
                    return null;
                }

                return line.substring(colonIndex + 2);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for dscl", e);
        } finally {
            process.destroyForcibly();
        }
    }
}
