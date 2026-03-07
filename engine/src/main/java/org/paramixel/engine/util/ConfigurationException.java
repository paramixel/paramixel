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
import org.jspecify.annotations.NonNull;

/**
 * Represents a configuration error that must abort execution.
 *
 * <p>Messages are standardized and always single-line to make failures easy to scan in build logs.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class ConfigurationException extends IllegalStateException {

    /**
     * Creates a new exception.
     *
     * @param key configuration key; never {@code null}
     * @param reason human-readable reason; never {@code null}
     * @param source value source identifier; never {@code null}
     * @param raw raw value; never {@code null}
     * @param normalized normalized value; never {@code null}
     */
    public ConfigurationException(
            final @NonNull String key,
            final @NonNull String reason,
            final @NonNull String source,
            final @NonNull String raw,
            final @NonNull String normalized) {
        super(message(key, reason, source, raw, normalized));
    }

    /**
     * Creates a new exception.
     *
     * @param key configuration key; never {@code null}
     * @param reason human-readable reason; never {@code null}
     * @param source value source identifier; never {@code null}
     * @param raw raw value; never {@code null}
     * @param normalized normalized value; never {@code null}
     * @param cause cause; may be {@code null}
     */
    public ConfigurationException(
            final @NonNull String key,
            final @NonNull String reason,
            final @NonNull String source,
            final @NonNull String raw,
            final @NonNull String normalized,
            final Throwable cause) {
        super(message(key, reason, source, raw, normalized), cause);
    }

    private static String message(
            final String key, final String reason, final String source, final String raw, final String normalized) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(raw, "raw must not be null");
        Objects.requireNonNull(normalized, "normalized must not be null");
        return "Invalid configuration: "
                + key
                + ": "
                + reason
                + " (source="
                + source
                + " raw='"
                + raw
                + "' normalized='"
                + normalized
                + "')";
    }
}
