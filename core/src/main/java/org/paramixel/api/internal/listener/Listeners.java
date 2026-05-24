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

package org.paramixel.api.internal.listener;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.regex.Pattern;
import org.paramixel.api.Status;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.internal.listener.support.Constants;
import org.paramixel.api.internal.support.AnsiColor;

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
        Objects.requireNonNull(status, "status must not be null");
        return status.name();
    }

    /**
     * Formats a status with ANSI color.
     *
     * @param status the status to format; must not be {@code null}
     * @return the colored status text
     */
    public static String formatAnsiStatus(final Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status.name()) {
            case "PASSED" -> AnsiColor.BOLD_GREEN_TEXT.format(status.name());
            case "FAILED" -> AnsiColor.BOLD_RED_TEXT.format(status.name());
            case "SKIPPED" -> AnsiColor.BOLD_YELLOW_TEXT.format(status.name());
            case "ABORTED" -> AnsiColor.BOLD_ORANGE_TEXT.format(status.name());
            case "RUNNING" -> AnsiColor.BOLD_BLUE_TEXT.format(status.name());
            default -> status.name();
        };
    }

    /**
     * Formats the descriptor path from root to descriptor by walking the tree.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the display path
     */
    public static String formatNamePath(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        var names = new ArrayDeque<String>();
        for (var d = descriptor; d != null; d = d.parent().orElse(null)) {
            var name = d.metadata().name();
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
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        var ids = new ArrayDeque<String>();
        for (var d = descriptor; d != null; d = d.parent().orElse(null)) {
            ids.addFirst(d.metadata().id());
        }
        return String.join("-", ids);
    }

    /**
     * Formats the action kind for a descriptor.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the kind label
     */
    public static String formatKind(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return descriptor.metadata().kind();
    }

    /**
     * Formats a descriptor exception for single-line reports.
     *
     * @param descriptor the descriptor; must not be {@code null}
     * @return the exception text, or {@code null} when not applicable
     */
    public static String formatException(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        if (!descriptor.metadata().status().isFailed()) {
            return null;
        }
        return descriptor
                .metadata()
                .throwable()
                .map(throwable -> {
                    var message = sanitizeMessage(throwable.getMessage());
                    return message == null || message.isBlank()
                            ? throwable.getClass().getName()
                            : throwable.getClass().getName() + ": " + message;
                })
                .or(() -> descriptor.metadata().message().map(Listeners::sanitizeMessage))
                .orElse(null);
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
}
