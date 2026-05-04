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

import java.util.Optional;

/**
 * Represents the execution state of a {@link Result}.
 *
 * <p>A status may describe a staged action, a passing action, a failed action, or a skipped action. Optional message
 * and throwable data provide additional context for reporting and diagnostics.
 */
public interface Status {

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
