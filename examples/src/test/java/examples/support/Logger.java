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

package examples.support;

import static java.lang.String.format;

import org.jspecify.annotations.NonNull;

/**
 * Lightweight console logger for example output.
 */
public class Logger {

    /**
     * Logger name prefix.
     */
    private final String name;

    /**
     * Creates a new logger instance.
     *
     * @param name logger name prefix
     */
    private Logger(final @NonNull String name) {
        this.name = name;
    }

    /**
     * Logs a message using {@code Object.toString()}.
     *
     * @param object the object to log
     */
    public void info(final @NonNull Object object) {
        System.out.printf("%s | %s%n", name, object);
    }

    /**
     * Logs a formatted message.
     *
     * @param format the {@link String#format} template
     * @param objects the format arguments
     */
    public void info(final @NonNull String format, final @NonNull Object... objects) {
        if (format.trim().isEmpty()) {
            throw new IllegalArgumentException("format is blank");
        }

        info(format(format, objects));
    }

    /**
     * Creates a logger for the given class.
     *
     * @param clazz the class whose name will be used as prefix
     * @return a new logger instance
     */
    public static Logger createLogger(final @NonNull Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz is null");
        }

        return new Logger(clazz.getName());
    }
}
