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

package nonapi.org.paramixel.reporting;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import nonapi.org.paramixel.support.AnsiColor;
import nonapi.org.paramixel.support.AnsiDetector;
import org.paramixel.api.Configuration;
import org.paramixel.api.Status;

/**
 * Resolves console formatting: whether ANSI codes are enabled and which sections were excluded by configuration.
 */
final class ConsoleSupport {

    private final boolean ansi;
    private final Set<String> excluded;

    ConsoleSupport(final Configuration configuration) {
        this.ansi = resolveAnsi(configuration.getString(Configuration.ANSI).orElse("auto"));
        this.excluded = parseExcluded(
                configuration.getString(Configuration.LISTENER_EXCLUDE).orElse(""));
    }

    private static boolean resolveAnsi(final String value) {
        return switch (value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> AnsiDetector.isAnsiAvailable();
        };
    }

    private static Set<String> parseExcluded(final String value) {
        final var tokens = new LinkedHashSet<String>();
        for (String token : value.split(",")) {
            final var trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            switch (trimmed) {
                case "status" -> {
                    tokens.add("status.header");
                    tokens.add("status.footer");
                }
                case "quiet" -> {
                    tokens.add("status.header");
                    tokens.add("status.footer");
                    tokens.add("summary.tree");
                }
                case "all" -> {
                    tokens.add("status.header");
                    tokens.add("status.footer");
                    tokens.add("summary.header");
                    tokens.add("summary.tree");
                    tokens.add("summary.footer");
                }
                default -> tokens.add(trimmed);
            }
        }
        return tokens;
    }

    /**
     * Returns whether a section is enabled.
     *
     * @param section the section token; must not be {@code null}
     * @return {@code true} unless excluded
     */
    boolean enabled(final String section) {
        return !excluded.contains(section);
    }

    /**
     * Returns the console line prefix: the blue, bracketed {@code PARAMIXEL} wordmark followed by one space.
     *
     * <p>Every console line Paramixel prints begins with this prefix, so framework output is visually separable from
     * test output and status columns stay aligned. The prefix has a fixed visible width; ANSI color codes do not affect
     * alignment.
     *
     * @return the prefix; never {@code null}
     */
    String prefix() {
        return "[" + color(AnsiColor.BOLD_BLUE_TEXT, "PARAMIXEL") + "] ";
    }

    /**
     * Prefixes one console line.
     *
     * @param text the line content; must not be {@code null}
     * @return the prefixed line; never {@code null}
     */
    String line(final String text) {
        return prefix() + text;
    }

    /**
     * Returns the short, unpadded label of a status kind.
     *
     * @param kind the status kind; must not be {@code null}
     * @return the label; never {@code null} or blank
     */
    static String shortLabel(final Status.Kind kind) {
        return switch (kind) {
            case PASSED -> "PASS";
            case FAILED -> "FAIL";
            case SKIPPED -> "SKIP";
            case ABORTED -> "ABORT";
            case CANCELED -> "CANCEL";
            case PENDING -> "PEND";
            case RUNNING -> "RUN";
        };
    }

    /**
     * Returns the color of a status kind.
     *
     * @param kind the status kind; must not be {@code null}
     * @return the color; never {@code null}
     */
    static AnsiColor colorOf(final Status.Kind kind) {
        return switch (kind) {
            case PASSED -> AnsiColor.BOLD_GREEN_TEXT;
            case FAILED -> AnsiColor.BOLD_RED_TEXT;
            case SKIPPED -> AnsiColor.BOLD_YELLOW_TEXT;
            case ABORTED -> AnsiColor.BOLD_ORANGE_TEXT;
            case CANCELED -> AnsiColor.BOLD_GRAY_TEXT;
            case PENDING, RUNNING -> AnsiColor.BOLD_WHITE_TEXT;
        };
    }

    /**
     * Returns the short status label that starts every status line.
     *
     * <p>The label is not padded: a single space separates it from the path or message. Nothing is prepended, so a
     * status line never begins with indentation.
     *
     * @param status the status; must not be {@code null}
     * @return the possibly colored label; never {@code null}
     */
    String label(final Status status) {
        return label(status.kind());
    }

    /**
     * Returns the short status label for a kind.
     *
     * @param kind the status kind; must not be {@code null}
     * @return the possibly colored label; never {@code null}
     */
    String label(final Status.Kind kind) {
        return color(colorOf(kind), shortLabel(kind));
    }

    /**
     * Returns a short label for inline use.
     *
     * @param status the status; must not be {@code null}
     * @return the possibly colored label; never {@code null}
     */
    String inlineLabel(final Status status) {
        return label(status);
    }

    /**
     * Applies a color when ANSI output is enabled.
     *
     * @param color the color; must not be {@code null}
     * @param text the text; must not be blank
     * @return the possibly formatted text; never {@code null}
     */
    String color(final AnsiColor color, final String text) {
        if (!ansi || text == null || text.isBlank()) {
            return text;
        }
        return color.format(text);
    }

    /**
     * Returns whether ANSI output is enabled.
     *
     * @return {@code true} when enabled
     */
    boolean ansi() {
        return ansi;
    }
}
