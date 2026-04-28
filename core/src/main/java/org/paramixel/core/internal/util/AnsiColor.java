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

import java.util.Objects;

/**
 * ANSI escape codes for terminal colors.
 */
public enum AnsiColor {
    RESET("\033[0m"),

    BOLD_BLUE_TEXT("\033[1;34m"),

    BOLD_GREEN_TEXT("\033[1;32m"),

    BOLD_RED_TEXT("\033[1;31m"),

    BOLD_ORANGE_TEXT("\033[1;33m"),

    BOLD_WHITE_TEXT("\033[1;37m"),

    GREEN_TEXT("\033[32m"),

    RED_TEXT("\033[31m"),

    YELLOW_TEXT("\033[33m");

    private final String code;

    AnsiColor(final String code) {
        this.code = code;
    }

    /**
     * Returns the ANSI escape code.
     *
     * @return The code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Formats text with this color.
     *
     * @param text The text to format; must not be null or blank.
     * @return The formatted text.
     */
    public String format(final String text) {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return code + text + RESET.code;
    }
}
