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
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Executes a single callback against a {@link Context}.
 *
 * <p>{@code Direct} is the simplest executable action type. The supplied {@link Executable} determines the outcome.
 * Throwing {@link SkipException} marks the action as skipped, throwing {@link FailException} marks it as failed, and
 * any other non-{@link Error} throwable is reported to the listener and converted into a failure result.
 */
public class Direct extends LeafAction {

    protected final Executable executable;

    protected Direct(String name, Executable executable) {
        this.name = validateName(name);
        this.executable = executable;
    }

    /**
     * Creates a direct action.
     *
     * @param name the action name
     * @param executable the callback to execute
     * @return a new direct action
     */
    public static Direct of(String name, Executable executable) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(executable, "executable must not be null");
        Direct instance = new Direct(name, executable);
        instance.initialize();
        return instance;
    }

    /**
     * Executes the configured callback.
     *
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();
        try {
            executable.execute(context);
            result.setStatus(DefaultStatus.PASS);
            result.setElapsedTime(Duration.between(start, Instant.now()));
        } catch (SkipException e) {
            result.setStatus(new DefaultStatus(DefaultStatus.Kind.SKIP, e.getMessage()));
            result.setElapsedTime(Duration.between(start, Instant.now()));
        } catch (FailException e) {
            result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, e.getMessage()));
            result.setElapsedTime(Duration.between(start, Instant.now()));
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            context.getListener().actionThrowable(result, t);
            result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, t));
            result.setElapsedTime(Duration.between(start, Instant.now()));
        }
        context.getListener().afterAction(result);
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
