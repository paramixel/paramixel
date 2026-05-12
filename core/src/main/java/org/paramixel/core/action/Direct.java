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
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.internal.UnrecoverableErrors;
import org.paramixel.core.support.Arguments;

/**
 * Executes a single callback against a {@link Context}.
 *
 * <p>{@code Direct} is the simplest executable action type. The supplied {@link Executable} determines the outcome.
 * Throwing {@link SkipException} marks the action as skipped, throwing {@link FailException} marks it as failed, and
 * any other throwable that is not an {@link OutOfMemoryError} or {@link StackOverflowError} is reported to the
 * listener and converted into a failure result. Unrecoverable errors are rethrown immediately.
 */
public final class Direct extends AbstractAction {

    /**
     * The callback executed when this action runs.
     */
    protected final Executable executable;

    /**
     * Creates a direct action with the supplied name, callback, and context mode.
     *
     * @param name the action name
     * @param executable the callback to execute
     * @param contextMode the context mode applied when this action executes or skips
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    private Direct(String name, Executable executable, Action.ContextMode contextMode) {
        super(contextMode);
        this.name = validateName(name);
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
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
    public final Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (contextMode == Action.ContextMode.ISOLATED) {
            context = context.createChild();
        }
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
        private Action.ContextMode contextMode = Action.ContextMode.ISOLATED;
        private Executable executable;
        private boolean built;

        private Builder(String name) {
            Objects.requireNonNull(name, "name must not be null");
            Arguments.requireNonBlank(name, "name must not be blank");
            this.name = name;
        }

        /**
         * Sets the context mode for this action.
         *
         * @param contextMode the context mode
         * @return this builder
         * @throws NullPointerException if {@code contextMode} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder contextMode(Action.ContextMode contextMode) {
            ensureNotBuilt();
            this.contextMode = Objects.requireNonNull(contextMode, "contextMode must not be null");
            return this;
        }

        /**
         * Sets the callback to execute.
         *
         * @param executable the callback to execute
         * @return this builder
         * @throws NullPointerException if {@code executable} is {@code null}
         * @throws IllegalStateException if this builder has already been built
         */
        public Builder execute(Executable executable) {
            ensureNotBuilt();
            this.executable = Objects.requireNonNull(executable, "executable must not be null");
            return this;
        }

        /**
         * Builds a new direct action.
         *
         * @return a new direct action
         * @throws IllegalStateException if no executable has been set or if this builder has already been built
         */
        public Direct build() {
            ensureNotBuilt();
            built = true;
            if (executable == null) {
                throw new IllegalStateException("executable must be configured");
            }
            var instance = new Direct(name, executable, contextMode);
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
     * Invokes the configured {@link Executable} callback and maps the outcome to a pass, skip, or failure status.
     *
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public final Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        if (contextMode == Action.ContextMode.ISOLATED) {
            context = context.createChild();
        }
        var result = new DefaultResult(this);
        var listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        try {
            executable.execute(context);
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
     * Functional callback executed by a {@link Direct} action.
     */
    @FunctionalInterface
    public interface Executable {

        /**
         * Performs the action's work.
         *
         * @param context the execution context
         * @throws SkipException to mark the action as skipped
         * @throws FailException to mark the action as failed
         * @throws Throwable any other throwable to report as an action failure
         */
        void execute(Context context) throws Throwable;
    }
}
