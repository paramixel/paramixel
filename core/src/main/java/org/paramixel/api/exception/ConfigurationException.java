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

package org.paramixel.api.exception;

import java.util.Objects;
import org.paramixel.api.internal.support.Arguments;

/**
 * Signals that Paramixel configuration is invalid, inconsistent, or cannot be loaded.
 *
 * <p>Thrown during framework initialization when a configuration property has an illegal value,
 * a required configuration resource cannot be read, or conflicting settings are detected.
 * This exception halts startup — callers must correct the configuration and restart.
 */
public final class ConfigurationException extends RuntimeException {

    /**
     * Creates a configuration exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public ConfigurationException(final String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        Arguments.requireNonBlank(message, "message must not be blank");
    }

    /**
     * Creates a configuration exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the cause of this exception
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public ConfigurationException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        Arguments.requireNonBlank(message, "message must not be blank");
    }
}
