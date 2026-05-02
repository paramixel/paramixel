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

package org.paramixel.core.listener;

import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * A {@link Listener} decorator that provides fault-tolerance by wrapping another listener
 * and guarding all callback invocations with {@code try/catch} blocks.
 *
 * <p>This class ensures that non-fatal exceptions thrown by the wrapped {@link Listener}
 * do not interrupt or break the execution flow of the {@link Runner}. Any non-fatal
 * {@link Throwable} (i.e., not an {@link Error}) raised during a callback is:</p>
 *
 * <ul>
 *   <li>Caught and suppressed</li>
 *   <li>Logged to {@code System.err}</li>
 *   <li>Followed by continuation of execution</li>
 * </ul>
 *
 * <p>{@link Error} subclasses (such as {@link OutOfMemoryError}, {@link StackOverflowError},
 * or {@link ThreadDeath}) are <strong>not caught</strong> and propagate immediately, since
 * they represent serious JVM-level failures that should not be silently suppressed.</p>
 *
 * <p>This is particularly useful in environments where listeners are optional,
 * user-provided, or non-critical, and should not compromise the stability of
 * the overall execution pipeline.</p>
 *
 * <p><strong>Note:</strong> This implementation logs directly to {@code System.err}.
 * In production environments, you may want to replace this with a proper logging framework.</p>
 */
public class SafeListener implements Listener {

    /**
     * Prefix used for log messages emitted by this class.
     */
    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    /**
     * The underlying listener being wrapped.
     */
    private final Listener delegate;

    /**
     * Wraps the given delegate listener with fault-tolerance.
     *
     * @param delegate the listener to wrap; must not be {@code null}
     * @return a new safe listener; never {@code null}
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public static SafeListener of(Listener delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new SafeListener(delegate);
    }

    /**
     * Constructs a new {@code SafeListener} that wraps the given delegate.
     *
     * @param delegate the listener to wrap; must not be {@code null}
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    private SafeListener(Listener delegate) {
        this.delegate = delegate;
    }

    /**
     * Invoked when a run is starting.
     *
     * <p>Any non-fatal exception thrown by the delegate is caught and logged.
     * {@link Error} subclasses are rethrown immediately.</p>
     *
     * @param runner the runner managing execution
     * @param action the root action being executed
     * @throws Error if the delegate throws an {@link Error}
     */
    @Override
    public void runStarted(Runner runner, Action action) {
        try {
            delegate.runStarted(runner, action);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("runStarted", t);
        }
    }

    /**
     * Invoked before an individual action is executed.
     *
     * <p>Any non-fatal exception thrown by the delegate is caught and logged.
     * {@link Error} subclasses are rethrown immediately.</p>
     *
     * @param context the current execution context
     * @param action  the action about to be executed
     * @throws Error if the delegate throws an {@link Error}
     */
    @Override
    public void beforeAction(Context context, Action action) {
        try {
            delegate.beforeAction(context, action);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("beforeAction", t);
        }
    }

    /**
     * Invoked when an action throws an exception.
     *
     * <p>Any non-fatal exception thrown by the delegate while handling the original
     * throwable is also caught and logged. {@link Error} subclasses are rethrown immediately.</p>
     *
     * @param context   the current execution context
     * @param action    the action that failed
     * @param throwable the exception thrown by the action
     * @throws Error if the delegate throws an {@link Error}
     */
    @Override
    public void actionThrowable(Context context, Action action, Throwable throwable) {
        try {
            delegate.actionThrowable(context, action, throwable);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("actionThrowable", t);
        }
    }

    /**
     * Invoked after an individual action has completed successfully.
     *
     * <p>Any non-fatal exception thrown by the delegate is caught and logged.
     * {@link Error} subclasses are rethrown immediately.</p>
     *
     * @param context the current execution context
     * @param action  the action that was executed
     * @param result  the result produced by the action
     * @throws Error if the delegate throws an {@link Error}
     */
    @Override
    public void afterAction(Context context, Action action, Result result) {
        try {
            delegate.afterAction(context, action, result);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("afterAction", t);
        }
    }

    /**
     * Invoked when a run has completed.
     *
     * <p>Any non-fatal exception thrown by the delegate is caught and logged.
     * {@link Error} subclasses are rethrown immediately.</p>
     *
     * @param runner the runner managing execution
     * @param action the root action that was executed
     * @throws Error if the delegate throws an {@link Error}
     */
    @Override
    public void runCompleted(Runner runner, Action action) {
        try {
            delegate.runCompleted(runner, action);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("runCompleted", t);
        }
    }

    /**
     * Logs an exception thrown by the delegate listener.
     *
     * @param methodName the name of the listener method where the exception occurred
     * @param t          the thrown exception
     */
    private void log(String methodName, Throwable t) {
        System.err.println(PARAMIXEL + "Listener." + methodName + " threw exception: " + t.getMessage());
        t.printStackTrace(System.err);
    }
}
