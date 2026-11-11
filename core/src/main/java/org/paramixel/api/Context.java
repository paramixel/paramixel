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
 * Read-only execution metadata and explicitly keyed resources for one occurrence.
 *
 * <p>A context belongs to one execution occurrence of one run. It exposes stable identity, the effective
 * configuration, the declared primary instance of the nearest lexical {@code forInstance} binding, and any secondary
 * resources declared by the occurrence or its ancestors.
 *
 * <p>Primary fixtures are <strong>not</strong> looked up by {@link Class}; typed callbacks receive them directly, and
 * additional objects are reached through an explicit {@link Key}. A context never exposes mutable execution state and
 * must not be retained after its callback returns.
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
     * Returns the primary instance of the nearest lexical {@code forInstance} binding.
     *
     * <p>Typed callbacks receive this instance as their argument; the method exists for framework adapters and for
     * advanced code that needs the raw value. A nested {@code forInstance} task establishes its own binding and does
     * not make an outer instance implicitly available through this method.
     *
     * @return the bound primary instance; never {@code null}
     * @throws IllegalStateException if no primary instance is bound in this occurrence's lexical scope
     */
    Object primaryInstance();

    /**
     * Returns the resource bound to the supplied key in this scope or an enclosing scope.
     *
     * @param <T> the resource type
     * @param key the resource key; must not be {@code null}
     * @return the resource
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws org.paramixel.api.exception.ResourceException if the resource is not available
     */
    <T> T require(Key<T> key);

    /**
     * Returns the resource bound to the supplied key, when available.
     *
     * @param <T> the resource type
     * @param key the resource key; must not be {@code null}
     * @return the resource, or an empty optional
     * @throws NullPointerException if {@code key} is {@code null}
     */
    <T> Optional<T> find(Key<T> key);
}
