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

package nonapi.org.paramixel.listener;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;

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
        Objects.requireNonNull(listeners, "listeners is null");
        Arguments.requireNonEmpty(listeners, "listeners is empty");
        Arguments.requireNoNullElements(listeners, "listeners contains null element");
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
        Objects.requireNonNull(listeners, "listeners is null");
        Arguments.requireTrue(listeners.length > 0, "listeners is empty");
        Arguments.requireNoNullElements(listeners, "listeners contains null element");
        this.listeners = List.of(listeners);
        this.ansiEnabled = ansiEnabled;
    }

    @Override
    public void initialize(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration is null");
        invokeEach("initialize", listener -> listener.initialize(configuration));
    }

    @Override
    public void onDiscoveryStarted() {
        invokeEach("onDiscoveryStarted", Listener::onDiscoveryStarted);
    }

    @Override
    public void onRunStarted() {
        invokeEach("onRunStarted", Listener::onRunStarted);
    }

    @Override
    public void onDiscoveryCompleted(final Descriptor root) {
        Objects.requireNonNull(root, "root is null");
        invokeEach("onDiscoveryCompleted", listener -> listener.onDiscoveryCompleted(root));
    }

    @Override
    public void onBeforeExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        invokeEach("onBeforeExecution", listener -> listener.onBeforeExecution(descriptor));
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        invokeEach("onAfterExecution", listener -> listener.onAfterExecution(descriptor));
    }

    @Override
    public void onRunCompleted(final Result result) {
        Objects.requireNonNull(result, "result is null");
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
