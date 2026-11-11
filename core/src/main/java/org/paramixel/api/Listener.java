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

import java.util.Optional;

/**
 * Receives run-level notifications and execution events.
 *
 * <p>Delivery model:
 *
 * <ul>
 *   <li>Events are produced by the scheduler and delivered by a dedicated dispatcher thread, so listener code never
 *       runs while the scheduler holds a permit, plan, or lock.</li>
 *   <li>The dispatcher queue is bounded. When it is full, the producer applies backpressure rather than dropping an
 *       event, which slows admission instead of corrupting the event stream.</li>
 *   <li>An exception thrown by a listener is isolated and recorded as a run diagnostic. It never changes a task's
 *       semantic pass/fail status.</li>
 *   <li>{@link #onRunCompleted(Result)} is delivered after all task events were drained, so reporting cannot race with
 *       finalization.</li>
 *   <li>A listener must not re-enter the running {@link Runner}; doing so is rejected.</li>
 * </ul>
 *
 * <p>Implementations must be thread-safe: the dispatcher delivers events on one thread, but a listener may be shared
 * with another run only when the implementation permits it.
 */
public interface Listener {

    /** A listener that ignores every callback. */
    Listener NOOP = new Listener() {};

    /**
     * Returns the default listener chain: status and summary console output, plus a report file writer when
     * {@link Configuration#REPORT_FILE} is set.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the listener chain; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    static Listener defaultListener(final Configuration configuration) {
        return nonapi.org.paramixel.reporting.DefaultListeners.create(configuration);
    }

    /**
     * Invoked once when a run starts, before discovery and execution.
     *
     * @param configuration the effective configuration; never {@code null}
     */
    default void onRunStarted(final Configuration configuration) {
        // Intentionally empty
    }

    /**
     * Invoked once before factory discovery begins.
     */
    default void onDiscoveryStarted() {
        // Intentionally empty
    }

    /**
     * Invoked after factory discovery produced the definition that will run.
     *
     * @param root the discovered root definition, or an empty optional when nothing matched
     */
    default void onDiscoveryCompleted(final Optional<Task> root) {
        // Intentionally empty
    }

    /**
     * Invoked once for a condition that does not fail the run but changes behavior, such as an unknown configuration
     * key or an ignored selector setting.
     *
     * @param message the warning; never {@code null}
     */
    default void onWarning(final String message) {
        // Intentionally empty
    }

    /**
     * Invoked for each lifecycle event of each occurrence.
     *
     * <p>Exactly one {@link TaskPhase#TERMINAL} event is delivered per occurrence.
     *
     * @param event the event; never {@code null}
     */
    default void onTaskEvent(final TaskEvent event) {
        // Intentionally empty
    }

    /**
     * Invoked once after the run completed and all task events were delivered.
     *
     * @param result the immutable result; never {@code null}
     */
    default void onRunCompleted(final Result result) {
        // Intentionally empty
    }
}
