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

import java.util.Objects;

/**
 * ANSI escape sequences for coloring and styling console status output and report highlighting.
 *
 * <p>Each constant's {@link #format(String)} method wraps the supplied text with the
 * corresponding escape code and a trailing {@link #RESET} sequence. This enum cannot be
 * instantiated.
 */
public enum AnsiColor {

    /**
     * Reset all ANSI formatting.
     */
    RESET("\033[0m"),

    /**
     * Bold blue text.
     */
    BOLD_BLUE_TEXT("\033[1;34m"),

    /**
     * Bold green text.
     */
    BOLD_GREEN_TEXT("\033[1;32m"),

    /**
     * Bold red text.
     */
    BOLD_RED_TEXT("\033[1;31m"),

    /**
     * Bold orange text.
     */
    BOLD_ORANGE_TEXT("\033[1;33m"),

    /**
     * Bold gray text.
     */
    BOLD_GRAY_TEXT("\033[1;90m"),

    /**
     * Bold yellow text.
     */
    BOLD_YELLOW_TEXT("\033[1;38;5;220m"),

    /**
     * Bold white text.
     */
    BOLD_WHITE_TEXT("\033[1;37m");

    private final String code;

    AnsiColor(final String code) {
        this.code = code;
    }

    /**
     * The raw ANSI escape sequence for this color or style.
     *
     * @return the raw ANSI escape sequence
     */
    public String code() {
        return code;
    }

    /**
     * Wraps the supplied text in this ANSI sequence and a trailing reset code.
     *
     * @param text the text to format
     * @return the formatted text
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws IllegalArgumentException if {@code text} is blank
     */
    public String format(final String text) {
        Arguments.requireNonBlank(Objects.requireNonNull(text), "text is blank");
        return code + text + RESET.code;
    }
}
