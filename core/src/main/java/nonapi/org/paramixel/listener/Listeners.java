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

package nonapi.org.paramixel.listener;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import nonapi.org.paramixel.support.AnsiColor;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;
import org.paramixel.api.Status;

/**
 * Formatting helpers shared by listener implementations.
 */
public final class Listeners {

    private static final Pattern NEWLINES = Pattern.compile("[\\n\\r]+");

    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" {2,}");

    private Listeners() {
        // Intentionally empty
    }

    /**
     * Formats a status without ANSI color.
     *
     * @param status the status to format; must not be {@code null}
     * @return the status text
     */
    public static String formatStatus(final Status status) {
        Objects.requireNonNull(status, "status is null");
        return status.name();
    }

    /**
     * Formats a status with ANSI color.
     *
     * @param status the status to format; must not be {@code null}
     * @return the colored status text
     */
    public static String formatAnsiStatus(final Status status) {
        Objects.requireNonNull(status, "status is null");
        return formatAnsiStatusName(status.name());
    }

    /**
     * Formats a descriptor's current outcome without ANSI color.
     *
     * @param descriptor the descriptor to format; must not be {@code null}
     * @return the outcome text
     */
    public static String formatStatus(final Descriptor descriptor) {
        return statusName(Objects.requireNonNull(descriptor, "descriptor is null"));
    }

    /**
     * Formats a descriptor's current outcome with ANSI color.
     *
     * @param descriptor the descriptor to format; must not be {@code null}
     * @return the colored outcome text
     */
    public static String formatAnsiStatus(final Descriptor descriptor) {
        return formatAnsiStatusName(formatStatus(descriptor));
    }

    /**
     * Formats a result's effective outcome without ANSI color.
     *
     * @param result the result to format; must not be {@code null}
     * @return the outcome text
     */
    public static String formatStatus(final Result result) {
        return statusName(Objects.requireNonNull(result, "result is null"));
    }

    /**
     * Formats a result's effective outcome with ANSI color.
     *
     * @param result the result to format; must not be {@code null}
     * @return the colored outcome text
     */
    public static String formatAnsiStatus(final Result result) {
        return formatAnsiStatusName(formatStatus(result));
    }

    /**
     * Formats the descriptor path from root to descriptor by walking the tree.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the display path
     */
    public static String formatNamePath(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        var names = new ArrayDeque<String>();
        for (var d = descriptor; d != null; d = d.parent().orElse(null)) {
            var name = d.action().displayName();
            if (!Constants.ROOT_NAME.equals(name)) {
                names.addFirst(name);
            }
        }
        return String.join(" / ", names);
    }

    /**
     * Formats the descriptor id path from root to descriptor by walking the tree.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the id path
     */
    public static String formatIdPath(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        var ids = new ArrayDeque<String>();
        for (var d = descriptor; d != null; d = d.parent().orElse(null)) {
            ids.addFirst(d.id());
        }
        return String.join("-", ids);
    }

    /**
     * Formats a descriptor exception for single-line reports.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the exception text, or {@code null} when not applicable
     */
    public static String formatException(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        if (!descriptor.isFailed()) {
            return null;
        }
        return descriptor
                .throwable()
                .map(throwable -> {
                    var message = sanitizeMessage(throwable.getMessage());
                    return message == null || message.isBlank()
                            ? throwable.getClass().getName()
                            : throwable.getClass().getName() + ": " + message;
                })
                .or(() -> descriptor.message().map(Listeners::sanitizeMessage))
                .orElse(null);
    }

    private static String statusName(final Descriptor descriptor) {
        if (descriptor.isFailed()) {
            return "FAILED";
        }
        if (descriptor.isAborted()) {
            return "ABORTED";
        }
        if (!descriptor.isCompleted()) {
            return "RUNNING";
        }
        if (descriptor.isSkipped()) {
            return "SKIPPED";
        }
        return "PASSED";
    }

    private static String statusName(final Result result) {
        if (result.isFailed()) {
            return "FAILED";
        }
        if (result.isAborted()) {
            return "ABORTED";
        }
        if (result.isSkipped()) {
            return "SKIPPED";
        }
        return "PASSED";
    }

    private static String formatAnsiStatusName(final String statusName) {
        return switch (statusName) {
            case "PASSED" -> AnsiColor.BOLD_GREEN_TEXT.format(statusName);
            case "FAILED" -> AnsiColor.BOLD_RED_TEXT.format(statusName);
            case "SKIPPED" -> AnsiColor.BOLD_YELLOW_TEXT.format(statusName);
            case "ABORTED" -> AnsiColor.BOLD_ORANGE_TEXT.format(statusName);
            case "RUNNING" -> AnsiColor.BOLD_BLUE_TEXT.format(statusName);
            default -> statusName;
        };
    }

    /**
     * Returns the elapsed milliseconds between a descriptor's start and completion timestamps.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return elapsed milliseconds, or {@code 0} when either timestamp is not available
     */
    public static long elapsedMillis(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        var start = descriptor.startedAt();
        var completed = descriptor.completedAt();
        if (start.isEmpty() || completed.isEmpty()) {
            return 0L;
        }
        return Math.max(
                0L,
                Duration.between(start.orElseThrow(), completed.orElseThrow()).toMillis());
    }

    /**
     * Sanitizes a message for single-line display.
     *
     * @param message the message, or {@code null}
     * @return the sanitized message, or {@code null}
     */
    public static String sanitizeMessage(final String message) {
        if (message == null) {
            return null;
        }
        return MULTIPLE_SPACES
                .matcher(NEWLINES.matcher(message.replace('\t', ' ')).replaceAll(" - "))
                .replaceAll(" ")
                .strip();
    }

    /**
     * Parses a comma-separated list of exclude tokens into an {@link EnumSet} of {@link ExcludeTarget}.
     *
     * <p>Supported tokens are defined by the {@link org.paramixel.api.Configuration#LISTENER_EXCLUDE}
     * configuration key. Null or blank input returns an empty set. Unrecognized tokens are silently ignored.
     *
     * @param value the raw configuration value, or {@code null}
     * @return the set of exclude targets; never {@code null}
     */
    public static EnumSet<ExcludeTarget> parseExcludes(final String value) {
        var excludes = EnumSet.noneOf(ExcludeTarget.class);
        if (value == null || value.isBlank()) {
            return excludes;
        }
        for (var token : value.split(",")) {
            var trimmed = token.strip().toLowerCase(Locale.ROOT);
            switch (trimmed) {
                case "status" -> {
                    excludes.add(ExcludeTarget.STATUS_HEADER);
                    excludes.add(ExcludeTarget.STATUS_FOOTER);
                }
                case "status.header" -> excludes.add(ExcludeTarget.STATUS_HEADER);
                case "status.footer" -> excludes.add(ExcludeTarget.STATUS_FOOTER);
                case "summary.header" -> excludes.add(ExcludeTarget.SUMMARY_HEADER);
                case "summary.tree" -> excludes.add(ExcludeTarget.SUMMARY_TREE);
                case "summary.footer" -> excludes.add(ExcludeTarget.SUMMARY_FOOTER);
                case "quiet" -> {
                    excludes.add(ExcludeTarget.STATUS_HEADER);
                    excludes.add(ExcludeTarget.STATUS_FOOTER);
                    excludes.add(ExcludeTarget.SUMMARY_TREE);
                }
                case "all" -> {
                    excludes.add(ExcludeTarget.STATUS_HEADER);
                    excludes.add(ExcludeTarget.STATUS_FOOTER);
                    excludes.add(ExcludeTarget.SUMMARY_HEADER);
                    excludes.add(ExcludeTarget.SUMMARY_TREE);
                    excludes.add(ExcludeTarget.SUMMARY_FOOTER);
                }
                default -> {
                    /* silently ignore unrecognized tokens */
                }
            }
        }
        return excludes;
    }
}
