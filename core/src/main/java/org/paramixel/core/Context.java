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
import java.util.concurrent.CompletableFuture;
import org.paramixel.core.exception.AncestorNotFoundException;

/**
 * Provides runtime services and scoped state to a running {@link Action}.
 *
 * <p>A {@link Context} exposes the active configuration, listener, scheduler, and a local
 * {@link Store}. Contexts may be arranged into a parent-child chain so nested actions can create
 * child scopes while still navigating to ancestor stores explicitly.
 *
 * <p>Use {@link #createChild()} when an action needs an isolated store while preserving the
 *     same configuration, listener, and scheduler.
 */
public interface Context {

    /**
     * Returns the effective configuration for the current run.
     *
     * @return the immutable configuration properties visible to this context
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the direct parent context.
     *
     * <p>Use this method when the parent is expected to exist. Use {@link #findParent()} when the
     * parent may not exist.
     *
     * @return the direct parent context
     * @throws AncestorNotFoundException when this context is the root context
     */
    Context getParent();

    /**
     * Returns the local store for this context.
     *
     * <p>The returned store is scoped to this context only. Descendants may access ancestor stores
     * by navigating the context hierarchy with {@link #getParent()}, {@link #getAncestor(String)},
     * {@link #findParent()}, or {@link #findAncestor(String)}.
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
     * Schedules an action asynchronously through Paramixel's scheduler.
     *
     * @param action the action to schedule
     * @return a future completed with the action result
     *
     * <p>The returned future completes when the action finishes running. Exceptions during the run are captured in the result status rather than propagated through the future.
     */
    CompletableFuture<Result> runAsync(Action action);

    /**
     * Returns the direct parent context wrapped in an {@link Optional}.
     *
     * <p>Equivalent to {@code findAncestor("../")}.
     *
     * @return the direct parent context, or an empty {@link Optional} when this context is the root
     */
    Optional<Context> findParent();

    /**
     * Returns an ancestor context by navigating upward using a path.
     *
     * <p>Use this method when the ancestor is expected to exist. Use {@link #findAncestor(String)}
     * when the ancestor may not exist.
     *
     * <p>Path semantics:
     * <ul>
     *   <li>{@code "../"} — parent context (one level up)</li>
     *   <li>{@code "../../"} — grandparent context (two levels up)</li>
     *   <li>{@code "/"} — root context</li>
     * </ul>
     *
     * <p>Both trailing-slash and non-trailing-slash forms are accepted (e.g., {@code ".."} and
     * {@code "../"} are equivalent). Named segments (e.g., {@code "../foo"}, {@code "/bar"}) and
     * self-reference segments (e.g., {@code "."}, {@code "./"}) are not supported because action
     * names are not unique per node.
     *
     * @param path the navigation path; {@code "../"} for parent, {@code "../../"} for grandparent,
     *     {@code "/"} for root
     * @return the ancestor context at the requested path
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is empty, contains named segments, or
     *     contains {@code "."} segments
     * @throws AncestorNotFoundException if the path traverses beyond the root
     */
    Context getAncestor(String path);

    /**
     * Finds an ancestor context by navigating upward using a path.
     *
     * <p>Path semantics:
     * <ul>
     *   <li>{@code "../"} — parent context (one level up)</li>
     *   <li>{@code "../../"} — grandparent context (two levels up)</li>
     *   <li>{@code "/"} — root context</li>
     * </ul>
     *
     * <p>Both trailing-slash and non-trailing-slash forms are accepted (e.g., {@code ".."} and
     * {@code "../"} are equivalent). Named segments (e.g., {@code "../foo"}, {@code "/bar"}) and
     * self-reference segments (e.g., {@code "."}, {@code "./"}) are not supported because action
     * names are not unique per node.
     *
     * @param path the navigation path; {@code "../"} for parent, {@code "../../"} for grandparent,
     *     {@code "/"} for root
     * @return the ancestor context at the requested path, or an empty {@link Optional} when the
     *     path traverses beyond the root
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IllegalArgumentException if {@code path} is empty, contains named segments, or
     *     contains {@code "."} segments
     */
    Optional<Context> findAncestor(String path);

    /**
     * Creates a child context derived from this context.
     *
     * <p>The child context shares the same configuration, listener, and scheduler, but owns
     * an independent local {@link Store}.
     *
     * @return a new child context whose parent is this context
     */
    Context createChild();
}
