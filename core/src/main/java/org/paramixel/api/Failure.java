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

package org.paramixel.api;

import java.util.Objects;
import java.util.Optional;

/**
 * One recorded failure of an execution occurrence or phase.
 *
 * <p>A task can record several failures: an operation failure remains primary, and later finalization failures are
 * retained as supplementary failures. The original stack trace is never replaced.
 */
public final class Failure {

    private final String phase;
    private final String message;
    private final Throwable throwable;
    private final boolean primary;

    private Failure(final String phase, final String message, final Throwable throwable, final boolean primary) {
        this.phase = phase;
        this.message = message;
        this.throwable = throwable;
        this.primary = primary;
    }

    /**
     * Creates a failure.
     *
     * @param phase the phase that failed, for example {@code BODY} or {@code AFTER}; must not be {@code null}
     * @param message the description; must not be {@code null}
     * @param throwable the cause, or {@code null} when no throwable is available
     * @param primary whether this is the primary failure of the occurrence
     * @return the failure; never {@code null}
     * @throws NullPointerException if {@code phase} or {@code message} is {@code null}
     */
    public static Failure of(
            final String phase, final String message, final Throwable throwable, final boolean primary) {
        return new Failure(
                Objects.requireNonNull(phase, "phase is null"),
                Objects.requireNonNull(message, "message is null"),
                throwable,
                primary);
    }

    /**
     * Returns the phase name that failed.
     *
     * @return the phase name; never {@code null}
     */
    public String phase() {
        return phase;
    }

    /**
     * Returns the human-readable description.
     *
     * @return the description; never {@code null}
     */
    public String message() {
        return message;
    }

    /**
     * Returns the cause, when one is available.
     *
     * @return the cause, or an empty optional
     */
    public Optional<Throwable> throwable() {
        return Optional.ofNullable(throwable);
    }

    /**
     * Returns whether this is the primary failure.
     *
     * @return {@code true} for the first failure of the occurrence
     */
    public boolean isPrimary() {
        return primary;
    }

    @Override
    public String toString() {
        return phase + ": " + message + (primary ? " (primary)" : "");
    }
}
