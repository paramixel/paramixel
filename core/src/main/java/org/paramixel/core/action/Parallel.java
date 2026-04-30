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

package org.paramixel.core.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that executes child actions concurrently.
 *
 * <p>Child actions are submitted to an {@link ExecutorService} and their concurrent
 * execution is bounded by a {@link Semaphore}. When {@code parallelism} is set, at most
 * that many children execute concurrently; when no parallelism limit is set, all children
 * execute concurrently.</p>
 *
 * <p><strong>Nesting:</strong> When a {@code Parallel} action is nested inside another
 * {@code Parallel} action and both share the same {@link ExecutorService}, thread
 * starvation can occur if the executor does not have enough threads. To avoid this,
 * supply a dedicated {@link ExecutorService} to inner {@code Parallel} actions using
 * {@link #of(String, ExecutorService, List)} or
 * {@link #of(String, ExecutorService, Action...)}.</p>
 */
public class Parallel extends AbstractAction {

    private final int parallelism;
    private final List<Action> children;
    private final ExecutorService executorService;

    /**
     * Creates a parallel action that limits concurrency with an internal thread bound.
     *
     * @param name the action name
     * @param parallelism the maximum number of concurrently running children
     * @param children the child actions to execute
     */
    protected Parallel(String name, int parallelism, List<Action> children) {
        this(name, parallelism, children, null);
    }

    /**
     * Creates a parallel action that dispatches all work to a supplied executor service.
     *
     * @param name the action name
     * @param executorService the executor service used for dispatch
     * @param children the child actions to execute
     */
    protected Parallel(String name, ExecutorService executorService, List<Action> children) {
        super();
        this.name = validateName(name);
        this.parallelism = Integer.MAX_VALUE;
        this.children = validateChildren(children);
        this.executorService = executorService;
    }

    /**
     * Creates a parallel action with either an explicit parallelism bound or an explicit executor.
     *
     * @param name the action name
     * @param parallelism the maximum concurrent child count
     * @param children the child actions to execute
     * @param executorService the explicit executor service to use, or {@code null}
     */
    protected Parallel(String name, int parallelism, List<Action> children, ExecutorService executorService) {
        super();
        this.name = validateName(name);
        this.parallelism = parallelism;
        this.children = validateChildren(children);
        this.executorService = executorService;
    }

    /**
     * Creates a parallel action that executes all children using the default execution strategy.
     *
     * <p>Children are submitted to the executor provided by {@link Context#getExecutorService()}.
     * When nesting {@code Parallel} actions, supply a dedicated {@link ExecutorService} to
     * inner actions via {@link #of(String, ExecutorService, List)} to avoid thread starvation.</p>
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, Integer.MAX_VALUE, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a parallel action that limits concurrent child execution.
     *
     * <p>Children are submitted to the executor provided by {@link Context#getExecutorService()}.
     * When nesting {@code Parallel} actions, supply a dedicated {@link ExecutorService} to
     * inner actions via {@link #of(String, ExecutorService, List)} to avoid thread starvation.</p>
     *
     * @param name the action name; must not be {@code null}
     * @param parallelism the maximum number of children to execute concurrently; must be positive
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, int parallelism, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, parallelism, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a parallel action from varargs children using the default execution strategy.
     *
     * <p>Children are submitted to the executor provided by {@link Context#getExecutorService()}.
     * When nesting {@code Parallel} actions, supply a dedicated {@link ExecutorService} to
     * inner actions via {@link #of(String, ExecutorService, Action...)} to avoid thread starvation.</p>
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, Integer.MAX_VALUE, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Creates a parallel action from varargs children with a concurrency limit.
     *
     * <p>Children are submitted to the executor provided by {@link Context#getExecutorService()}.
     * When nesting {@code Parallel} actions, supply a dedicated {@link ExecutorService} to
     * inner actions via {@link #of(String, ExecutorService, Action...)} to avoid thread starvation.</p>
     *
     * @param name the action name; must not be {@code null}
     * @param parallelism the maximum number of children to execute concurrently; must be positive
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, int parallelism, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, parallelism, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Creates a parallel action that dispatches child execution to the supplied executor service.
     *
     * @param name the action name; must not be {@code null}
     * @param executorService the executor service to use; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, ExecutorService executorService, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(executorService, "executorService must not be null");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, executorService, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a parallel action from varargs children using the supplied executor service.
     *
     * @param name the action name; must not be {@code null}
     * @param executorService the executor service to use; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, ExecutorService executorService, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(executorService, "executorService must not be null");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Parallel instance = new Parallel(name, executorService, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Returns the configured concurrency limit.
     *
     * @return the maximum number of children that may run concurrently
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the executor service used for dispatch, if one was explicitly configured.
     *
     * @return the configured executor service, if present
     */
    public Optional<ExecutorService> executorService() {
        return Optional.ofNullable(executorService);
    }

    /**
     * Returns the child actions executed by this parallel action.
     *
     * <p>The returned list is unmodifiable and reflects the registration order established
     * at construction time.</p>
     *
     * @return the child actions
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    /**
     * Executes all child actions concurrently and aggregates their resulting statuses.
     *
     * <p>Each child runs with its own child {@link Context}. Concurrency is capped by the
     * configured parallelism even if the underlying executor could schedule more tasks.</p>
     *
     * @param context the execution context for this action
     * @throws NullPointerException if {@code context} is {@code null}
     */
    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Result.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();

        ExecutorService es = (executorService != null) ? executorService : context.getExecutorService();
        Semaphore semaphore = new Semaphore(parallelism, true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Action child : getChildren()) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            futures.add(CompletableFuture.runAsync(
                    () -> {
                        try {
                            child.execute(context.createChild());
                        } finally {
                            semaphore.release();
                        }
                    },
                    es));
        }

        for (CompletableFuture<Void> f : futures) {
            f.join();
        }

        this.result = Result.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
