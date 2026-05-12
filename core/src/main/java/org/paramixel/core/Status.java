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
import java.util.Optional;
import org.paramixel.core.internal.DefaultStatus;

/**
 * Represents the execution state of a {@link Result}.
 *
 * <p>A status may describe a staged action, a passing action, a failed action, or a skipped action. Optional message
 * and throwable data provide additional context for reporting and diagnostics.
 */
public interface Status {

    /**
     * Produces a status indicating that the action has not yet been executed.
     *
     * @return a staged status
     */
    static Status staged() {
        return DefaultStatus.STAGED;
    }

    /**
     * Produces a status indicating that the action completed successfully.
     *
     * @return a passing status
     */
    static Status pass() {
        return DefaultStatus.PASS;
    }

    /**
     * Produces a status indicating that the action was intentionally skipped.
     *
     * @return a skipped status
     */
    static Status skip() {
        return DefaultStatus.SKIP;
    }

    /**
     * Produces a status indicating that the action was skipped for the supplied reason.
     *
     * @param message the skip message
     * @return a skipped status
     */
    static Status skip(String message) {
        return new DefaultStatus(DefaultStatus.Kind.SKIP, Objects.requireNonNull(message, "message must not be null"));
    }

    /**
     * Produces a status indicating that the action failed with a default message.
     *
     * @return a failure status
     */
    static Status failure() {
        return DefaultStatus.FAILURE;
    }

    /**
     * Produces a status indicating that the action failed with the supplied throwable.
     *
     * @param throwable the failure throwable
     * @return a failure status
     */
    static Status failure(Throwable throwable) {
        return new DefaultStatus(
                DefaultStatus.Kind.FAILURE, Objects.requireNonNull(throwable, "throwable must not be null"));
    }

    /**
     * Produces a status indicating that the action failed with the supplied message.
     *
     * @param message the failure message
     * @return a failure status
     */
    static Status failure(String message) {
        return new DefaultStatus(
                DefaultStatus.Kind.FAILURE, Objects.requireNonNull(message, "message must not be null"));
    }

    /**
     * Returns whether the associated action has been staged but not yet completed.
     *
     * @return {@code true} if the action is staged; otherwise {@code false}
     */
    boolean isStaged();

    /**
     * Returns whether the associated action completed successfully.
     *
     * @return {@code true} if the action passed; otherwise {@code false}
     */
    boolean isPass();

    /**
     * Returns whether the associated action completed with a failure.
     *
     * @return {@code true} if the action failed; otherwise {@code false}
     */
    boolean isFailure();

    /**
     * Returns whether the associated action was skipped.
     *
     * @return {@code true} if the action was skipped; otherwise {@code false}
     */
    boolean isSkip();

    /**
     * Returns a short display name suitable for human-readable output.
     *
     * @return the status display name
     */
    String getDisplayName();

    /**
     * Returns the descriptive message for this status, if one is available.
     *
     * @return the status message, or an empty {@link Optional} when no message is available
     */
    Optional<String> getMessage();

    /**
     * Returns the throwable associated with this status, if one is available.
     *
     * @return the recorded throwable, or an empty {@link Optional} when no throwable is associated with the status
     */
    Optional<Throwable> getThrowable();
}
