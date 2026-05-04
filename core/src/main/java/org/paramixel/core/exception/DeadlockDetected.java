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
 * Indicates that Paramixel detected a potential deadlock in an action execution plan.
 *
 * <p>This exception is typically raised during validation before execution begins.
 */
public final class DeadlockDetected extends RuntimeException {

    private DeadlockDetected(String message) {
        super(message);
    }

    /**
     * Creates a deadlock-detected exception with the supplied message.
     *
     * @param message the exception message
     * @return a new deadlock-detected exception
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static DeadlockDetected of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new DeadlockDetected(message);
    }
}
