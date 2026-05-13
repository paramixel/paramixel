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
import org.paramixel.core.Action;
import org.paramixel.core.AsyncScheduler;
import org.paramixel.core.CompositeAction;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.internal.SchedulerOverride;
import org.paramixel.core.support.Arguments;

/**
 * Runs child actions concurrently.
 *
 * <p>Child actions are submitted to Paramixel's scheduler and their concurrency is limited by the configured
 * parallelism.
 *
 * <p>All child actions are always submitted for running regardless of individual outcomes. The parallel status is
 * computed from child results after all children complete: failure takes precedence over skip, and skip takes
 * precedence over pass.
 */
public final class Parallel extends AbstractAction implements CompositeAction, SchedulerOverride {

    private final List<Action> children;
    private final int parallelism;
    private final AsyncScheduler scheduler;

    private Parallel(String name, int parallelism, List<Action> children, AsyncScheduler scheduler) {
        super();
        this.name = validateName(name);
        this.children = validateChildren(children);
        this.parallelism = parallelism;
        this.scheduler = scheduler;
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

    @Override
    public Optional<AsyncScheduler> schedulerOverride() {
        return Optional.ofNullable(scheduler);
    }

    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        for (Action child : children) {
            result.addChild(child.skip(context.createChild()));
        }
        result.complete(DefaultStatus.SKIP, Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /**
     * Fluent builder for {@link Parallel}.
     */
    public static final class Builder {

        private final String name;
        private final List<Action> children = new ArrayList<>();
        private int parallelism = Integer.MAX_VALUE;
        private AsyncScheduler scheduler;
        private boolean built;

        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
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
         * Sets the scheduler used to execute this parallel action's subtree.
         *
         * <p>The scheduler receives each direct child admitted by this {@link Parallel}. Descendant calls to
         * {@link Context#runAsync(Action)} also use this scheduler while executing within the subtree.
         *
         * @param scheduler the scheduler used for this parallel subtree
         * @return this builder
         * @throws NullPointerException if {@code scheduler} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder scheduler(AsyncScheduler scheduler) {
            ensureNotBuilt();
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
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
            var instance = new Parallel(name, parallelism, List.copyOf(children), scheduler);
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
    public Result run(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        var listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();

        try {
            for (Action child : children) {
                result.addChild(child.run(context.createChild()));
            }

            result.complete(computeStatus(result.getChildren()), Duration.between(start, Instant.now()));
            listener.afterAction(result);
            return result;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof OutOfMemoryError || cause instanceof StackOverflowError) {
                result.complete(
                        new DefaultStatus(DefaultStatus.Kind.FAILURE, (Error) cause),
                        Duration.between(start, Instant.now()));
                listener.afterAction(result);
                throw (Error) cause;
            }
            if (cause instanceof Error error) {
                listener.actionThrowable(result, error);
                result.complete(
                        new DefaultStatus(DefaultStatus.Kind.FAILURE, error), Duration.between(start, Instant.now()));
                listener.afterAction(result);
                return result;
            }
            boolean interrupted = cause instanceof InterruptedException;
            if (interrupted) {
                result.complete(
                        new DefaultStatus(DefaultStatus.Kind.FAILURE, e.getCause()),
                        Duration.between(start, Instant.now()));
            } else {
                result.complete(
                        new DefaultStatus(DefaultStatus.Kind.FAILURE, e), Duration.between(start, Instant.now()));
            }
            listener.afterAction(result);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
    }

    private Status computeStatus(List<Result> childResults) {
        for (Result childResult : childResults) {
            Objects.requireNonNull(childResult, "childResults must not contain null elements");
            var status = childResult.getStatus();
            if (status.isFailure()) {
                return status;
            }
        }
        for (Result childResult : childResults) {
            var status = childResult.getStatus();
            if (status.isSkip()) {
                return status;
            }
        }
        return DefaultStatus.PASS;
    }

    private List<Action> validateChildren(List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        Arguments.requireNonEmpty(children, "children must not be empty");
        var validated = new ArrayList<Action>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children must not contain null elements");
            Arguments.require(child != this, "action must not add itself as a child");
            validated.add(child);
        }
        return List.copyOf(validated);
    }
}
