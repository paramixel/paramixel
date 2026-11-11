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

package nonapi.org.paramixel.execution;

import org.paramixel.api.TaskEvent;

/**
 * Receives execution events and diagnostics from the engine.
 *
 * <p>Implementations must be non-blocking with respect to scheduler state: the engine calls these methods on its own
 * thread while it owns the scheduling decision, so an implementation that dispatches to a separate thread (with a
 * bounded queue and backpressure) is expected.
 */
public interface EventPublisher {

    /**
     * Publishes one lifecycle event.
     *
     * @param event the event; never {@code null}
     */
    void publish(TaskEvent event);

    /**
     * Publishes a warning that does not fail the run.
     *
     * @param message the warning; never {@code null}
     */
    void warning(String message);

    /**
     * Publishes a run-level infrastructure diagnostic.
     *
     * @param message the diagnostic; never {@code null}
     */
    void diagnostic(String message);
}
