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

package org.paramixel.core;

import java.util.Objects;

/**
 * Signals an error during configuration loading.
 *
 * <p>ConfigurationException is thrown when configuration properties cannot be loaded
 * or parsed from available sources (classpath resources, system properties).</p>
 *
 * @see Configuration
 * @see ResolverException
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Creates a configuration error with a detail message.
     *
     * @param message the detail message; may be {@code null}
     */
    private ConfigurationException(final String message) {
        super(message);
    }

    /**
     * Creates a configuration error with a detail message and cause.
     *
     * @param message the detail message; may be {@code null}
     * @param cause the cause; may be {@code null}
     */
    private ConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a configuration error with a detail message.
     *
     * @param message the detail message; must not be {@code null}
     * @return a new {@code ConfigurationException}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static ConfigurationException of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        return new ConfigurationException(message);
    }

    /**
     * Creates a configuration error with a detail message and cause.
     *
     * @param message the detail message; must not be {@code null}
     * @param cause the cause; must not be {@code null}
     * @return a new {@code ConfigurationException}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     */
    public static ConfigurationException of(String message, Throwable cause) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        return new ConfigurationException(message, cause);
    }
}
