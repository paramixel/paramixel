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

package org.paramixel.core.exception;

import java.util.Objects;
import org.paramixel.core.support.Arguments;

/**
 * Indicates that Paramixel configuration is invalid or cannot be processed.
 *
 * <p>This exception is used for invalid property values, unreadable configuration resources, and similar
 * configuration-related failures.
 */
public class ConfigurationException extends RuntimeException {

    private ConfigurationException(final String message) {
        super(message);
    }

    private ConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a configuration exception with the supplied message.
     *
     * @param message the exception message
     * @return a new configuration exception
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static ConfigurationException of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new ConfigurationException(message);
    }

    /**
     * Creates a configuration exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the underlying cause
     * @return a new configuration exception
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static ConfigurationException of(String message, Throwable cause) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        Objects.requireNonNull(cause, "cause must not be null");
        return new ConfigurationException(message, cause);
    }
}
