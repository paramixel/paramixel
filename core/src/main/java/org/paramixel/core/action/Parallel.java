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
import org.paramixel.core.internal.DefaultContext;
import org.paramixel.core.internal.Results;
import org.paramixel.core.internal.util.Arguments;

/**
 * A built-in action that executes child actions concurrently.
 */
public class Parallel extends AbstractAction {

    private final int parallelism;
    private final List<Action> children;
    private final ExecutorService executorService;

    private Parallel(String name, int parallelism, List<Action> children) {
        this(name, parallelism, children, null);
    }

    private Parallel(String name, ExecutorService executorService, List<Action> children) {
        super(name);
        this.parallelism = Integer.MAX_VALUE;
        this.children = validateChildren(children);
        this.executorService = executorService;
    }

    private Parallel(String name, int parallelism, List<Action> children, ExecutorService executorService) {
        super(name);
        this.parallelism = parallelism;
        this.children = validateChildren(children);
        this.executorService = executorService;
    }

    /**
     * Creates a parallel action that executes all children using the default execution strategy.
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        return new Parallel(name, Integer.MAX_VALUE, children);
    }

    /**
     * Creates a parallel action that limits concurrent child execution.
     *
     * @param name the action name; must not be {@code null}
     * @param parallelism the maximum number of children to execute concurrently; must be positive
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, int parallelism, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
        return new Parallel(name, parallelism, children);
    }

    /**
     * Creates a parallel action from varargs children using the default execution strategy.
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        return new Parallel(name, Integer.MAX_VALUE, List.of(children));
    }

    /**
     * Creates a parallel action from varargs children with a concurrency limit.
     *
     * @param name the action name; must not be {@code null}
     * @param parallelism the maximum number of children to execute concurrently; must be positive
     * @param children the child actions; must not be {@code null} or empty
     * @return a new parallel action
     */
    public static Parallel of(String name, int parallelism, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        return new Parallel(name, parallelism, List.of(children));
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
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(executorService, "executorService must not be null");
        return new Parallel(name, executorService, children);
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
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(executorService, "executorService must not be null");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        return new Parallel(name, executorService, List.of(children));
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

    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Results.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();

        ExecutorService es = (executorService != null) ? executorService : context.getExecutorService();
        Semaphore semaphore = new Semaphore(parallelism, true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        DefaultContext defaultContext = (DefaultContext) context;

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
                            child.execute(new DefaultContext(defaultContext));
                        } finally {
                            semaphore.release();
                        }
                    },
                    es));
        }

        for (CompletableFuture<Void> f : futures) {
            f.join();
        }

        this.result = Results.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
