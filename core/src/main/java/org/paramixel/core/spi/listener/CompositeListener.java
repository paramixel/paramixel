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

import java.util.List;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.support.Arguments;

/**
 * Forwards listener callbacks to multiple delegate listeners in order.
 *
 * <p>This listener is useful for composing reporting behaviors without requiring a custom listener implementation.
 */
public class CompositeListener implements Listener {

    private final List<Listener> listeners;

    /**
     * Creates a composite listener from an ordered list of delegates.
     *
     * @param listeners the listeners to invoke for every callback
     * @throws NullPointerException if {@code listeners} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code listeners} is empty
     */
    public CompositeListener(List<Listener> listeners) {
        Objects.requireNonNull(listeners, "listeners must not be null");
        Arguments.requireNonEmpty(listeners, "listeners must not be empty");
        Arguments.requireNoNullElements(listeners, "listeners must not contain null elements");
        this.listeners = List.copyOf(listeners);
    }

    /**
     * Creates a composite listener from an ordered array of delegates.
     *
     * @param listeners the listeners to invoke for every callback
     * @throws NullPointerException if {@code listeners} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code listeners} is empty
     */
    public CompositeListener(Listener... listeners) {
        Objects.requireNonNull(listeners, "listeners must not be null");
        Arguments.require(listeners.length > 0, "listeners must not be empty");
        Arguments.requireNoNullElements(listeners, "listeners must not contain null elements");
        this.listeners = List.of(listeners);
    }

    @Override
    public void runStarted(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        for (Listener listener : listeners) {
            listener.runStarted(runner);
        }
    }

    @Override
    public void beforeAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        for (Listener listener : listeners) {
            listener.beforeAction(result);
        }
    }

    @Override
    public void actionThrowable(Result result, Throwable throwable) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(throwable, "throwable must not be null");
        for (Listener listener : listeners) {
            listener.actionThrowable(result, throwable);
        }
    }

    @Override
    public void afterAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        for (Listener listener : listeners) {
            listener.afterAction(result);
        }
    }

    @Override
    public void skipAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        for (Listener listener : listeners) {
            listener.skipAction(result);
        }
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        for (Listener listener : listeners) {
            listener.runCompleted(runner, result);
        }
    }
}
