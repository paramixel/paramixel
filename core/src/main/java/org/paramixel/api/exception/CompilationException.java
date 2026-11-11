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
 * Thrown when a run's definition, configuration, or resource declaration fails validation before execution.
 *
 * <p>Compilation is side-effect free and completes before any factory, lifecycle callback, or test body runs, so a
 * {@code CompilationException} guarantees that no user code was executed.
 *
 * <p>The exception carries every independent diagnostic the compiler found, in a deterministic order, rather than only
 * the first one.
 */
public class CompilationException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    private final transient java.util.List<String> diagnostics;

    /**
     * Creates an exception with one message.
     *
     * @param message the description; must not be {@code null}
     */
    public CompilationException(final String message) {
        this(message, java.util.List.of(), null);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the description; must not be {@code null}
     * @param cause the cause, or {@code null}
     */
    public CompilationException(final String message, final Throwable cause) {
        this(message, java.util.List.of(), cause);
    }

    /**
     * Creates an exception that aggregates diagnostics.
     *
     * @param message the summary description; must not be {@code null}
     * @param diagnostics the individual diagnostics; must not be {@code null}
     * @param cause the cause, or {@code null}
     */
    public CompilationException(final String message, final java.util.List<String> diagnostics, final Throwable cause) {
        super(message, cause);
        this.diagnostics = java.util.List.copyOf(diagnostics);
        for (String diagnostic : this.diagnostics) {
            addSuppressed(new ParamixelException(diagnostic));
        }
    }

    /**
     * Returns the individual diagnostics.
     *
     * @return the immutable, ordered diagnostics; never {@code null}
     */
    public java.util.List<String> diagnostics() {
        return diagnostics;
    }
}
