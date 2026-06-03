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
import java.util.Optional;
import nonapi.org.paramixel.support.Arguments;

/**
 * An action that executes a before-action, a single body child, and an after-action
 * in sequence, with lifecycle-style guarantees.
 *
 * <p>When configured as <em>dependent</em> (the default), a failure in the body child
 * causes the body child execution to fail. When configured as <em>independent</em>,
 * the body child runs regardless of its outcome. The after-action always runs,
 * regardless of earlier outcomes.
 *
 * <p>To execute multiple body actions, wrap a {@link Sequence} or {@link Parallel}
 * in {@link Builder#body(Action)}.
 */
public final class Scope implements Action {

    private final String displayName;
    private final Action before;
    private final Action body;
    private final Action after;

    private Scope(final String displayName, final Action before, final Action body, final Action after) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.before = before;
        this.body = Objects.requireNonNull(body, "body is null");
        this.after = after;
    }

    /**
     * Creates a new builder for a {@code Scope} with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     */
    public static Builder builder(final String displayName) {
        return new Builder(displayName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the before action.
     *
     * @return the before action, or empty if none is declared
     */
    public Optional<Action> before() {
        return Optional.ofNullable(before);
    }

    /**
     * Returns the body child action.
     *
     * @return the body child action; never {@code null}
     */
    public Action body() {
        return body;
    }

    /**
     * Returns the after action.
     *
     * @return the after action, or empty if none is declared
     */
    public Optional<Action> after() {
        return Optional.ofNullable(after);
    }

    /**
     * Fluent builder for {@link Scope} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private Action before;
        private Action body;
        private Action after;

        private Builder(final String displayName) {
            Objects.requireNonNull(displayName, "displayName is null");
            Arguments.requireNonBlank(displayName, "displayName is blank");
            this.displayName = displayName;
        }

        /**
         * Sets the before-action. Calling this method
         * again overwrites the previous before-action.
         *
         * @param action the before-action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder before(final Action action) {
            this.before = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the before-action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the before-action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder before(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.before = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Sets the body action. Calling this method
         * again overwrites the previous body action.
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
         * Sets the after-action. Calling this method
         * again overwrites the previous after-action.
         *
         * @param action the after-action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder after(final Action action) {
            this.after = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the after-action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the after-action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder after(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.after = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Builds the scope action.
         *
         * @return a new scope action
         */
        public Scope build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            return new Scope(displayName, before, body, after);
        }
    }
}
