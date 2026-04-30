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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * A {@link Listener} that delegates to multiple child listeners.
 *
 * <p>CompositeListener implements the composite pattern, allowing multiple listeners
 * to be combined into a single listener. When a callback is invoked on the composite,
 * it is forwarded to all child listeners in order.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Delegates to multiple listeners</li>
 *   <li>Preserves invocation order (first-to-last)</li>
 *   <li>Validates input (no nulls, non-empty)</li>
 *   <li>Immutable after construction</li>
 *   <li>Thread-safe for read-only access</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Combine multiple listeners
 * Listener combined = new CompositeListener(
 *     new StatusListener(),
 *     new SummaryListener(new TableSummaryRenderer()),
 *     new CustomLoggingListener()
 * );
 *
 * // Use with varargs
 * Listener combined = new CompositeListener(
 *     new StatusListener(),
 *     new FileLoggingListener("test.log")
 * );
 * }</pre>
 *
 * @see Listener
 * @see StatusListener
 * @see SummaryListener
 */
public class CompositeListener implements Listener {

    private final List<Listener> listeners;

    /**
     * Creates a composite listener from a list of child listeners.
     *
     * @param listeners the child listeners to delegate to; must not be {@code null} or empty
     * @throws NullPointerException if {@code listeners} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code listeners} is empty
     */
    public CompositeListener(List<Listener> listeners) {
        Objects.requireNonNull(listeners, "listeners must not be null");
        if (listeners.isEmpty()) {
            throw new IllegalArgumentException("listeners must not be empty");
        }
        List<Listener> validated = new ArrayList<>(listeners.size());
        for (Listener listener : listeners) {
            validated.add(Objects.requireNonNull(listener, "listeners must not contain null elements"));
        }
        this.listeners = Collections.unmodifiableList(validated);
    }

    /**
     * Creates a composite listener from varargs child listeners.
     *
     * @param listeners the child listeners to delegate to; must not be {@code null} or empty
     * @throws NullPointerException if {@code listeners} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if {@code listeners} is empty
     */
    public CompositeListener(Listener... listeners) {
        this(List.of(listeners));
    }

    @Override
    public void runStarted(Runner runner, Action action) {
        for (Listener listener : listeners) {
            listener.runStarted(runner, action);
        }
    }

    @Override
    public void runCompleted(Runner runner, Action action) {
        for (Listener listener : listeners) {
            listener.runCompleted(runner, action);
        }
    }

    @Override
    public void beforeAction(Context context, Action action) {
        for (Listener listener : listeners) {
            listener.beforeAction(context, action);
        }
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        for (Listener listener : listeners) {
            listener.afterAction(context, action, result);
        }
    }

    @Override
    public void actionThrowable(Context context, Action action, Throwable throwable) {
        for (Listener listener : listeners) {
            listener.actionThrowable(context, action, throwable);
        }
    }
}
