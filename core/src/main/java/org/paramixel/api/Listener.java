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
import nonapi.org.paramixel.reporting.DefaultListeners;

/**
 * Receives run-level notifications and execution events.
 *
 * <p>Delivery model:
 *
 * <ul>
 *   <li>Events are produced by the scheduler and delivered by a dedicated dispatcher thread, so listener code never
 *       runs while the scheduler holds a permit, plan, or lock.</li>
 *   <li>A full listener queue pauses new work rather than dropping events. Timeouts, cancellation, and cleanup still
 *       advance; their events and events from in-flight work use a bounded reserve sized to the plan.</li>
 *   <li>An exception thrown by a listener is isolated and recorded as a run diagnostic. It never changes a task's
 *       semantic pass/fail status. Unrecoverable VM errors are propagated to the runner.</li>
 *   <li>{@link #onRunCompleted(Result)} runs on the dispatcher after all events were delivered. If delivery stops
 *       making progress under backpressure or during final delivery for
 *       {@link Configuration#LISTENER_DRAIN_TIMEOUT_MILLIS}, the dispatcher is interrupted and undelivered events
 *       are discarded. The result records a {@value #DRAIN_TIMEOUT_DIAGNOSTIC} infrastructure failure with a nonzero
 *       exit code, independently of its task outcomes. If event delivery stalls, completion is not invoked.</li>
 *   <li>The {@link Result} passed to {@link #onRunCompleted(Result)} already carries the run's diagnostics, including
 *       infrastructure diagnostics such as {@code NON_QUIESCENT} and listener failures recorded during delivery.</li>
 *   <li>A listener must not re-enter the running {@link Runner}; doing so is rejected.</li>
 * </ul>
 *
 * <p>Implementations must be thread-safe: the dispatcher delivers events on one thread, but a listener may be shared
 * with another run only when the implementation permits it.
 */
public interface Listener {

    /** A listener that ignores every callback. */
    Listener NOOP = new Listener() {};

    /** Run failure phase and diagnostic code for stalled listener delivery. */
    String DRAIN_TIMEOUT_DIAGNOSTIC = "LISTENER_DRAIN_TIMEOUT";

    /**
     * Returns the default listener chain: status and summary console output, plus a report file writer when
     * {@link Configuration#REPORT_FILE} is set.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the listener chain; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    static Listener defaultListener(final Configuration configuration) {
        return DefaultListeners.create(configuration);
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
     * @param root the discovered root definition, or an empty optional when no factory contributes a task
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
     * <p>On a complete event stream, exactly one {@link TaskPhase#TERMINAL} event is delivered per occurrence.
     *
     * @param event the event; never {@code null}
     */
    default void onTaskEvent(final TaskEvent event) {
        // Intentionally empty
    }

    /**
     * Invoked once after the run completed and all task events were delivered. Not invoked for an incomplete stream.
     *
     * @param result the immutable result, including the run's diagnostics; never {@code null}
     */
    default void onRunCompleted(final Result result) {
        // Intentionally empty
    }
}
