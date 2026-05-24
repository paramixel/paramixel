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
 * Signals that the action hierarchy contains a cyclic parent-child relationship.
 *
 * <p>Thrown during pre-execution validation when the framework detects that following
 * parent references from an action leads back to that same action, forming a cycle.
 * Cycles prevent the scheduler from determining a valid execution order, so this exception
 * halts the test run before any actions execute.
 */
public final class CycleDetectedException extends RuntimeException {

    /**
     * Creates a cycle-detected exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public CycleDetectedException(final String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        Arguments.requireNonBlank(message, "message must not be blank");
    }
}
