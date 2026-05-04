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

package org.paramixel.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Provides runtime services and scoped state to an executing {@link Action}.
 *
 * <p>A {@code Context} exposes the active configuration, listener, executor service, and a local
 * {@link Store}. Contexts may be arranged into a parent-child chain so nested actions can create
 * child scopes while still navigating to ancestor stores explicitly.
 *
 * @apiNote Use {@link #createChild()} when an action needs an isolated store while preserving the
 *     same configuration, listener, and executor service.
 */
public interface Context {

    /**
     * Returns the effective configuration for the current run.
     *
     * @return the configuration properties visible to this context
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the direct parent context.
     *
     * @return the direct parent context, or an empty {@link Optional} when this context is the
     *     root context
     */
    Optional<Context> getParent();

    /**
     * Returns the local store for this context.
     *
     * <p>The returned store is scoped to this context only. Descendants may access ancestor stores
     * by first navigating the context hierarchy with {@link #getParent()} or
     * {@link #findAncestor(int)}.
     *
     * @return the local store for this context
     */
    Store getStore();

    /**
     * Returns the listener receiving run lifecycle callbacks.
     *
     * @return the active listener
     */
    Listener getListener();

    /**
     * Returns the executor service available to actions for asynchronous work.
     *
     * @return the executor service associated with the current run
     */
    ExecutorService getExecutorService();

    /**
     * Finds the ancestor context reached by walking upward from this context.
     *
     * <p>A {@code levelUp} of {@code 0} returns this context, {@code 1} returns the direct parent,
     * and larger values continue upward through the ancestry chain.
     *
     * @param levelUp the number of parent hops to traverse
     * @return the ancestor context at the requested level, or an empty {@link Optional} when no
     *     ancestor exists at that level
     * @throws IllegalArgumentException if {@code levelUp} is negative
     */
    Optional<Context> findAncestor(int levelUp);

    /**
     * Creates a child context derived from this context.
     *
     * <p>The child context shares the same configuration, listener, and executor service, but owns
     * an independent local {@link Store}.
     *
     * @return a new child context whose parent is this context
     */
    Context createChild();
}
