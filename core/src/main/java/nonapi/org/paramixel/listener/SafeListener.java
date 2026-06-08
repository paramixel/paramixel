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

import java.util.Objects;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;

/**
 * Listener wrapper that reports recoverable listener failures instead of aborting execution.
 */
public final class SafeListener implements Listener {

    private final Listener delegate;
    private final boolean ansiEnabled;

    /**
     * Creates a safe listener.
     *
     * @param delegate the delegate listener
     */
    public SafeListener(final Listener delegate) {
        this(delegate, true);
    }

    /**
     * Creates a safe listener with configurable ANSI diagnostics.
     *
     * @param delegate the delegate listener
     * @param ansiEnabled whether ANSI diagnostics should be used
     */
    public SafeListener(final Listener delegate, final boolean ansiEnabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
        this.ansiEnabled = ansiEnabled;
    }

    @Override
    public void initialize(final Configuration configuration) {
        invoke("initialize", () -> delegate.initialize(configuration));
    }

    @Override
    public void onDiscoveryStarted() {
        invoke("onDiscoveryStarted", delegate::onDiscoveryStarted);
    }

    @Override
    public void onDiscoveryCompleted(final Descriptor root) {
        invoke("onDiscoveryCompleted", () -> delegate.onDiscoveryCompleted(root));
    }

    @Override
    public void onRunStarted() {
        invoke("onRunStarted", delegate::onRunStarted);
    }

    @Override
    public void onBeforeExecution(final Descriptor descriptor) {
        invoke("onBeforeExecution", () -> delegate.onBeforeExecution(descriptor));
    }

    @Override
    public void onAfterExecution(final Descriptor descriptor) {
        invoke("onAfterExecution", () -> delegate.onAfterExecution(descriptor));
    }

    @Override
    public void onRunCompleted(final Result result) {
        invoke("onRunCompleted", () -> delegate.onRunCompleted(result));
    }

    private void invoke(final String methodName, final Runnable callback) {
        try {
            callback.run();
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            var prefix = ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
            System.err.println(prefix + "Listener." + methodName + " threw exception: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }
}
