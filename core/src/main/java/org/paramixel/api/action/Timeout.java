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
 * An action that executes a single body action with a wall-clock deadline.
 *
 * <p>If the body completes within the configured duration, the body's status
 * propagates. If the body does not complete within the duration, the action
 * fails with {@link Status#FAILED} and a message indicating the timeout.
 *
 * <p>On timeout, the body's subtree is cancelled best-effort: each still-running descendant
 * is marked terminal and has its scheduled future completed exceptionally, and each leaf
 * descendant has its executing thread interrupted. Cooperative (interruptible) bodies —
 * {@code Thread.sleep}, blocking {@code java.util.concurrent} operations, NIO, etc. — terminate
 * and release their leaf permits promptly.
 *
 * <p>Lifecycle cleanup is not cancelled. Within the subtree, the after action of any
 * {@link Scope}, {@link Instance}, or {@code Static} whose before action had been attempted still
 * runs (uninterrupted, even if it was already running at the deadline), and this action completes
 * only after that cleanup has finished. Cleanup is therefore not bounded by the timeout. A
 * lifecycle that had not started when the deadline passed is cancelled without running its
 * before or after action.
 *
 * <p>A leaf whose body ignores interruption (e.g. a CPU-bound loop) cannot be forcibly stopped.
 * If the cancelled subtree makes no cleanup progress for a grace period of five seconds, this
 * action stops waiting, marks the remaining descendants (including unreachable after actions)
 * {@link Status#ABORTED}, and completes; the stuck thread may keep its leaf permit until it
 * returns or the scheduler is closed. Because scheduler threads are daemon threads, such leaks
 * cannot prevent JVM shutdown.
 *
 * <p>Skipped executions short-circuit without executing the body.
 */
public final class Timeout implements Action {

    private final String displayName;
    private final Action body;
    private final Duration timeout;

    private Timeout(final String displayName, final Action body, final Duration timeout) {
        this.displayName = Objects.requireNonNull(displayName, "displayName is null");
        this.body = Objects.requireNonNull(body, "body is null");
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
    public static Builder timeout(final String displayName) {
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
     * Returns the body action.
     *
     * @return the body action; never {@code null}
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
    public static final class Builder implements ActionBuilder {

        private final String displayName;
        private Action body;
        private Duration timeout;

        private Builder(final String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets the body action. A builder argument is built immediately and the resulting
         * action snapshot is stored; an action argument is used as is.
         *
         * @param action the body action or builder; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code action} is {@code null}
         */
        public Builder body(final ActionBuilder action) {
            Objects.requireNonNull(action, "action is null");
            this.body = Objects.requireNonNull(action.build(), "action.build() returned null");
            return this;
        }

        /**
         * Sets the timeout duration.
         *
         * @param timeout the maximum duration for the body to complete; must not be {@code null} or non-positive
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
         * Builds the timeout action.
         *
         * @return a new timeout action
         * @throws IllegalStateException if no timeout is configured
         * @throws IllegalStateException if no body action was configured
         */
        @Override
        public Timeout build() {
            if (timeout == null) {
                throw new IllegalStateException("timeout duration must be configured");
            }
            if (body == null) {
                throw new IllegalStateException("body action must be configured");
            }
            return new Timeout(displayName, body, timeout);
        }
    }
}
