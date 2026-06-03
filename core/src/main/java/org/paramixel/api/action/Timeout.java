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

import java.time.Duration;
import java.util.Objects;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Status;

/**
 * An action that executes a single child with a wall-clock deadline.
 *
 * <p>If the child completes within the configured duration, the child's status
 * propagates. If the child does not complete within the duration, the action
 * fails with {@link Status#FAILED} and a message indicating the timeout.
 *
 * <p>On timeout, the child's executing thread is interrupted after a brief
 * grace period. If the child does not terminate after interruption, the action
 * records failure and continues — the child thread becomes orphaned. Because
 * the framework's scheduler uses daemon threads, orphaned threads cannot
 * prevent JVM shutdown.
 *
 * <p>Skipped executions short-circuit without executing the child.
 */
public final class Timeout implements Action {

    private final String displayName;
    private final Action body;
    private final Duration timeout;

    private Timeout(final String displayName, final Action body, final Duration timeout) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.body = Objects.requireNonNull(body, "child is null");
        this.timeout = validateTimeout(timeout);
    }

    /**
     * Creates a new builder for a {@code Timeout} action with the given display name.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @return a new builder
     * @throws NullPointerException if {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Builder builder(final String displayName) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        return new Builder(displayName);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the timeout duration.
     *
     * @return the timeout duration; never {@code null}
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns the child action.
     *
     * @return the child action; never {@code null}
     */
    public Action body() {
        return body;
    }

    private static Duration validateTimeout(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is null");
        Arguments.requireFalse(timeout.isZero() || timeout.isNegative(), "timeout must be positive, was: " + timeout);
        return timeout;
    }

    /**
     * Fluent builder for {@link Timeout} actions.
     */
    public static final class Builder implements org.paramixel.api.action.Builder {

        private final String displayName;
        private Action body;
        private Duration timeout;

        private Builder(final String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets the child action.
         *
         * @param action the child action; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final Action action) {
            this.body = Objects.requireNonNull(action, "action is null");
            return this;
        }

        /**
         * Sets the child action from a builder. The builder is built immediately
         * and the resulting action snapshot is stored.
         *
         * @param builder the child action builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code builder} is {@code null}
         */
        public Builder body(final org.paramixel.api.action.Builder builder) {
            Objects.requireNonNull(builder, "builder is null");
            this.body = Objects.requireNonNull(builder.build(), "builder.build() returned null");
            return this;
        }

        /**
         * Sets the timeout duration.
         *
         * @param timeout the maximum duration for the child to complete; must not be {@code null} or non-positive
         * @return this builder
         * @throws NullPointerException if {@code timeout} is {@code null}
         * @throws IllegalArgumentException if {@code timeout} is zero or negative
         */
        public Builder timeout(final Duration timeout) {
            Objects.requireNonNull(timeout, "timeout is null");
            Arguments.requireFalse(
                    timeout.isZero() || timeout.isNegative(), "timeout must be positive, was: " + timeout);
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the timeout duration in milliseconds.
         *
         * @param milliseconds the maximum duration in milliseconds; must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code milliseconds} is not positive
         */
        public Builder timeoutMillis(final long milliseconds) {
            return timeout(Duration.ofMillis(milliseconds));
        }

        /**
         * Builds the timeout action.
         *
         * @return a new timeout action
         * @throws IllegalStateException if no timeout is configured
         * @throws IllegalStateException if no child action was configured
         */
        @Override
        public Timeout build() {
            if (timeout == null) {
                throw new IllegalStateException("timeout duration must be configured");
            }
            if (body == null) {
                throw new IllegalStateException("child action must be configured");
            }
            return new Timeout(displayName, body, timeout);
        }
    }
}
