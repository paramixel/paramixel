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
 * Indicates that an ancestor context does not exist.
 *
 * <p>Thrown by {@code getParent()} when the current context is the root, or by
 * {@code getAncestor(path)} when the path traverses beyond the root.
 */
public final class AncestorNotFoundException extends RuntimeException {

    private AncestorNotFoundException(final String message) {
        super(message);
    }

    /**
     * Creates an ancestor-not-found exception with the supplied message.
     *
     * @param message the exception message
     * @return a new ancestor-not-found exception
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static AncestorNotFoundException of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new AncestorNotFoundException(message);
    }
}
