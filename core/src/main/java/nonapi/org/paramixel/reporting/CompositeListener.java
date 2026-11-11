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
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.TaskEvent;

/**
 * Delivers to several listeners in a deterministic order.
 *
 * <p>Composition is used by {@link DefaultListeners} so status output, the summary, and a report file all observe the
 * same event stream and the same immutable result.
 */
final class CompositeListener implements Listener {

    private final List<Listener> listeners;

    CompositeListener(final List<Listener> listeners) {
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners is null"));
    }

    @Override
    public void onRunStarted(final Configuration configuration) {
        for (Listener listener : listeners) {
            listener.onRunStarted(configuration);
        }
    }

    @Override
    public void onDiscoveryStarted() {
        for (Listener listener : listeners) {
            listener.onDiscoveryStarted();
        }
    }

    @Override
    public void onDiscoveryCompleted(final java.util.Optional<org.paramixel.api.Task> root) {
        for (Listener listener : listeners) {
            listener.onDiscoveryCompleted(root);
        }
    }

    @Override
    public void onWarning(final String message) {
        for (Listener listener : listeners) {
            listener.onWarning(message);
        }
    }

    @Override
    public void onTaskEvent(final TaskEvent event) {
        for (Listener listener : listeners) {
            listener.onTaskEvent(event);
        }
    }

    @Override
    public void onRunCompleted(final org.paramixel.api.Result result) {
        final var failures = new ArrayList<Throwable>();
        for (Listener listener : listeners) {
            try {
                listener.onRunCompleted(result);
            } catch (RuntimeException e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            final var first = failures.get(0);
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw new IllegalStateException("A reporting listener failed", first);
        }
    }
}
