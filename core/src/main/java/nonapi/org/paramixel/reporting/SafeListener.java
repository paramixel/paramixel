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

package nonapi.org.paramixel.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Task;
import org.paramixel.api.TaskEvent;

/**
 * Isolates a user listener from the engine.
 *
 * <p>A listener exception is recorded as a diagnostic and never changes a task's semantic status: a passing test cannot
 * be turned into a failing one by a broken reporter, and a failing test cannot be rescued by one either. Unrecoverable
 * VM errors are rethrown because continuing after them would be dishonest.
 */
final class SafeListener implements Listener {

    private static final int MAX_RECORDED_FAILURES = 20;

    private final Listener delegate;
    private final List<String> diagnostics = new ArrayList<>();
    private int failureCount;

    SafeListener(final Listener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
    }

    /**
     * Returns the recorded listener diagnostics.
     *
     * @return the immutable diagnostics; never {@code null}
     */
    synchronized List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }

    @Override
    public void onRunStarted(final Configuration configuration) {
        guard("onRunStarted", () -> delegate.onRunStarted(configuration));
    }

    @Override
    public void onDiscoveryStarted() {
        guard("onDiscoveryStarted", delegate::onDiscoveryStarted);
    }

    @Override
    public void onDiscoveryCompleted(final java.util.Optional<Task> root) {
        guard("onDiscoveryCompleted", () -> delegate.onDiscoveryCompleted(root));
    }

    @Override
    public void onWarning(final String message) {
        guard("onWarning", () -> delegate.onWarning(message));
    }

    @Override
    public void onTaskEvent(final TaskEvent event) {
        guard("onTaskEvent", () -> delegate.onTaskEvent(event));
    }

    @Override
    public void onRunCompleted(final Result result) {
        guard("onRunCompleted", () -> delegate.onRunCompleted(result));
    }

    private void guard(final String callback, final Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            record(callback, t);
        }
    }

    private synchronized void record(final String callback, final Throwable throwable) {
        failureCount++;
        if (failureCount <= MAX_RECORDED_FAILURES) {
            diagnostics.add("Listener " + callback + " threw "
                    + throwable.getClass().getName() + ": " + throwable.getMessage());
        }
    }
}
