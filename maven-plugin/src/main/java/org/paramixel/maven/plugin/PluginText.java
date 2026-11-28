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

package org.paramixel.maven.plugin;

import java.util.Objects;

/**
 * The small text helpers the plugin needs, kept local so the plugin depends only on the public Paramixel API.
 *
 * <p>The plugin used to import two internal ({@code nonapi}) Paramixel helpers. Those packages are not exported by
 * the {@code org.paramixel.api} module, so the plugin carries the two behaviors it actually uses.
 */
final class PluginText {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD_RED = "\033[1;31m";

    private PluginText() {
        // Intentionally empty
    }

    /**
     * Returns the supplied text in bold red, followed by a reset.
     *
     * @param text the text to format; must not be {@code null}
     * @return the formatted text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    static String boldRed(final String text) {
        return ANSI_BOLD_RED + Objects.requireNonNull(text, "text is null") + ANSI_RESET;
    }

    /**
     * Requires a non-blank value.
     *
     * @param value the value to check; must not be {@code null} or blank
     * @param message the failure message; must not be {@code null}
     * @return the value
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    static String requireNonBlank(final String value, final String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
