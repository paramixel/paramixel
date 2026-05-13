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
import java.util.Objects;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.internal.UnrecoverableErrors;
import org.paramixel.core.support.Arguments;

/**
 * Runs a single callback against a {@link Context}.
 *
 * <p>{@code Direct} is the simplest runnable action type. The supplied {@link ThrowableRunnable} determines the outcome.
 * Throwing {@link SkipException} marks the action as skipped, throwing {@link FailException} marks it as failed, and
 * any other throwable that is not an {@link OutOfMemoryError} or {@link StackOverflowError} is reported to the
 * listener and converted into a failure result. Unrecoverable errors are rethrown immediately.
 */
public final class Direct extends AbstractAction {

    /**
     * The callback run when this action runs.
     */
    protected final ThrowableRunnable throwableRunnable;

    /**
     * Creates a direct action with the supplied name and callback.
     *
     * @param name the action name
     * @param throwableRunnable the callback to run
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    private Direct(String name, ThrowableRunnable throwableRunnable) {
        super();
        this.name = validateName(name);
        this.throwableRunnable = Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
    }

    /**
     * Creates a new builder for composing a direct action.
     *
     * @param name the action name
     * @return a new direct action builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        result.complete(DefaultStatus.SKIP, Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /**
     * Fluent builder for {@link Direct} actions.
     */
    public static final class Builder {

        private final String name;
        private ThrowableRunnable throwableRunnable;
        private boolean built;

        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Sets the callback to run.
         *
         * @param throwableRunnable the callback whose outcome determines the action status
         * @return this builder
         * @throws NullPointerException if {@code throwableRunnable} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder runnable(ThrowableRunnable throwableRunnable) {
            ensureNotBuilt();
            this.throwableRunnable = Objects.requireNonNull(throwableRunnable, "throwableRunnable must not be null");
            return this;
        }

        /**
         * Builds a new direct action.
         *
         * @return a new direct action
         * @throws IllegalStateException if no throwableRunnable has been set or if this builder has already been built
         */
        public Direct build() {
            ensureNotBuilt();
            built = true;
            if (throwableRunnable == null) {
                throw new IllegalStateException("throwableRunnable must be configured");
            }
            var instance = new Direct(name, throwableRunnable);
            instance.initialize();
            return instance;
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("builder already built");
            }
        }
    }

    /**
     * Invokes the configured {@link ThrowableRunnable} callback and maps the outcome to a pass, skip, or failure status.
     *
     * @param context the run context
     * @return the run result
     */
    @Override
    public Result run(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        var listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        try {
            throwableRunnable.run(context);
            result.complete(DefaultStatus.PASS, Duration.between(start, Instant.now()));
        } catch (SkipException e) {
            result.complete(
                    new DefaultStatus(DefaultStatus.Kind.SKIP, e.getMessage()), Duration.between(start, Instant.now()));
        } catch (FailException e) {
            result.complete(
                    new DefaultStatus(DefaultStatus.Kind.FAILURE, e.getMessage()),
                    Duration.between(start, Instant.now()));
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            listener.actionThrowable(result, t);
            result.complete(new DefaultStatus(DefaultStatus.Kind.FAILURE, t), Duration.between(start, Instant.now()));
        }
        listener.afterAction(result);
        return result;
    }

    /**
     * Functional callback run by a {@link Direct} action.
     */
    @FunctionalInterface
    public interface ThrowableRunnable {

        /**
         * Performs the action's work.
         *
         * @param context the run context
         * @throws SkipException to mark the action as skipped
         * @throws FailException to mark the action as failed
         * @throws Throwable any other throwable to report as an action failure
         */
        void run(Context context) throws Throwable;
    }
}
