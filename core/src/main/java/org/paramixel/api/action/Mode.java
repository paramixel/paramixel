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

package org.paramixel.api.action;

import org.paramixel.api.Status;

/**
 * Determines how an action is executed.
 *
 * <p>The mode controls whether the action's logic runs, or whether a skipped or aborted
 * outcome is produced without executing the action body. Modes are per-execution and are
 * not properties of reusable {@link Action} definitions.
 *
 * @see Metadata#mode()
 */
public enum Mode {

    /**
     * Execute the action's logic.
     */
    RUN,

    /**
     * Produce a skipped outcome without executing the action's logic.
     */
    SKIP,

    /**
     * Produce an aborted outcome without executing the action's logic.
     */
    ABORT;

    /**
     * Returns the terminal status for this mode.
     *
     * @return the corresponding terminal status
     */
    public Status toStatus() {
        return switch (this) {
            case RUN -> Status.PASSED;
            case SKIP -> Status.SKIPPED;
            case ABORT -> Status.ABORTED;
        };
    }

    /**
     * Returns the mode to propagate based on a terminal status.
     *
     * @param status the terminal status; must not be {@code null}
     * @return {@link #ABORT} for aborted status, {@link #SKIP} otherwise
     */
    public static Mode fromStatus(final Status status) {
        return status.isAborted() ? ABORT : SKIP;
    }
}
