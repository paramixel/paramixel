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

/**
 * Detects whether the current process environment supports ANSI escape codes on the console output.
 *
 * <p>Detection checks whether a console is attached, the {@code NO_COLOR} environment variable is unset (per
 * <a href="https://no-color.org/">no-color.org</a>), and the {@code TERM} environment variable is not {@code dumb}.
 */
public final class AnsiDetector {

    private AnsiDetector() {
        // Intentionally empty
    }

    /**
     * Returns whether ANSI escape codes should be emitted based on the current process environment.
     *
     * @return {@code true} when a console is attached and no environment variable disables color
     */
    public static boolean isAnsiAvailable() {
        return detect(System.console() != null, System.getenv("NO_COLOR"), System.getenv("TERM"));
    }

    /**
     * Evaluates whether ANSI escape codes should be emitted, given console presence and environment variables.
     *
     * <p>ANSI is disabled when no console is attached, the {@code NO_COLOR} environment variable is set
     * (per <a href="https://no-color.org/">no-color.org</a> — any value, including empty, disables color),
     * or the {@code TERM} environment variable is {@code "dumb"}.
     *
     * @param hasConsole whether {@link System#console()} returned a non-null reference
     * @param noColor the value of the {@code NO_COLOR} environment variable, or {@code null} when unset
     * @param term the value of the {@code TERM} environment variable, or {@code null} when unset
     * @return {@code true} when ANSI output is appropriate for the given inputs
     */
    public static boolean detect(final boolean hasConsole, final String noColor, final String term) {
        if (!hasConsole) {
            return false;
        }
        if (noColor != null) {
            return false;
        }
        if ("dumb".equals(term)) {
            return false;
        }
        return true;
    }
}
