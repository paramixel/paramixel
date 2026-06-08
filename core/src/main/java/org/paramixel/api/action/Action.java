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

import org.paramixel.api.Context;

/**
 * Defines a reusable execution unit processed by the Paramixel scheduler.
 *
 * <p>Actions are reusable definitions. Discovery binds an action occurrence to a descriptor,
 * and execution uses a {@link Context} for action-facing runtime services such as
 * configuration and fixture instances.
 *
 * <p>The scheduler owns all traversal, status aggregation, and listener callbacks.
 *
 * <p>For actions with mutable configuration, use the corresponding {@link Builder} to
 * construct an immutable action snapshot via {@link Builder#build()}.
 */
public sealed interface Action
        permits Assert,
                Conditional,
                Delay,
                Instance,
                Isolated,
                Parallel,
                Repeat,
                Scope,
                Sequence,
                Static,
                Step,
                Timeout {

    /**
     * Returns the display name used in console output and reports.
     *
     * @return the action display name
     */
    String displayName();
}
