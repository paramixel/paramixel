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

import java.util.Objects;
import java.util.function.Predicate;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;

/**
 * An action that evaluates a {@link Predicate}{@code <Context>} before executing a single
 * body action.
 *
 * <p>When the predicate returns {@code true}, the body action executes normally and its
 * status propagates to this node. When the predicate returns {@code false}, this node
 * reports {@code PASSED} and the body action — together with all descendants — is
 * executed in {@code SKIP} mode, so the subtree appears as skipped in reports. Because
 * this node reports {@code PASSED} when the condition is not met, it does not disrupt
 * dependent sibling execution in {@link org.paramixel.api.action.Sequential} or
 * {@link org.paramixel.api.action.Sequence}.
 *
 * <p>If the predicate throws, this node is set to {@code FAILED} with a descriptive
 * message that includes the exception, and the body action is skipped.
 *
 * <p>The body action is always present in the descriptor tree regardless of the condition
 * outcome — only statuses change at runtime. This preserves structural invariants that
 * listeners and report formats depend on.
 *
 * <p>The predicate should be side-effect-free and idempotent. It receives the
 * {@link Context} for this node and can inspect {@link Context#configuration()} for
 * system properties, OS name, Java version, and user-defined values.
 */
public final class Conditional implements Action {

    private final String displayName;
    private final Predicate<Context> condition;
    private final Action body;

    private Conditional(final String displayName, final Predicate<Context> condition, final Action body) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(condition, "condition is null");
        this.condition = condition;
        this.body = Objects.requireNonNull(body, "body is null");
    }

    /**
     * Creates a new builder for a {@code Conditional} action with the given display
     * name and condition predicate.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param condition the predicate evaluated before the body; must not be {@code null}
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code condition} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName, final Predicate<Context> condition) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(condition, "condition is null");
        return new Builder(displayName, condition);
    }

    /**
     * Creates a new builder for a {@code Conditional} action with the given display name
     * and condition predicate.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param condition the predicate evaluated before the body; must not be {@code null}
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code condition} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     * @see Builder
     */
    public static Builder conditional(final String displayName, final Predicate<Context> condition) {
        return builder(displayName, condition);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the condition predicate.
     *
     * @return the condition predicate; never {@code null}
     */
    public Predicate<Context> condition() {
        return condition;
    }

    /**
     * Returns the body action.
     *
     * @return the body action; never {@code null}
     */
    public Action body() {
        return body;
    }

    /**
     * Fluent builder for {@link Conditional} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final Predicate<Context> condition;
        private Action body;

        private Builder(final String displayName, final Predicate<Context> condition) {
            this.displayName = displayName;
            this.condition = condition;
        }

        /**
         * Sets the body action. Calling this method again overwrites the previous
         * body action.
         *
         * @param action the body action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final Action action) {
            this.body = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the body action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the body action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder body(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.body = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Builds the conditional action.
         *
         * @return a new conditional action
         * @throws IllegalStateException if no body action was configured
         */
        @Override
        public Conditional build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            return new Conditional(displayName, condition, body);
        }
    }
}
