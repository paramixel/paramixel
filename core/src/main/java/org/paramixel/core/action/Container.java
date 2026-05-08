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
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Ordered composition action with optional setup and cleanup actions.
 */
public final class Container extends AbstractAction implements CompositeAction {

    /** Controls whether body children continue after an earlier child fails or skips. */
    public enum ChildMode {

        /** Run every body child regardless of earlier failures or skips. */
        INDEPENDENT,

        /** Stop after the first failed or skipped body child and skip the remaining body children. */
        DEPENDENT
    }

    /** Controls the order used for body child execution. */
    public enum OrderMode {

        /** Run body children in builder declaration order. */
        DECLARED,

        /** Shuffle body children before execution. */
        SHUFFLED
    }

    /** Execution policy for body children. */
    public record Policy(ChildMode childMode, OrderMode orderMode, long seed) {

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

        /** Fluent builder for {@link Policy}. */
        public static final class Builder {

            private ChildMode childMode = ChildMode.DEPENDENT;
            private OrderMode orderMode = OrderMode.DECLARED;
            private Long seed = null;
            private boolean built;

            public Builder childMode(ChildMode childMode) {
                ensureNotBuilt();
                this.childMode = Objects.requireNonNull(childMode, "childMode must not be null");
                return this;
            }

            public Builder orderMode(OrderMode orderMode) {
                ensureNotBuilt();
                this.orderMode = Objects.requireNonNull(orderMode, "orderMode must not be null");
                return this;
            }

            public Builder seed(long seed) {
                ensureNotBuilt();
                this.seed = seed;
                return this;
            }

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

    @Override
    public List<Action> getChildren() {
        return allChildren;
    }

    public Optional<Action> getBefore() {
        return Optional.ofNullable(before);
    }

    public List<Action> getBodyChildren() {
        return bodyChildren;
    }

    public Optional<Action> getAfter() {
        return Optional.ofNullable(after);
    }

    public Policy getPolicy() {
        return policy;
    }

    @Override
    protected Result skipSelf(Context context) {
        DefaultResult result = new DefaultResult(this);
        for (Action child : allChildren) {
            Result childResult = child.skip(context);
            result.addChild(childResult);
        }
        result.setStatus(DefaultStatus.SKIP);
        result.setRunDuration(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /** Fluent builder for {@link Container}. */
    public static final class Builder {

        private final String name;
        private Action.ContextMode contextMode = Action.ContextMode.ISOLATED;
        private Policy policy = Policy.defaults();
        private Action before;
        private final List<Action> children = new ArrayList<>();
        private Action after;
        private boolean built;

        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        public Builder contextMode(Action.ContextMode contextMode) {
            ensureNotBuilt();
            this.contextMode = Objects.requireNonNull(contextMode, "contextMode must not be null");
            return this;
        }

        public Builder policy(Policy policy) {
            ensureNotBuilt();
            this.policy = Objects.requireNonNull(policy, "policy must not be null");
            return this;
        }

        public Builder before(Action before) {
            ensureNotBuilt();
            this.before = Objects.requireNonNull(before, "before must not be null");
            return this;
        }

        public Builder child(Action child) {
            ensureNotBuilt();
            children.add(Objects.requireNonNull(child, "child must not be null"));
            return this;
        }

        public Builder after(Action after) {
            ensureNotBuilt();
            this.after = Objects.requireNonNull(after, "after must not be null");
            return this;
        }

        public Container build() {
            ensureNotBuilt();
            built = true;
            if (before == null && children.isEmpty() && after == null) {
                throw new IllegalStateException("container must contain before, child, or after action");
            }
            Container instance = new Container(name, before, List.copyOf(children), after, policy, contextMode);
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

        List<Result> statusResults = new ArrayList<>();
        Status computedStatus = DefaultStatus.PASS;

        if (before != null) {
            Result beforeResult = before.execute(context);
            result.addChild(beforeResult);
            statusResults.add(beforeResult);
            if (beforeResult.getStatus().isFailure() || beforeResult.getStatus().isSkip()) {
                computedStatus = beforeResult.getStatus();
            }
        }

        List<Action> orderedChildren = orderedChildren();
        if (computedStatus.isPass()) {
            for (int index = 0; index < orderedChildren.size(); index++) {
                Action child = orderedChildren.get(index);
                Result childResult = child.execute(context);
                result.addChild(childResult);
                statusResults.add(childResult);
                if (policy.childMode() == ChildMode.DEPENDENT
                        && (childResult.getStatus().isFailure()
                                || childResult.getStatus().isSkip())) {
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

        result.setStatus(computeContainerStatus(statusResults));
        result.setRunDuration(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }

    private List<Action> orderedChildren() {
        if (policy.orderMode() == OrderMode.SHUFFLED) {
            List<Action> ordered = new ArrayList<>(bodyChildren);
            Collections.shuffle(ordered, new Random(policy.seed()));
            return ordered;
        }
        return bodyChildren;
    }

    private Status computeContainerStatus(List<Result> results) {
        for (Result childResult : results) {
            Objects.requireNonNull(childResult, "childResults must not contain null elements");
            if (childResult.getStatus().isFailure()) {
                return childResult.getStatus();
            }
        }
        for (Result childResult : results) {
            if (childResult.getStatus().isSkip()) {
                return childResult.getStatus();
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

    private static List<Action> allChildren(Action before, List<Action> children, Action after) {
        Objects.requireNonNull(children, "children must not be null");
        List<Action> all = new ArrayList<>();
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
