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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.paramixel.api.action.Action;

/**
 * Describes one bound occurrence of an action in a run descriptor tree.
 *
 * <p>Descriptors are read-only through this public interface, and are not for implementation by
 * users: the framework creates and mutates concrete descriptors during discovery and execution.
 * Reusing the same {@link Action} instance in multiple locations creates distinct descriptors with
 * independent execution state.
 *
 * <p>Structurally mirrors {@link Action}: {@link #before()} and {@link #after()} are
 * separate structural slots, not included in {@link #children()}. The descriptor provides
 * access to the bound {@link #action()}, tree navigation ({@link #parent()}, {@link #before()},
 * {@link #children()}, {@link #after()}), and live execution-occurrence state.
 *
 * <p>The current outcome, including its message and throwable, is {@link #status()}.
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
     * Returns the display name of the bound action.
     *
     * <p>Shorthand for {@code action().displayName()}.
     *
     * @return the display name; never {@code null}
     */
    default String displayName() {
        return action().displayName();
    }

    /**
     * Returns the human-readable path from the run root to this descriptor.
     *
     * <p>Components are the display names of the descriptor and its ancestors, excluding the run
     * root, joined with {@code " / "}. Listener and report authors use this instead of rebuilding
     * the path themselves.
     *
     * @return the display-name path; empty for the run root
     */
    String path();

    /**
     * Returns this descriptor's current status.
     *
     * <p>The status carries the outcome kind, message and throwable; compare it with
     * {@link Status#kind()}.
     *
     * @return the status; never {@code null}
     */
    Status status();

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
     * Returns how long this descriptor executed.
     *
     * @return the duration between {@link #startedAt()} and {@link #completedAt()}, or empty when this
     *     descriptor has not both started and completed
     */
    default Optional<Duration> duration() {
        var start = startedAt();
        var end = completedAt();
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(start.orElseThrow(), end.orElseThrow()));
    }

    /**
     * Returns whether this descriptor has reached a terminal outcome.
     *
     * @return {@code true} when the descriptor outcome is terminal
     */
    default boolean isCompleted() {
        return status().kind().isTerminal();
    }

    /**
     * Returns the before-child descriptor, if this descriptor's action declares one.
     *
     * <p>The before-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Scope}
     * override this method to return their before-child. Terminal and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the before-child descriptor, or empty if none
     */
    default Optional<Descriptor> before() {
        return Optional.empty();
    }

    /**
     * Returns this descriptor's direct child actions in discovery order.
     *
     * <p>Body children do not include the {@link #before()} or {@link #after()}
     * descriptors — those are separate structural slots.
     *
     * @return immutable ordered child action descriptors; never {@code null} and contains no {@code null} elements
     */
    List<Descriptor> children();

    /**
     * Returns the after-child descriptor, if this descriptor's action declares one.
     *
     * <p>The after-child is a separate structural slot and is not included in
     * {@link #children()}. Composite actions such as {@link org.paramixel.api.action.Scope}
     * override this method to return their after-child. Terminal and decorator descriptors
     * inherit the default, which returns an empty optional.
     *
     * @return the after-child descriptor, or empty if none
     */
    default Optional<Descriptor> after() {
        return Optional.empty();
    }
}
