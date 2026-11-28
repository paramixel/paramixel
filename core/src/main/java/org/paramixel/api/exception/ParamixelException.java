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

/**
 * The root of Paramixel's framework exception hierarchy.
 *
 * <p>Application failures are captured in results rather than rethrown; this type marks failures raised by the
 * framework itself: invalid configuration, invalid definitions or plans, discovery problems, and unavailable
 * resources. Framework exceptions are distinguishable from ordinary test failures, which keeps infrastructure defects
 * from masquerading as assertion failures.
 */
public class ParamixelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the supplied message.
     *
     * @param message the description; must not be {@code null}
     */
    public ParamixelException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with the supplied message and cause.
     *
     * @param message the description; must not be {@code null}
     * @param cause the cause, or {@code null}
     */
    public ParamixelException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
