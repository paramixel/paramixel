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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.paramixel.core.Action;
import org.paramixel.core.CompositeAction;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Executes child actions in order with optional setup and cleanup actions.
 *
 * <p>A {@link Container} runs an optional {@code before} action first, then the body children, and finally an
 * optional {@code after} action. The {@code after} action always runs regardless of earlier outcomes.
 *
 * <p>Body child execution is governed by {@link ChildMode} and {@link OrderMode}. When
 * {@link ChildMode#DEPENDENT}, the container stops after the first failed or skipped body child and skips the
 * remaining body children. When {@link ChildMode#INDEPENDENT}, every body child runs regardless of earlier
 * failures or skips.
 *
 * <p>The container status is computed from child results: failure takes precedence over skip, and skip takes
 * precedence over pass.
 */
public final class Container extends AbstractAction implements CompositeAction {

    /**
     * Controls whether body children continue after an earlier child fails or skips.
     */
    public enum ChildMode {

        /**
         * Run every body child regardless of earlier failures or skips.
         */
        INDEPENDENT,

        /**
         * Stop after the first failed or skipped body child and skip the remaining body children.
         */
        DEPENDENT
    }

    /**
     * Controls the order used for body child execution.
     */
    public enum OrderMode {

        /**
         * Run body children in builder declaration order.
         */
        DECLARED,

        /**
         * Shuffle body children before execution using the policy seed.
         */
        SHUFFLED
    }

    /**
     * Execution policy governing body child behavior and ordering.
     *
     * @param childMode whether body children continue after failures or skips
     * @param orderMode the execution order for body children
     * @param seed the seed used when {@code orderMode} is {@link OrderMode#SHUFFLED}
     */
    public record Policy(ChildMode childMode, OrderMode orderMode, long seed) {

        /**
         * Compact constructor that rejects null arguments.
         *
         * @throws NullPointerException if any argument is null
         */
        public Policy {
            Objects.requireNonNull(childMode, "childMode must not be null");
            Objects.requireNonNull(orderMode, "orderMode must not be null");
        }

        /**
         * Returns the default dependent, declared-order policy.
         *
         * @return the default policy
         */
        public static Policy defaults() {
            return new Policy(
                    ChildMode.DEPENDENT,
                    OrderMode.DECLARED,
                    ThreadLocalRandom.current().nextLong());
        }

        /**
         * Creates a policy builder.
         *
         * @return a new policy builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Fluent builder for {@link Policy}.
         */
        public static final class Builder {

            private ChildMode childMode = ChildMode.DEPENDENT;
            private OrderMode orderMode = OrderMode.DECLARED;
            private Long seed = null;
            private boolean built;

            /**
             * Creates a policy builder with default values.
             */
            private Builder() {
                // Intentionally empty
            }

            /**
             * Sets whether body children continue after failures or skips.
             *
             * @param childMode the child mode
             * @return this builder
             * @throws NullPointerException if {@code childMode} is {@code null}
             * @throws IllegalStateException if this builder has already been built
             */
            public Builder childMode(ChildMode childMode) {
                ensureNotBuilt();
                this.childMode = Objects.requireNonNull(childMode, "childMode must not be null");
                return this;
            }

            /**
             * Sets the execution order for body children.
             *
             * @param orderMode the order mode
             * @return this builder
             * @throws NullPointerException if {@code orderMode} is {@code null}
             * @throws IllegalStateException if this builder has already been built
             */
            public Builder orderMode(OrderMode orderMode) {
                ensureNotBuilt();
                this.orderMode = Objects.requireNonNull(orderMode, "orderMode must not be null");
                return this;
            }

            /**
             * Sets the seed used when {@link OrderMode#SHUFFLED} is selected.
             *
             * @param seed the shuffle seed
             * @return this builder
             * @throws IllegalStateException if this builder has already been built
             */
            public Builder seed(long seed) {
                ensureNotBuilt();
                this.seed = seed;
                return this;
            }

            /**
             * Builds an immutable policy from the configured criteria.
             *
             * <p>When no seed is supplied, a random seed is generated.
             *
             * @return a new policy
             * @throws IllegalStateException if this builder has already been built
             */
            public Policy build() {
                ensureNotBuilt();
                built = true;
                long resolvedSeed =
                        (seed != null) ? seed : ThreadLocalRandom.current().nextLong();
                return new Policy(childMode, orderMode, resolvedSeed);
            }

            private void ensureNotBuilt() {
                if (built) {
                    throw new IllegalStateException("builder already built");
                }
            }
        }
    }

    private final Action before;
    private final List<Action> bodyChildren;
    private final List<Action> allChildren;
    private final Action after;
    private final Policy policy;
    private final Random random;

    private Container(
            String name,
            Action before,
            List<Action> bodyChildren,
            Action after,
            Policy policy,
            Action.ContextMode contextMode) {
        super(contextMode);
        this.name = validateName(name);
        this.before = before;
        this.bodyChildren = List.copyOf(bodyChildren);
        this.allChildren = validateChildren(allChildren(before, bodyChildren, after));
        this.after = after;
        this.policy = policy;
        this.random = new Random(policy.seed());
    }

    /**
     * Creates a new container builder.
     *
     * @param name the container name
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Returns the before-action, if one is configured.
     *
     * @return the before-action, or an empty {@link Optional} when no before-action is configured
     */
    public Optional<Action> getBefore() {
        return Optional.ofNullable(before);
    }

    /**
     * Returns all child actions in this container, including the before-action, body children, and after-action.
     *
     * @return the immutable child list in before, body, after order
     */
    @Override
    public List<Action> getChildren() {
        return allChildren;
    }

    /**
     * Returns the body child actions in the order they will be executed.
     *
     * <p>The returned list does not include the before-action or after-action.
     *
     * @return the immutable body child list
     */
    public List<Action> getBodyChildren() {
        return bodyChildren;
    }

    /**
     * Returns the after-action, if one is configured.
     *
     * @return the after-action, or an empty {@link Optional} when no after-action is configured
     */
    public Optional<Action> getAfter() {
        return Optional.ofNullable(after);
    }

    /**
     * Returns the execution policy governing body child behavior.
     *
     * @return the container policy
     */
    public Policy getPolicy() {
        return policy;
    }

    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (contextMode == Action.ContextMode.ISOLATED) {
            context = context.createChild();
        }
        var result = new DefaultResult(this);
        for (Action child : allChildren) {
            Result childResult = child.skip(context);
            result.addChild(childResult);
        }
        result.complete(DefaultStatus.SKIP, Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /**
     * Fluent builder for {@link Container}.
     */
    public static final class Builder {

        private final String name;
        private Action.ContextMode contextMode = Action.ContextMode.ISOLATED;
        private Policy policy = Policy.defaults();
        private Action before;
        private final List<Action> children = new ArrayList<>();
        private Action after;
        private boolean built;

        /**
         * Creates a builder for a container with the supplied name.
         */
        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Sets the context mode for this container.
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
         * Sets the execution policy governing body child behavior.
         *
         * @param policy the policy for this container
         * @return this builder
         * @throws NullPointerException if {@code policy} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder policy(Policy policy) {
            ensureNotBuilt();
            this.policy = Objects.requireNonNull(policy, "policy must not be null");
            return this;
        }

        /**
         * Sets the before-action that runs before body children.
         *
         * @param before the action to run before body children
         * @return this builder
         * @throws NullPointerException if {@code before} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder before(Action before) {
            ensureNotBuilt();
            this.before = Objects.requireNonNull(before, "before must not be null");
            return this;
        }

        /**
         * Adds a body child action.
         *
         * @param child the body child action to add
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
         * Sets the after-action that runs after body children.
         *
         * <p>The after-action always runs regardless of earlier outcomes.
         *
         * @param after the action to run after body children
         * @return this builder
         * @throws NullPointerException if {@code after} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder after(Action after) {
            ensureNotBuilt();
            this.after = Objects.requireNonNull(after, "after must not be null");
            return this;
        }

        /**
         * Builds an immutable container from the configured criteria.
         *
         * @return a new container
         * @throws IllegalStateException if this builder has already been built, or if no before, child, or after
         *     action is configured
         */
        public Container build() {
            ensureNotBuilt();
            built = true;
            if (before == null && children.isEmpty() && after == null) {
                throw new IllegalStateException("container must contain before, child, or after action");
            }
            var instance = new Container(name, before, List.copyOf(children), after, policy, contextMode);
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
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (contextMode == Action.ContextMode.ISOLATED) {
            context = context.createChild();
        }
        var result = new DefaultResult(this);
        var listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();

        var statusResults = new ArrayList<Result>();
        Status computedStatus = DefaultStatus.PASS;

        if (before != null) {
            Result beforeResult = before.execute(context);
            result.addChild(beforeResult);
            statusResults.add(beforeResult);
            var beforeStatus = beforeResult.getStatus();
            if (beforeStatus.isFailure() || beforeStatus.isSkip()) {
                computedStatus = beforeStatus;
            }
        }

        List<Action> orderedChildren = orderedChildren();
        if (computedStatus.isPass()) {
            for (int index = 0; index < orderedChildren.size(); index++) {
                Action child = orderedChildren.get(index);
                Result childResult = child.execute(context);
                result.addChild(childResult);
                statusResults.add(childResult);
                var childStatus = childResult.getStatus();
                if (policy.childMode() == ChildMode.DEPENDENT && (childStatus.isFailure() || childStatus.isSkip())) {
                    for (Action remaining : orderedChildren.subList(index + 1, orderedChildren.size())) {
                        Result skipResult = remaining.skip(context);
                        result.addChild(skipResult);
                        statusResults.add(skipResult);
                    }
                    break;
                }
            }
        } else {
            for (Action child : orderedChildren) {
                Result skipResult = child.skip(context);
                result.addChild(skipResult);
                statusResults.add(skipResult);
            }
        }

        if (after != null) {
            Result afterResult = after.execute(context);
            result.addChild(afterResult);
            statusResults.add(afterResult);
        }

        result.complete(computeContainerStatus(statusResults), Duration.between(start, Instant.now()));
        listener.afterAction(result);
        return result;
    }

    private List<Action> orderedChildren() {
        if (policy.orderMode() == OrderMode.SHUFFLED) {
            var ordered = new ArrayList<>(bodyChildren);
            Collections.shuffle(ordered, random);
            return ordered;
        }
        return bodyChildren;
    }

    /**
     * Returns body children in the order selected for this execution.
     *
     * @return the ordered body children
     */
    public List<Action> orderedBodyChildren() {
        return orderedChildren();
    }

    private Status computeContainerStatus(List<Result> results) {
        for (Result childResult : results) {
            Objects.requireNonNull(childResult, "childResults must not contain null elements");
            var status = childResult.getStatus();
            if (status.isFailure()) {
                return status;
            }
        }
        for (Result childResult : results) {
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

    private static List<Action> allChildren(Action before, List<Action> children, Action after) {
        Objects.requireNonNull(children, "children must not be null");
        var all = new ArrayList<Action>();
        if (before != null) {
            all.add(before);
        }
        all.addAll(children);
        if (after != null) {
            all.add(after);
        }
        return all;
    }
}
