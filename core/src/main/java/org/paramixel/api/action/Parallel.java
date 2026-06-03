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

package org.paramixel.api.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nonapi.org.paramixel.support.Arguments;

/**
 * Composite action that executes all direct children concurrently with bounded admission.
 *
 * <p>This implementation limits how many direct child branches are unfinished at once. Each direct child
 * consumes one branch slot from this Parallel, regardless of whether that child is a Step, Sequence, or
 * nested Parallel. Nested Parallel actions enforce their own limits inside their own execution state.</p>
 *
 * <p>{@link #parallelism()} is an upper bound, not a guarantee. Actual concurrency may be lower due to
 * global scheduler limits ({@code paramixel.parallelism}), nested branch contention, and normal task
 * timing.</p>
 *
 * <p>This prevents large Parallel actions from flooding the scheduler ready queue while avoiding the
 * over-aggressive branch-weighting behavior that caused child Parallel actions to reserve multiple parent
 * slots and accidentally cap overall execution.</p>
 *
 * <p>Thread-safety: instances are immutable and safe for concurrent use.</p>
 */
public final class Parallel implements Action {

    private final String displayName;
    private final List<Action> children;
    private final int parallelism;

    private Parallel(final String displayName, final int parallelism, final List<Action> children) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.children = validateChildren(children);
        this.parallelism = parallelism;
    }

    /**
     * Creates a new builder for a Parallel action with the given display name.
     *
     * @param displayName the action display name (must not be {@code null} or blank)
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName) {
        return new Builder(displayName);
    }

    /**
     * Returns the action display name.
     *
     * @return the display name, never {@code null}
     */
    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the declared direct-child parallelism value.
     *
     * <p>When {@link Builder#parallelism(int)} is not called, this method returns {@link
     * Integer#MAX_VALUE} as an internal sentinel. In that case, execution-time admission inherits
     * the scheduler parallelism (derived from {@code paramixel.parallelism}).
     *
     * <p>When explicitly configured, this value is the requested maximum number of direct child
     * branches that may be in-flight simultaneously, capped by scheduler parallelism
     * ({@code paramixel.parallelism}). Global scheduler parallelism is enforced separately at
     * leaf-action execution time. At the root parallel node, runner-level top-level admission may
     * also be throttled by {@code paramixel.parallelism}.</p>
     *
     * <p>This value is a cap only. Runtime concurrency is best-effort and may be lower depending on
     * global and nested contention.</p>
     *
     * @return the parallelism limit
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the child actions.
     *
     * @return the child actions; never {@code null}
     */
    public List<Action> children() {
        return children;
    }

    private static List<Action> validateChildren(final List<Action> children) {
        Objects.requireNonNull(children, "children is null");
        var validated = new ArrayList<Action>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children contains null element");
            validated.add(child);
        }
        return List.copyOf(validated);
    }

    /**
     * Fluent builder for {@link Parallel} actions.
     *
     * <p>Builders are reusable; calling {@link #build()} creates a new immutable
     * {@code Parallel} snapshot from the builder's current configuration without
     * modifying the builder's state.</p>
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final List<Action> children = new ArrayList<>();
        private int parallelism = Integer.MAX_VALUE;

        private Builder(final String displayName) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            this.displayName = displayName;
        }

        /**
         * Sets the maximum number of in-flight direct child branches.
         *
         * <p>When this method is not called, direct-child admission inherits scheduler parallelism
         * at execution time (derived from {@code paramixel.parallelism}). When configured, the
         * requested value is capped by scheduler parallelism. Global scheduler parallelism is
         * enforced separately at leaf-action execution time. At the root parallel node, runner-level
         * top-level admission may also be throttled by {@code paramixel.parallelism}.</p>
         *
         * @param parallelism the parallelism limit (must be positive)
         * @return this builder
         * @throws IllegalArgumentException if {@code parallelism} is not positive
         */
        public Builder parallelism(final int parallelism) {
            Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Adds a child action.
         *
         * @param action the child action (must not be {@code null})
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder child(final Action action) {
            children.add(Objects.requireNonNull(action, "action is null"));
            return this;
        }

        /**
         * Adds a child action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the child action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder child(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            children.add(Objects.requireNonNull(builder.build(), "builder.build() returned null"));
            return this;
        }

        /**
         * Builds an immutable {@link Parallel} action from this builder's configuration.
         *
         * @return a new Parallel action
         */
        @Override
        public Parallel build() {
            return new Parallel(displayName, parallelism, List.copyOf(children));
        }
    }
}
