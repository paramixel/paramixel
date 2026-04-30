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

package org.paramixel.maven.plugin.internal.util;

import org.paramixel.core.support.AnsiColor;

public enum AnsiLabel {
    INFO(AnsiColor.BOLD_BLUE_TEXT, "INFO", true),

    TEST(AnsiColor.BOLD_WHITE_TEXT, "TEST", false),

    BRACKETED_TEST(AnsiColor.BOLD_WHITE_TEXT, "TEST", true),

    PASS(AnsiColor.BOLD_GREEN_TEXT, "PASS", false),

    FAIL(AnsiColor.BOLD_RED_TEXT, "FAIL", false),

    SKIP(AnsiColor.BOLD_ORANGE_TEXT, "SKIP", false),

    BRACKETED_PASS(AnsiColor.BOLD_GREEN_TEXT, "PASS", true),

    BRACKETED_FAIL(AnsiColor.BOLD_RED_TEXT, "FAIL", true),

    BRACKETED_SKIP(AnsiColor.BOLD_ORANGE_TEXT, "SKIP", true),

    MARK(AnsiColor.BOLD_GREEN_TEXT, "\u2713", false),

    CHECK_MARK(AnsiColor.BOLD_GREEN_TEXT, "\u2713", false),

    CROSS_MARK(AnsiColor.BOLD_RED_TEXT, "\u2717", false),

    SKIP_MARK(AnsiColor.BOLD_ORANGE_TEXT, "\u2298", false),

    WARN_MARK(AnsiColor.BOLD_ORANGE_TEXT, "\u26A0", false),

    UNKNOWN_MARK(AnsiColor.BOLD_WHITE_TEXT, "?", false);

    private final AnsiColor color;

    private final String text;

    private final boolean bracketed;

    AnsiLabel(final AnsiColor color, final String text, final boolean bracketed) {
        this.color = color;
        this.text = text;
        this.bracketed = bracketed;
    }

    public String getCode() {
        return color.getCode();
    }

    @Override
    public String toString() {
        var formatted = color.format(text);
        return bracketed
                ? AnsiColor.BOLD_WHITE_TEXT.format("[") + formatted + AnsiColor.BOLD_WHITE_TEXT.format("]")
                : formatted;
    }
}
