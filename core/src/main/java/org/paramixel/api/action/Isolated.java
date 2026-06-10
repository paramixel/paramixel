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
import nonapi.org.paramixel.support.Arguments;

/**
 * An action that executes a single body action under the protection of a named
 * re-entrant lock.
 *
 * <p>Two {@code Isolated} nodes with the same {@link #lockName()} serialize even
 * when placed in the same {@link Parallel}. Nodes with different lock names run
 * independently.
 *
 * <p>Nested {@code Isolated} actions with the same lock name are re-entrant: the
 * inner node re-acquires the lock that the outer node already holds, preventing
 * self-deadlock. Use a different lock name when nesting must produce independent
 * serialization boundaries.
 *
 * <p>This action resets neither the fixture instance holder nor static state.
 * Compose with {@link Instance} to provide per-test fixture isolation inside a
 * serialized body.
 *
 * <p>Skipped or aborted executions short-circuit without acquiring the lock.
 */
public final class Isolated implements Action {

    private final String displayName;
    private final String lockName;
    private final Action body;

    private Isolated(final String displayName, final String lockName, final Action body) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(lockName, "lockName is null");
        this.lockName = Arguments.requireNonBlank(lockName, "lockName is blank");
        this.body = Objects.requireNonNull(body, "body is null");
    }

    /**
     * Creates a new builder for an {@code Isolated} action with the given display
     * name and lock name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param lockName the name of the re-entrant lock shared by all {@code Isolated}
     *     nodes that should serialize with this one; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code lockName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} or {@code lockName} is blank
     */
    public static Builder builder(final String displayName, final String lockName) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(lockName, "lockName is null");
        Arguments.requireNonBlank(lockName, "lockName is blank");
        return new Builder(displayName, lockName);
    }

    /**
     * Creates a new builder for an {@code Isolated} action with the given display name
     * and lock name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param lockName the name of the re-entrant lock; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} or {@code lockName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} or {@code lockName} is blank
     * @see Builder
     */
    public static Builder isolated(final String displayName, final String lockName) {
        return builder(displayName, lockName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the name of the re-entrant lock that serializes execution across all
     * {@code Isolated} nodes that share this name.
     *
     * @return the lock name; never {@code null} or blank
     */
    public String lockName() {
        return lockName;
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
     * Fluent builder for {@link Isolated} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private final String lockName;
        private Action body;

        private Builder(final String displayName, final String lockName) {
            this.displayName = displayName;
            this.lockName = lockName;
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
         * Builds the isolated action.
         *
         * @return a new isolated action
         * @throws IllegalStateException if no body action was configured
         */
        @Override
        public Isolated build() {
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            return new Isolated(displayName, lockName, body);
        }
    }
}
