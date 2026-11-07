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

import java.util.Objects;
import nonapi.org.paramixel.support.Arguments;

/**
 * Validates action display names.
 *
 * <p>Every action and action builder enforces the same contract: the display name must be
 * non-{@code null} and non-blank. Keeping the two checks here gives that contract one
 * implementation, one pair of messages, and one place to change.
 */
final class ActionNames {

    private ActionNames() {
        // Intentionally empty
    }

    /**
     * Returns the supplied display name after validating it.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return the validated display name
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    static String requireDisplayName(final String displayName) {
        Objects.requireNonNull(displayName, "displayName is null");
        return Arguments.requireNonBlank(displayName, "displayName is blank");
    }
}
