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

import java.time.Duration;
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
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Executes child actions concurrently.
 *
 * <p>Parallel execution uses either an explicitly supplied {@link ExecutorService} or the executor service from the
 * current {@link Context}. A positive parallelism limit controls how many child actions may execute at once.
 */
public class Parallel extends BranchAction {

    private final int parallelism;
    private final ExecutorService executorService;

    /**
     * Creates a parallel action that uses the context executor and the supplied parallelism limit.
     *
     * @param name the action name
     * @param parallelism the maximum number of concurrently executing children
     * @param children the child actions
     */
    protected Parallel(String name, int parallelism, List<Action> children) {
        this(name, parallelism, children, null);
    }

    /**
     * Creates a parallel action that always uses the supplied executor service.
     *
     * @param name the action name
     * @param executorService the dedicated executor service
     * @param children the child actions
     */
    protected Parallel(String name, ExecutorService executorService, List<Action> children) {
        super(children);
        this.name = validateName(name);
        this.parallelism = Integer.MAX_VALUE;
        this.executorService = executorService;
    }

    /**
     * Creates a parallel action with explicit executor selection and parallelism limit.
     *
     * @param name the action name
     * @param parallelism the maximum number of concurrently executing children
     * @param children the child actions
     * @param executorService the dedicated executor service, or {@code null} to use the context executor
     */
    protected Parallel(String name, int parallelism, List<Action> children, ExecutorService executorService) {
        super(children);
        this.name = validateName(name);
        this.parallelism = parallelism;
        this.executorService = executorService;
    }

    /**
     * Creates a parallel action with unbounded internal parallelism.
     *
     * @param name the action name
     * @param children the child actions
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
     * Creates a parallel action with an explicit parallelism limit.
     *
     * @param name the action name
     * @param parallelism the maximum number of child actions running at once
     * @param children the child actions
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
     * Creates a parallel action with unbounded internal parallelism.
     *
     * @param name the action name
     * @param children the child actions
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
     * Creates a parallel action with an explicit parallelism limit.
     *
     * @param name the action name
     * @param parallelism the maximum number of child actions running at once
     * @param children the child actions
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
     * Creates a parallel action that uses the supplied executor service.
     *
     * @param name the action name
     * @param executorService the executor service to use
     * @param children the child actions
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
     * Creates a parallel action that uses the supplied executor service.
     *
     * @param name the action name
     * @param executorService the executor service to use
     * @param children the child actions
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
     * Returns the configured parallelism limit.
     *
     * @return the maximum number of child tasks allowed to run concurrently
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the explicitly configured executor service, if one exists.
     *
     * @return the configured executor service, or an empty {@link Optional} when the context executor will be used
     */
    public Optional<ExecutorService> executorService() {
        return Optional.ofNullable(executorService);
    }

    /**
     * Executes child actions concurrently and aggregates their results.
     *
     * @param context the execution context
     * @return the aggregated execution result
     */
    @Override
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();

        ExecutorService es = (executorService != null) ? executorService : context.getExecutorService();
        Semaphore semaphore = new Semaphore(parallelism, true);
        List<CompletableFuture<Result>> futures = new ArrayList<>();

        try {
            for (Action child : getChildren()) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return child.execute(context.createChild());
                            } finally {
                                semaphore.release();
                            }
                        },
                        es));
            }

            for (CompletableFuture<Result> f : futures) {
                Result childResult = f.join();
                result.addChild(childResult);
            }

            result.setStatus(computeStatus(result.getChildren()));
            result.setElapsedTime(Duration.between(start, Instant.now()));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
                result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, e.getCause()));
                result.setElapsedTime(Duration.between(start, Instant.now()));
                context.getListener().afterAction(result);
                Thread.currentThread().interrupt();
            } else {
                result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, e));
                result.setElapsedTime(Duration.between(start, Instant.now()));
                context.getListener().afterAction(result);
                throw e;
            }
            throw e;
        }
        context.getListener().afterAction(result);
        return result;
    }
}
