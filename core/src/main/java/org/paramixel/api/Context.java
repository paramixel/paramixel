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

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only runtime services for an active action execution.
 *
 * <p>Actions use this context to read the configuration, the current {@link Descriptor}, and the
 * fixture instances provided by enclosing {@link org.paramixel.api.action.Instance} actions.
 * Descriptor traversal, scheduling, status transitions, and listener notifications are owned by the
 * runner and execution engine.
 *
 * <p>This interface is <strong>not for implementation by users</strong>: the framework supplies the
 * only implementation. Fixtures are provided by an {@code Instance} in the tree and are never bound
 * dynamically, so the compile step can validate every typed step before any user code runs.
 */
public interface Context {

    /**
     * Returns the configuration for the current run.
     *
     * @return the run configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Returns the descriptor of the action currently executing.
     *
     * @return the current descriptor; never {@code null}
     */
    Descriptor descriptor();

    /**
     * Returns the current fixture instance for the requested type.
     *
     * @param <T> the requested instance type
     * @param type the requested instance class; must not be {@code null}
     * @return the instance, or empty when no compatible instance is available
     */
    <T> Optional<T> instance(Class<T> type);

    /**
     * Returns the current fixture instance for the requested type, throwing if none is available.
     *
     * @param <T> the requested instance type
     * @param type the requested instance class; must not be {@code null}
     * @return the instance
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalStateException if no compatible instance is available
     */
    default <T> T requireInstance(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");

        return instance(type)
                .orElseThrow(() -> new IllegalStateException("No instance is available for " + type.getName()));
    }
}
