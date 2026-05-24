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

package examples.support;

import static java.lang.String.format;

/**
 * Lightweight stdout logger for example code. Each line is prefixed with the logger name,
 * typically the fully-qualified class name supplied at creation.
 */
public class Logger {

    private final String name;

    private Logger(final String name) {
        this.name = name;
    }

    /**
     * Logs an object to stdout using its {@link Object#toString()} representation.
     *
     * @param object the object to log
     */
    public void info(final Object object) {
        System.out.printf("%s | %s%n", name, object);
    }

    /**
     * Logs a formatted message to stdout using {@link String#format} semantics.
     *
     * @param format a non-blank format string
     * @param objects format arguments
     * @throws IllegalArgumentException if {@code format} is blank
     */
    public void info(final String format, final Object... objects) {
        if (format.trim().isEmpty()) {
            throw new IllegalArgumentException("format is blank");
        }

        info(format(format, objects));
    }

    /**
     * Creates a logger whose prefix is the fully-qualified name of the given class.
     *
     * @param clazz the class to derive the logger name from
     * @return a new logger instance
     * @throws IllegalArgumentException if {@code clazz} is null
     */
    public static Logger createLogger(final Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz is null");
        }

        return new Logger(clazz.getSimpleName());
    }
}
