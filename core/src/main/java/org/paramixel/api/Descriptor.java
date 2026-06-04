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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.paramixel.api.action.Action;

/**
 * Describes one bound occurrence of an action in a run descriptor tree.
 *
 * <p>Descriptors are read-only through this public interface. The framework creates and
 * mutates concrete descriptors during discovery and execution. Reusing the same {@link Action}
 * instance in multiple locations creates distinct descriptors with independent execution state.
 *
 * <p>Structurally mirrors {@link Action}: {@link #before()} and {@link #after()} are
 * separate structural slots, not included in {@link #children()}. The descriptor provides
 * access to the bound {@link #action()}, tree navigation ({@link #parent()}, {@link #before()},
 * {@link #children()}, {@link #after()}), and live execution-occurrence state.
 */
public interface Descriptor {

    /**
     * Returns this descriptor's parent in the descriptor tree.
     *
     * @return the parent descriptor, or empty for the root descriptor
     */
    Optional<Descriptor> parent();

    /**
     * Returns the generated execution-occurrence identifier, unique across the current run.
     *
     * @return the execution-occurrence identifier; never {@code null} or blank
     */
    String id();

    /**
     * Returns the action bound to this descriptor occurrence.
     *
     * @return the action; never {@code null}
     */
    Action action();

    /**
     * Returns whether this descriptor execution completed successfully.
     *
     * @return {@code true} when this descriptor passed
     */
    boolean isPassed();

    /**
     * Returns whether this descriptor execution completed with a failure.
     *
     * @return {@code true} when this descriptor failed
     */
    boolean isFailed();

    /**
     * Returns whether this descriptor execution was skipped.
     *
     * @return {@code true} when this descriptor was skipped
     */
    boolean isSkipped();

    /**
     * Returns whether this descriptor execution was aborted.
     *
     * @return {@code true} when this descriptor was aborted
     */
    boolean isAborted();

    /**
     * Returns the instant when this descriptor started running.
     *
     * @return the start instant, or empty when this descriptor has not started
     */
    Optional<Instant> startedAt();

    /**
     * Returns the instant when this descriptor reached a terminal outcome.
     *
     * @return the completed instant, or empty when this descriptor has not completed
     */
    Optional<Instant> completedAt();

    /**
     * Returns the optional outcome message associated with this descriptor's current status.
     *
     * @return the current status message, or empty when the current status has no message
     */
    Optional<String> message();

    /**
     * Returns the optional throwable associated with this descriptor's current status.
     *
     * @return the current status throwable, or empty when the current status has no throwable
     */
    Optional<Throwable> throwable();

    /**
     * Returns whether this descriptor has reached a terminal outcome.
     *
     * @return {@code true} when the descriptor outcome is terminal
     */
    boolean isCompleted();

    /**
     * Returns the before-child descriptor, if this descriptor's action declares one.
     *
     * <p>The before-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Scope},
     * {@link org.paramixel.api.action.Static}, and {@link org.paramixel.api.action.Instance}
     * override this method to return their before-child. Terminal and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the before-child descriptor, or empty if none
     */
    default Optional<Descriptor> before() {
        return Optional.empty();
    }

    /**
     * Returns this descriptor's direct body children in discovery order.
     *
     * <p>Body children do not include the {@link #before()} or {@link #after()}
     * descriptors — those are separate structural slots.
     *
     * @return immutable ordered body child descriptors; never {@code null} and contains no {@code null} elements
     */
    List<Descriptor> children();

    /**
     * Returns the after-child descriptor, if this descriptor's action declares one.
     *
     * <p>The after-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Scope},
     * {@link org.paramixel.api.action.Static}, and {@link org.paramixel.api.action.Instance}
     * override this method to return their after-child. Terminal and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the after-child descriptor, or empty if none
     */
    default Optional<Descriptor> after() {
        return Optional.empty();
    }
}
