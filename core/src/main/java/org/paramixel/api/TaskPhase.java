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

/**
 * The lifecycle phases reported by Paramixel.
 *
 * <p>The order of the constants is the normal phase order of a task occurrence:
 * {@code ADMITTED → CONDITION → BEFORE → BODY → CHILDREN → AFTER → TERMINAL}.
 * A task without a phase skips it. A {@code CONDITION} phase is reported only when the task declares a condition.
 *
 * <p>A {@code BEFORE} or {@code AFTER} phase result is reported for an identified hook slot ({@code before[setUp]}). A
 * slot that holds a task subtree is reported as an occurrence under its owner instead, and that occurrence carries its
 * own phases.
 */
public enum TaskPhase {

    /** The occurrence acquired its admission requirements and started. */
    ADMITTED,

    /** A runtime condition of the task was evaluated. */
    CONDITION,

    /** A {@code before} hook ran. */
    BEFORE,

    /** The task's own body operation ran. */
    BODY,

    /** A cooperative timeout expired for the task's subtree. */
    TIMEOUT,

    /** The task's direct child group ran. */
    CHILDREN,

    /** An {@code after} hook ran. */
    AFTER,

    /** The task reached its terminal status. */
    TERMINAL
}
