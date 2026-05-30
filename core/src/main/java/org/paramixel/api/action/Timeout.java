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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nonapi.org.paramixel.ExecutionRequest;
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

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
 * <p>Non-{@link Mode#RUN} modes short-circuit without executing the child.
 */
public final class Timeout implements Action<Void> {

    private static final Duration GRACE_PERIOD = Duration.ofMillis(1000);
    private static final String KIND = "Timeout";

    private final String name;
    private final Action<?> child;
    private final Duration timeout;

    private Timeout(final String name, final Action<?> child, final Duration timeout) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        this.child = Objects.requireNonNull(child, "child is null");
        Arguments.requireTrue(child != this, "action must not add itself as a child");
        this.timeout = validateTimeout(timeout);
    }

    /**
     * Creates a new spec for a {@code Timeout} action with the given name.
     *
     * @param name the action name; must not be {@code null} or blank
     * @return a new spec
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Spec of(final String name) {
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        return new Spec(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * Returns the child action.
     *
     * @return the child action; never {@code null}
     */
    public Action<?> child() {
        return child;
    }

    /**
     * Returns the timeout duration.
     *
     * @return the timeout duration; never {@code null}
     */
    public Duration timeout() {
        return timeout;
    }

    @Override
    public List<Action<?>> children() {
        return List.of(child);
    }

    @Override
    public void execute(final Context context) {
        Objects.requireNonNull(context, "context is null");
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                runChildren(context, mode);
                context.setStatus(mode.toStatus());
            } else {
                context.setStatus(run(context));
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
        }
        listener.onAfterExecution(descriptor);
    }

    private Status run(final Context context) {
        if (!(context instanceof ConcreteContext concreteContext)) {
            throw new IllegalArgumentException("context must be a ConcreteContext");
        }

        var childDescriptor = Arguments.requireInstanceOf(
                context.descriptor().children().get(0),
                MutableDescriptor.class,
                "child descriptor must be a MutableDescriptor");
        @SuppressWarnings("resource")
        var scheduler = concreteContext.scheduler();

        var childFuture = concreteContext.scheduleAsync(ExecutionRequest.run(childDescriptor));
        var timedFuture = childFuture.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            scheduler.managedJoin(timedFuture);
            return childDescriptor.metadata().status();
        } catch (CompletionException e) {
            var cause = e.getCause();
            if (cause instanceof TimeoutException) {
                return handleTimeout(childDescriptor);
            }
            if (cause instanceof Error err) {
                throw err;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    private Status handleTimeout(final MutableDescriptor childDescriptor) {
        if (!childDescriptor.metadata().isCompleted()) {
            var graceDeadline = System.nanoTime() + GRACE_PERIOD.toNanos();
            while (!childDescriptor.metadata().isCompleted() && System.nanoTime() < graceDeadline) {
                Thread.yield();
            }
        }

        if (!childDescriptor.metadata().isCompleted()) {
            childDescriptor.interruptExecutingThread();
            childDescriptor.setStatus(Status.failed("timed out after " + timeout.toMillis() + " ms"));
            childDescriptor.completeFuture();
        }

        return Status.FAILED;
    }

    private static Duration validateTimeout(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is null");
        Arguments.requireFalse(timeout.isZero() || timeout.isNegative(), "timeout must be positive, was: " + timeout);
        return timeout;
    }

    private static void runChildren(final Context context, final Mode mode) {
        if (context instanceof ConcreteContext concrete) {
            concrete.runChildren(mode);
        } else {
            throw new IllegalArgumentException("context must be a ConcreteContext");
        }
    }

    /**
     * Fluent spec for {@link Timeout} actions.
     */
    public static final class Spec implements org.paramixel.api.action.Spec<Void> {

        private final String name;
        private Action<?> child;
        private Duration timeout;
        private boolean resolved;

        private Spec(final String name) {
            this.name = name;
        }

        /**
         * Sets the timeout duration.
         *
         * @param timeout the maximum duration for the child to complete; must not be {@code null} or non-positive
         * @return this spec
         * @throws NullPointerException if {@code timeout} is {@code null}
         * @throws IllegalArgumentException if {@code timeout} is zero or negative
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec timeout(final Duration timeout) {
            ensureNotResolved();
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
         * @return this spec
         * @throws IllegalArgumentException if {@code milliseconds} is not positive
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec timeoutMillis(final long milliseconds) {
            return timeout(Duration.ofMillis(milliseconds));
        }

        /**
         * Sets the child action resolved from the supplied spec. Calling this method
         * again overwrites the previous child.
         *
         * @param spec the spec for the child action; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code spec} is {@code null}
         * @throws IllegalStateException if this spec has already been resolved
         */
        public Spec child(final org.paramixel.api.action.Spec<?> spec) {
            ensureNotResolved();
            this.child = Objects.requireNonNull(spec, "spec is null").resolve();
            return this;
        }

        /**
         * Sets a child action that invokes the supplied consumer.
         * Calling this method again overwrites the previous child.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the execution {@link Context}.
         */
        public Spec child(final String name, final ThrowingConsumer<?> consumer) {
            return child(Step.of(name, consumer));
        }

        /**
         * Sets a child action with a custom kind that invokes the supplied consumer.
         * Calling this method again overwrites the previous child.
         *
         * @param name the action name; must not be {@code null} or blank
         * @param kind the action kind; must not be {@code null} or blank
         * @param consumer the consumer to invoke; must not be {@code null}
         * @return this spec
         * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
         * @throws IllegalStateException if this spec has already been resolved
         * <p>The consumer receives the execution {@link Context}.
         */
        public Spec child(final String name, final String kind, final ThrowingConsumer<?> consumer) {
            return child(Step.of(name, kind, consumer));
        }

        /**
         * Builds the timeout action.
         *
         * @return a new timeout action
         * @throws IllegalStateException if this spec has already been resolved, no child is configured, or no timeout is configured
         */
        public Timeout resolve() {
            ensureNotResolved();
            if (child == null) {
                throw new IllegalStateException("timeout must contain a child action");
            }
            if (timeout == null) {
                throw new IllegalStateException("timeout duration must be configured");
            }
            resolved = true;
            return new Timeout(name, child, timeout);
        }

        private void ensureNotResolved() {
            if (resolved) {
                throw new IllegalStateException("spec already resolved");
            }
        }
    }
}
