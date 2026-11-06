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

/**
 * An action that executes a before action, a single body action, and an after action
 * without managing a test fixture instance.
 *
 * <p>This action is deprecated. It is functionally identical to {@link Scope}. Use
 * {@link Scope} instead.
 *
 * <p>Unlike {@link Instance}, this action does not create or destroy a fixture. It is
 * suitable for standalone test steps that do not require a shared instance. A before action
 * runs first. If the before action fails, the body action is skipped. The after action
 * always runs, regardless of earlier outcomes.
 *
 * <p>To execute multiple body actions, wrap a {@link Sequential} or {@link Parallel}
 * in {@link Builder#body(Action)}.
 *
 * @deprecated since 6.2.0, for removal — use {@link Scope} instead, which provides
 *     identical semantics and lifecycle guarantees.
 */
@Deprecated(forRemoval = true, since = "6.2.0")
public final class Static implements Action {

    private final Scope delegate;

    private Static(final Scope delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
    }

    /**
     * Creates a new builder for a {@code Static} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    @Deprecated(forRemoval = true)
    public static Builder builder(final String displayName) {
        ActionNames.requireDisplayName(displayName);
        return new Builder(displayName);
    }

    @Override
    public String displayName() {
        return delegate.displayName();
    }

    /**
     * Returns the before action, if one is declared.
     *
     * @return the before action, or empty if none is declared
     */
    public Optional<Action> before() {
        return delegate.before();
    }

    /**
     * Returns the body action.
     *
     * @return the body action; never {@code null}
     */
    public Action body() {
        return delegate.body();
    }

    /**
     * Returns the after action, if one is declared.
     *
     * @return the after action, or empty if none is declared
     */
    public Optional<Action> after() {
        return delegate.after();
    }

    /**
     * Fluent builder for {@link Static} actions.
     *
     * @deprecated since 6.2.0, for removal — use {@link Scope.Builder} instead.
     */
    @Deprecated(forRemoval = true)
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final Scope.Builder delegate;

        private Builder(final String displayName) {
            this.delegate = Scope.builder(displayName);
        }

        /**
         * Sets the before action. Calling this method
         * again overwrites the previous before action.
         *
         * @param action the before action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder before(final Action action) {
            delegate.before(action);
            return this;
        }

        /**
         * Sets the before action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the before action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder before(final org.paramixel.api.action.Builder builder) {
            delegate.before(builder);
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
            delegate.body(action);
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
            delegate.body(builder);
            return this;
        }

        /**
         * Sets the after action. Calling this method
         * again overwrites the previous after action.
         *
         * @param action the after action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder after(final Action action) {
            delegate.after(action);
            return this;
        }

        /**
         * Sets the after action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the after action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder after(final org.paramixel.api.action.Builder builder) {
            delegate.after(builder);
            return this;
        }

        /**
         * Builds the static action.
         *
         * @return a new static action
         */
        @Override
        public Static build() {
            return new Static(delegate.build());
        }
    }
}
