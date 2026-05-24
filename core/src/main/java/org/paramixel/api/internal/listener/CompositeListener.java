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

package org.paramixel.api.internal.listener;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.internal.listener.support.Constants;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.api.internal.support.UnrecoverableErrors;

/**
 * Forwards listener callbacks to multiple delegate listeners in order.
 */
public class CompositeListener implements Listener {

    private final List<Listener> listeners;
    private final boolean ansiEnabled;

    /**
     * Creates a composite listener from an ordered list of delegates.
     *
     * @param listeners the listeners to invoke
     */
    public CompositeListener(final List<Listener> listeners) {
        this(listeners, true);
    }

    /**
     * Creates a composite listener from an ordered list of delegates with configurable ANSI diagnostics.
     *
     * @param listeners the listeners to invoke
     * @param ansiEnabled whether ANSI diagnostics should be used
     */
    public CompositeListener(final List<Listener> listeners, final boolean ansiEnabled) {
        Objects.requireNonNull(listeners, "listeners must not be null");
        Arguments.requireNonEmpty(listeners, "listeners must not be empty");
        Arguments.requireNoNullElements(listeners, "listeners must not contain null elements");
        this.listeners = List.copyOf(listeners);
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * Creates a composite listener from an ordered array of delegates.
     *
     * @param listeners the listeners to invoke
     */
    public CompositeListener(final Listener... listeners) {
        this(true, listeners);
    }

    /**
     * Creates a composite listener from an ordered array of delegates with configurable ANSI diagnostics.
     *
     * @param ansiEnabled whether ANSI diagnostics should be used
     * @param listeners the listeners to invoke
     */
    public CompositeListener(final boolean ansiEnabled, final Listener... listeners) {
        Objects.requireNonNull(listeners, "listeners must not be null");
        Arguments.requireTrue(listeners.length > 0, "listeners must not be empty");
        Arguments.requireNoNullElements(listeners, "listeners must not contain null elements");
        this.listeners = List.of(listeners);
        this.ansiEnabled = ansiEnabled;
    }

    @Override
    public void onRunStarted() {
        invokeEach("onRunStarted", Listener::onRunStarted);
    }

    @Override
    public void onDiscoveryCompleted(final Descriptor root) {
        Objects.requireNonNull(root, "root must not be null");
        invokeEach("onDiscoveryCompleted", listener -> listener.onDiscoveryCompleted(root));
    }

    @Override
    public void onBeforeExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        invokeEach("onBeforeExecution", listener -> listener.onBeforeExecution(descriptor));
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        invokeEach("onAfterExecution", listener -> listener.onAfterExecution(descriptor));
    }

    @Override
    public void onRunCompleted(final Result result) {
        Objects.requireNonNull(result, "result must not be null");
        invokeEach("onRunCompleted", listener -> listener.onRunCompleted(result));
    }

    private void invokeEach(final String methodName, final Consumer<Listener> callback) {
        for (Listener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (Throwable t) {
                UnrecoverableErrors.rethrowIfUnrecoverable(t);
                log(methodName, t);
            }
        }
    }

    private void log(final String methodName, final Throwable throwable) {
        var prefix = ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
        System.err.println(prefix + "CompositeListener delegate Listener." + methodName + " threw exception: "
                + throwable.getMessage());
        throwable.printStackTrace(System.err);
    }
}
