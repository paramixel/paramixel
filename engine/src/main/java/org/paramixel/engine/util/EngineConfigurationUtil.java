/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.util;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Provides centralized configuration normalization and parsing.
 *
 * <p>Normalization is performed as:
 * <ol>
 *   <li>Trim leading/trailing whitespace.
 *   <li>Decode Unicode escapes (backslash + 'u' + 4 hex digits) in the trimmed value.
 *   <li>Do not trim again after decoding.
 * </ol>
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EngineConfigurationUtil {

    /**
     * Source identifier for standardized messages.
     */
    public enum Source {
        JUNIT_CONFIG("junit"),
        PROPERTIES_FILE("properties"),
        PROPERTIES_IO("properties-file"),
        SYSTEM_PROPERTIES("system"),
        PROGRAMMATIC("programmatic");

        private final String id;

        Source(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private EngineConfigurationUtil() {
        // INTENTIONALLY EMPTY
    }

    public static String normalizeProvided(
            final @NonNull String key, final @NonNull String rawValue, final @NonNull Source source) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(rawValue, "rawValue must not be null");
        Objects.requireNonNull(source, "source must not be null");

        final String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            throw new ConfigurationException(key, "must not be blank", source.id(), rawValue, "");
        }

        final String normalized = decodeUnicodeEscapes(trimmed);
        return normalized;
    }

    public static int parseProvidedPositiveInt(
            final @NonNull String key,
            final @NonNull String rawValue,
            final @NonNull Source source,
            final int min,
            final int max) {
        final String normalized = normalizeProvided(key, rawValue, source);

        if (!normalized.equals(normalized.trim())) {
            throw new ConfigurationException(
                    key,
                    "must not contain leading/trailing whitespace after Unicode unescape",
                    source.id(),
                    rawValue,
                    normalized);
        }

        final int value;
        try {
            value = Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    key,
                    "must be an integer in range [" + min + ", " + max + "]",
                    source.id(),
                    rawValue,
                    normalized,
                    e);
        }

        if (value < min || value > max) {
            throw new ConfigurationException(
                    key, "must be an integer in range [" + min + ", " + max + "]", source.id(), rawValue, normalized);
        }

        return value;
    }

    public static String requireAllowedValue(
            final @NonNull String key,
            final @NonNull String rawValue,
            final @NonNull Source source,
            final @NonNull Set<String> allowed) {
        Objects.requireNonNull(allowed, "allowed must not be null");

        final String normalized = normalizeProvided(key, rawValue, source);
        if (!allowed.contains(normalized)) {
            final String allowedString = allowed.stream().sorted().toList().toString();
            throw new ConfigurationException(key, "must be one of " + allowedString, source.id(), rawValue, normalized);
        }
        return normalized;
    }

    private static String decodeUnicodeEscapes(final String value) {
        final int length = value.length();
        final StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            if (c == '\\' && i + 5 < length && value.charAt(i + 1) == 'u' && !isEscaped(value, i)) {
                final int codePoint = parseHex4(value, i + 2);
                if (codePoint >= 0) {
                    sb.append((char) codePoint);
                    i += 5;
                    continue;
                }
            }
            sb.append(c);
        }

        return sb.toString();
    }

    private static boolean isEscaped(final String value, final int index) {
        int count = 0;
        for (int i = index - 1; i >= 0 && value.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    private static int parseHex4(final String value, final int start) {
        int code = 0;
        for (int i = 0; i < 4; i++) {
            final char ch = value.charAt(start + i);
            final int digit = Character.digit(ch, 16);
            if (digit < 0) {
                return -1;
            }
            code = (code << 4) + digit;
        }
        return code;
    }
}
