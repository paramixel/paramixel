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

package nonapi.org.paramixel.classgraph.io.github.classgraph;

/**
 * An unchecked exception that is thrown when an error state occurs or an unhandled exception is caught during
 * scanning.
 *
 * <p>
 * (Extends {@link IllegalArgumentException}, which extends {@link RuntimeException}, so either of the more generic
 * exceptions may be caught.)
 */
public class ClassGraphException extends IllegalArgumentException {
    /** serialVersionUID. */
    static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param message
     *            the message
     */
    ClassGraphException(final String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param message
     *            the message
     * @param cause
     *            the cause
     */
    ClassGraphException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
