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

package org.paramixel.core.internal.listener;

import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * Static formatting methods for status, action kind, exception detail, and JSON escaping used by built-in listener
 * implementations.
 */
final class Listeners {

    private Listeners() {
        // Intentionally empty
    }

    static String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isStaged()) {
            return "STAGED";
        } else if (status.isPass()) {
            return "PASS";
        } else if (status.isFailure()) {
            return "FAIL";
        } else {
            return "SKIP";
        }
    }

    static String formatAnsiStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isStaged()) {
            return AnsiColor.BOLD_GRAY_TEXT.format("STAGED");
        } else if (status.isPass()) {
            return AnsiColor.BOLD_GREEN_TEXT.format("PASS");
        } else if (status.isFailure()) {
            return AnsiColor.BOLD_RED_TEXT.format("FAIL");
        } else {
            return AnsiColor.BOLD_ORANGE_TEXT.format("SKIP");
        }
    }

    static String formatKind(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        Class<?> actionClass = action.getClass();
        if (actionClass.getPackageName().startsWith(Constants.ACTION_PACKAGE_PREFIX)) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }

    static String formatException(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isFailure()) {
            return status.getThrowable()
                    .map(f -> f.getClass().getName() + ": " + sanitizeMessage(f.getMessage()))
                    .or(() -> status.getMessage())
                    .orElse(null);
        }
        return null;
    }

    static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ');
        boolean changed;
        do {
            String collapsed = sanitized.replace("  ", " ");
            changed = collapsed.length() != sanitized.length();
            sanitized = collapsed;
        } while (changed);
        return sanitized.trim();
    }

    static String escapeJson(String value) {
        if (value == null) {
            return null;
        }
        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
