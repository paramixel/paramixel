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

import java.util.List;
import java.util.Optional;

/**
 * Execution metadata and the state store of one occurrence's scope.
 *
 * <p>A context belongs to one execution occurrence of one run. It exposes stable identity, the effective configuration,
 * and the named entries of the scope the occurrence belongs to. A scope is created at the run root, at every direct child
 * of a {@code parallel} group, and at every task carrying {@code retry} or {@code repeat}; sequential descendants share
 * the nearest one, and parallel siblings never see each other's entries.
 *
 * <p>Reads resolve from the current scope and then each enclosing scope, nearest first. Writes land in the scope of the
 * nearest scope-owning self-or-ancestor, and a write from an attempt that has been superseded is discarded.
 *
 * <p>The framework never closes a stored value and never probes for {@link AutoCloseable}: release is authored in an
 * {@code after} hook, with {@link #find(String, Class)} for whatever a partial {@code before} managed to store. A
 * context must not be retained after its callback returns.
 *
 * <p>This interface is not for implementation by users: the framework supplies the only implementations.
 */
public interface Context {

    /**
     * Returns the effective configuration of the run.
     *
     * @return the configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Returns the stable identifier of the run.
     *
     * @return the run identifier; never {@code null}
     */
    String runId();

    /**
     * Returns the stable identifier of this occurrence within the run.
     *
     * <p>The identifier is derived from the compiled structure, not from completion order, thread identity, or object
     * identity.
     *
     * @return the occurrence identifier; never {@code null}
     */
    String occurrenceId();

    /**
     * Returns the identifier of the parent occurrence.
     *
     * @return the parent identifier, or an empty optional for the root occurrence
     */
    Optional<String> parentOccurrenceId();

    /**
     * Returns the display name of this occurrence.
     *
     * @return the name; never {@code null}
     */
    String name();

    /**
     * Returns the display-name path from the run root to this occurrence.
     *
     * <p>Framework scaffolding added by factory discovery is excluded, so a discovered factory's path starts at the
     * factory name. Use {@link #occurrenceId()} for stable identity.
     *
     * @return the path; never {@code null}
     */
    String path();

    /**
     * Returns the 1-based attempt index of the current execution attempt.
     *
     * @return the attempt index; at least one
     */
    int attempt();

    /**
     * Returns the tags declared by this occurrence.
     *
     * @return the immutable tags; never {@code null}
     */
    List<String> tags();

    /**
     * Returns whether cooperative cancellation has been requested for this occurrence or an ancestor.
     *
     * <p>Cancellation is cooperative. Paramixel can request interruption and stop admitting new work, but it cannot
     * forcibly stop a callback that ignores interruption.
     *
     * @return {@code true} when cancellation was requested
     */
    boolean isCanceled();

    /**
     * Returns the cancellation reason, when cancellation was requested.
     *
     * @return the reason, or an empty optional
     */
    Optional<String> cancellationReason();

    /**
     * Stores a value in the context of the scope this occurrence belongs to.
     *
     * <p>A scope is created at the run root, at every direct child of a {@code parallel} group, and at every task
     * carrying {@code retry} or {@code repeat}. Sequential descendants share the nearest enclosing scope, so a value
     * stored by one step is visible to the next sibling and to the enclosing group's {@code after} hook; parallel
     * siblings never see each other's entries. A scope is cleared when its owner finishes.
     *
     * <p>The framework never closes a stored value: it does not test for {@link AutoCloseable} and offers no release
     * hook. Close a resource in an {@code after} hook and drop the reference with {@link #remove(String)} when the
     * scope outlives the object's usefulness; anything left when the scope ends is dropped for you.
     *
     * <p>A write from an attempt that has already been superseded — after a timeout, a cancellation, or a hook that
     * ignored interruption — is discarded, so an abandoned callback cannot contaminate the next attempt.
     *
     * @param name the entry name; must not be {@code null} or blank
     * @param value the value; must not be {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    void put(String name, Object value);

    /**
     * Removes an entry from the context of the scope this occurrence belongs to.
     *
     * <p>Removing is independent of releasing: the framework does not close the value, and removing an entry that is
     * not present is not an error.
     *
     * @param name the entry name; must not be {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    void remove(String name);

    /**
     * Returns the entry stored under {@code name} in this scope or an enclosing scope.
     *
     * @param <T> the entry type
     * @param name the entry name; must not be {@code null}
     * @param type the expected type; must not be {@code null}
     * @return the entry; never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws org.paramixel.api.exception.ContextException if the entry is absent or holds an incompatible type
     */
    <T> T require(String name, Class<T> type);

    /**
     * Returns the entry stored under {@code name}, when available.
     *
     * <p>This is the teardown form: an {@code after} hook can release whatever a partially completed {@code before}
     * hook managed to store, without masking the original failure.
     *
     * @param <T> the entry type
     * @param name the entry name; must not be {@code null}
     * @param type the expected type; must not be {@code null}
     * @return the entry, or an empty optional when it is absent or holds an incompatible type
     * @throws NullPointerException if an argument is {@code null}
     */
    <T> Optional<T> find(String name, Class<T> type);
}
