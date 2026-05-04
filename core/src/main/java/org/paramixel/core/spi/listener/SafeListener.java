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

package org.paramixel.core.spi.listener;

import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * Wraps another {@link Listener} and suppresses non-{@link Error} callback failures.
 *
 * <p>When a delegate callback throws, this listener logs the problem to standard error and allows the run to
 * continue.
 */
public class SafeListener implements Listener {

    private static final String PARAMIXEL = Constants.PARAMIXEL;

    private final Listener delegate;

    /**
     * Creates a safe listener around the supplied delegate.
     *
     * @param delegate the listener to wrap
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public SafeListener(Listener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void runStarted(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        try {
            delegate.runStarted(runner);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("runStarted", t);
        }
    }

    @Override
    public void beforeAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        try {
            delegate.beforeAction(result);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("beforeAction", t);
        }
    }

    @Override
    public void actionThrowable(Result result, Throwable throwable) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(throwable, "throwable must not be null");
        try {
            delegate.actionThrowable(result, throwable);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("actionThrowable", t);
        }
    }

    @Override
    public void afterAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        try {
            delegate.afterAction(result);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("afterAction", t);
        }
    }

    @Override
    public void skipAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        try {
            delegate.skipAction(result);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("skipAction", t);
        }
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        try {
            delegate.runCompleted(runner, result);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            log("runCompleted", t);
        }
    }

    private void log(String methodName, Throwable t) {
        System.err.println(Constants.PARAMIXEL + "Listener." + methodName + " threw exception: " + t.getMessage());
        t.printStackTrace(System.err);
    }
}
