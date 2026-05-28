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

import java.time.Duration;
import java.util.Optional;
import org.paramixel.api.Status;

/**
 * Describes the identity and execution-occurrence state of an action.
 *
 * <p>Metadata records a generated execution-occurrence identifier, a human-readable
 * name, the action kind, the runtime action class name, and the live execution
 * state (status, mode, run duration, message, and throwable). A separate object, rather
 * than the action itself, keeps the metadata available before the action runs and without
 * holding a reference to the action instance.
 *
 * <p>The identifier is generated per execution occurrence and is unique across the current
 * run. Running the same {@link Action} instance twice produces different metadata identifiers.
 * The {@code @Paramixel.Id} annotation and {@code AnnotationResolver.byId(...)} remain
 * separate lookup/discovery concepts and do not imply execution identifiers.
 *
 * <p>Implementations are thread-safe. Mutable state fields ({{@link #status()}},
 * {{@link #mode()}}, {{@link #runDuration()}}) are protected by synchronization in
 * {@code ConcreteMetadata}; identity fields are immutable.
 */
public interface Metadata {

    /**
     * Returns the generated execution-occurrence identifier, unique across the current run.
     *
     * @return the execution-occurrence identifier; never {@code null} or blank
     */
    String id();

    /**
     * Returns the human-readable action name used in console output and reports.
     *
     * @return the action display name; never {@code null}
     */
    String name();

    /**
     * Returns the action kind used in console output and reports.
     *
     * @return the action kind; never {@code null} or blank
     */
    String kind();

    /**
     * Returns the runtime action class name.
     *
     * @return the fully qualified class name of the action; never {@code null}
     */
    String className();

    /**
     * Returns the current execution status of this descriptor occurrence.
     *
     * @return the execution status; never {@code null}
     */
    Status status();

    /**
     * Returns the mode requested for this descriptor execution.
     *
     * @return the execution mode; never {@code null}
     */
    Mode mode();

    /**
     * Returns the local run duration measured for this descriptor.
     *
     * @return the run duration; never {@code null}
     */
    Duration runDuration();

    /**
     * Returns the optional status message.
     *
     * <p>Delegates to {{@link Status#message()}} on the current status.
     *
     * @return the message, or empty when no message is available
     */
    Optional<String> message();

    /**
     * Returns the optional throwable recorded for this descriptor.
     *
     * <p>Delegates to {@link Status#throwable()} on the current status.
     *
     * @return the throwable, or empty when no throwable is available
     */
    Optional<Throwable> throwable();

    /**
     * Returns whether this descriptor has reached a terminal status.
     *
     * @return {@code true} when the descriptor status is terminal
     */
    boolean isCompleted();
}
