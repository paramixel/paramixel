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
import org.paramixel.core.CompositeAction;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Executes child actions concurrently.
 *
 * <p>Child actions are submitted to an executor service and their concurrency is limited by a semaphore initialized
 * to the configured parallelism. When no explicit executor service is supplied, the context executor service is used.
 *
 * <p>All child actions are always submitted for execution regardless of individual outcomes. The parallel status is
 * computed from child results after all children complete: failure takes precedence over skip, and skip takes
 * precedence over pass.
 */
public final class Parallel extends AbstractAction implements CompositeAction {

    private final List<Action> children;
    private final int parallelism;
    private final ExecutorService executorService;

    private Parallel(
            String name,
            int parallelism,
            List<Action> children,
            ExecutorService executorService,
            Action.ContextMode contextMode) {
        super(contextMode);
        this.name = validateName(name);
        this.children = validateChildren(children);
        this.parallelism = parallelism;
        this.executorService = executorService;
    }

    /**
     * Creates a new parallel action builder.
     *
     * @param name the action name
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Returns all child actions in this parallel composition.
     *
     * @return the immutable child list in builder declaration order
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    /**
     * Returns the maximum number of child actions that may execute concurrently.
     *
     * @return the parallelism limit
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Returns the dedicated executor service, or an empty {@link Optional} when the context executor will be used.
     *
     * @return the configured executor service, or an empty {@link Optional} when none was supplied
     */
    public Optional<ExecutorService> getExecutorService() {
        return Optional.ofNullable(executorService);
    }

    @Override
    protected Result skipSelf(Context context) {
        DefaultResult result = new DefaultResult(this);
        for (Action child : children) {
            Result childResult = child.skip(context);
            result.addChild(childResult);
        }
        result.setStatus(DefaultStatus.SKIP);
        result.setRunDuration(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /** Fluent builder for {@link Parallel}. */
    public static final class Builder {

        private final String name;
        private final List<Action> children = new ArrayList<>();
        private Action.ContextMode contextMode = Action.ContextMode.ISOLATED;
        private int parallelism = Integer.MAX_VALUE;
        private ExecutorService executorService;
        private boolean built;

        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Sets the context mode for this parallel action.
         *
         * @param contextMode the context mode applied when this action executes or skips
         * @return this builder
         * @throws NullPointerException if {@code contextMode} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder contextMode(Action.ContextMode contextMode) {
            ensureNotBuilt();
            this.contextMode = Objects.requireNonNull(contextMode, "contextMode must not be null");
            return this;
        }

        /**
         * Sets the maximum number of child actions that may execute concurrently.
         *
         * @param parallelism the concurrency limit, must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code parallelism} is not positive
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder parallelism(int parallelism) {
            ensureNotBuilt();
            Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Sets a dedicated executor service for this parallel action.
         *
         * <p>When supplied, this executor service is used instead of the context executor service.
         *
         * @param executorService the executor service to use
         * @return this builder
         * @throws NullPointerException if {@code executorService} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder executorService(ExecutorService executorService) {
            ensureNotBuilt();
            this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
            return this;
        }

        /**
         * Adds a child action.
         *
         * @param child the child action to add
         * @return this builder
         * @throws NullPointerException if {@code child} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder child(Action child) {
            ensureNotBuilt();
            children.add(Objects.requireNonNull(child, "child must not be null"));
            return this;
        }

        /**
         * Builds an immutable parallel action from the configured criteria.
         *
         * @return a new parallel action
         * @throws IllegalStateException if this builder has already been built, or if no children are configured
         */
        public Parallel build() {
            ensureNotBuilt();
            built = true;
            Arguments.require(!children.isEmpty(), "children must not be empty");
            Parallel instance = new Parallel(name, parallelism, List.copyOf(children), executorService, contextMode);
            instance.initialize();
            return instance;
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("builder already built");
            }
        }
    }

    @Override
    protected Result executeSelf(Context context) {
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();

        ExecutorService es = (executorService != null) ? executorService : context.getExecutorService();
        Semaphore semaphore = new Semaphore(parallelism, true);
        List<CompletableFuture<Result>> futures = new ArrayList<>();

        try {
            for (Action child : children) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return child.execute(context);
                            } finally {
                                semaphore.release();
                            }
                        },
                        es));
            }

            for (CompletableFuture<Result> f : futures) {
                result.addChild(f.join());
            }

            result.setStatus(computeStatus(result.getChildren()));
            result.setRunDuration(Duration.between(start, Instant.now()));
            context.getListener().afterAction(result);
            return result;
        } catch (RuntimeException e) {
            boolean interrupted = e.getCause() instanceof InterruptedException;
            if (interrupted) {
                result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, e.getCause()));
            } else {
                result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, e));
            }
            result.setRunDuration(Duration.between(start, Instant.now()));
            context.getListener().afterAction(result);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
    }

    private Status computeStatus(List<Result> childResults) {
        for (Result childResult : childResults) {
            Objects.requireNonNull(childResult, "childResults must not contain null elements");
            if (childResult.getStatus().isFailure()) {
                return DefaultStatus.FAILURE;
            }
        }
        for (Result childResult : childResults) {
            if (childResult.getStatus().isSkip()) {
                return DefaultStatus.SKIP;
            }
        }
        return DefaultStatus.PASS;
    }

    private List<Action> validateChildren(List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        Arguments.requireNonEmpty(children, "children must not be empty");
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children must not contain null elements");
            Arguments.require(child != this, "action must not add itself as a child");
            validated.add(child);
        }
        return List.copyOf(validated);
    }
}
