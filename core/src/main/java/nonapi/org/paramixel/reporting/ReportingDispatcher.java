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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.execution.EventPublisher;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.TaskEvent;

/**
 * Bounded, asynchronous event delivery.
 *
 * <p>Production is synchronous with a bounded queue: when the queue is full, the scheduler waits for the dispatcher
 * instead of dropping events. That is a deliberate backpressure choice — a slow reporter slows admission rather than
 * corrupting the stream — and it cannot deadlock the scheduler because the dispatcher holds no scheduler lock and never
 * calls back into the engine.
 *
 * <p>Delivery guarantees: events are delivered in production order, exactly one terminal event per occurrence is
 * produced by the engine, and {@link #finish(Result)} drains every queued event before invoking
 * {@link Listener#onRunCompleted(Result)} on the calling thread, so reporting cannot race finalization.
 */
public final class ReportingDispatcher implements EventPublisher, AutoCloseable {

    private static final Object END = new Object();

    private final Listener listener;
    private final SafeListener safe;
    private final BlockingQueue<Object> queue;
    private final Thread thread;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final List<String> diagnostics = new ArrayList<>();
    private final Object diagnosticsLock = new Object();

    /**
     * Creates and starts a dispatcher.
     *
     * @param listener the listener; must not be {@code null}
     * @param configuration the effective configuration; must not be {@code null}
     * @throws org.paramixel.api.exception.ConfigurationException if the queue capacity is not positive
     */
    public ReportingDispatcher(final Listener listener, final Configuration configuration) {
        this.listener = java.util.Objects.requireNonNull(listener, "listener is null");
        this.safe = new SafeListener(listener);
        final var capacity =
                configuration.getInteger(Configuration.LISTENER_QUEUE_CAPACITY).orElse(8192);
        if (capacity <= 0) {
            throw new org.paramixel.api.exception.ConfigurationException(
                    "Invalid configuration for '" + Configuration.LISTENER_QUEUE_CAPACITY + "': must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.thread = new Thread(this::drainLoop, "paramixel-listener");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * Starts the run.
     *
     * @param configuration the effective configuration; must not be {@code null}
     */
    public void runStarted(final Configuration configuration) {
        safe.onRunStarted(configuration);
    }

    /**
     * Notifies discovery start.
     */
    public void discoveryStarted() {
        safe.onDiscoveryStarted();
    }

    /**
     * Notifies discovery completion.
     *
     * @param root the discovered root, or empty
     */
    public void discoveryCompleted(final java.util.Optional<org.paramixel.api.Task> root) {
        safe.onDiscoveryCompleted(root);
    }

    @Override
    public void publish(final TaskEvent event) {
        enqueue(event);
    }

    @Override
    public void warning(final String message) {
        enqueue(new Warning(message));
    }

    @Override
    public void diagnostic(final String message) {
        enqueue(new DiagnosticMessage(message));
    }

    private void enqueue(final Object item) {
        if (finished.get()) {
            return;
        }
        try {
            queue.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record("Event delivery was interrupted: " + e.getMessage());
        }
    }

    private void drainLoop() {
        while (true) {
            final Object item;
            try {
                item = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (item == END) {
                return;
            }
            deliver(item);
        }
    }

    private void deliver(final Object item) {
        try {
            if (item instanceof TaskEvent event) {
                safe.onTaskEvent(event);
            } else if (item instanceof Warning warning) {
                record("warning: " + warning.message);
                safe.onWarning(warning.message);
            } else if (item instanceof DiagnosticMessage diagnostic) {
                record(diagnostic.message);
            }
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            record("Listener delivery failed: " + t);
        }
    }

    private void record(final String message) {
        synchronized (diagnosticsLock) {
            diagnostics.add(message);
        }
    }

    /**
     * Drains all queued events, then invokes {@link Listener#onRunCompleted(Result)} on the calling thread.
     *
     * @param result the immutable result; must not be {@code null}
     * @return listener diagnostics recorded during delivery; never {@code null}
     * @throws InterruptedException if interrupted while waiting for the queue to drain
     */
    public List<String> finish(final Result result) throws InterruptedException {
        if (finished.compareAndSet(false, true)) {
            queue.put(END);
            thread.join(TimeUnit.SECONDS.toMillis(30));
        }
        safe.onRunCompleted(result);
        final var collected = new ArrayList<String>();
        synchronized (diagnosticsLock) {
            collected.addAll(diagnostics);
            diagnostics.clear();
        }
        collected.addAll(safe.diagnostics());
        return collected;
    }

    /**
     * Stops the dispatcher without reporting a completed run.
     */
    @Override
    public void close() {
        if (finished.compareAndSet(false, true)) {
            try {
                queue.put(END);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread.interrupt();
        }
    }

    private record Warning(String message) {}

    private record DiagnosticMessage(String message) {}
}
