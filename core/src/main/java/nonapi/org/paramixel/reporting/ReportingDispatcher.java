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
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.execution.EventPublisher;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Task;
import org.paramixel.api.TaskEvent;
import org.paramixel.api.exception.ConfigurationException;

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
 * {@link Listener#onRunCompleted(Result)} on the calling thread, so reporting cannot race finalization. The drain is
 * bounded by {@link Configuration#LISTENER_DRAIN_TIMEOUT_MILLIS}: a listener that does not drain in time has its
 * remaining events discarded, and the degradation is reported as a {@value #DRAIN_TIMEOUT_DIAGNOSTIC} diagnostic
 * instead of being hidden.
 */
public final class ReportingDispatcher implements EventPublisher, AutoCloseable {

    /** The diagnostic code recorded when listeners did not drain within the configured timeout. */
    public static final String DRAIN_TIMEOUT_DIAGNOSTIC = "LISTENER_DRAIN_TIMEOUT";

    /** The drain timeout used when {@link Configuration#LISTENER_DRAIN_TIMEOUT_MILLIS} is unset. */
    public static final long DEFAULT_DRAIN_TIMEOUT_MILLIS = 30_000L;

    private static final int DEFAULT_QUEUE_CAPACITY = 8192;

    private static final Object END = new Object();

    private final SafeListener safe;
    private final BlockingQueue<Object> queue;
    private final long drainTimeoutMillis;
    private final Thread thread;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final List<String> diagnostics = new ArrayList<>();
    private final Object diagnosticsLock = new Object();
    private volatile boolean discarding;

    /**
     * Creates and starts a dispatcher.
     *
     * @param listener the listener; must not be {@code null}
     * @param configuration the effective configuration; must not be {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws ConfigurationException if the queue capacity or the drain timeout is not positive
     */
    public ReportingDispatcher(final Listener listener, final Configuration configuration) {
        Objects.requireNonNull(listener, "listener is null");
        Objects.requireNonNull(configuration, "configuration is null");
        this.safe = new SafeListener(listener);
        final var capacity =
                configuration.getInteger(Configuration.LISTENER_QUEUE_CAPACITY).orElse(DEFAULT_QUEUE_CAPACITY);
        if (capacity <= 0) {
            throw new ConfigurationException(
                    "Invalid configuration for '" + Configuration.LISTENER_QUEUE_CAPACITY + "': must be positive");
        }
        final var drainTimeout = configuration
                .getLong(Configuration.LISTENER_DRAIN_TIMEOUT_MILLIS)
                .orElse(DEFAULT_DRAIN_TIMEOUT_MILLIS);
        if (drainTimeout <= 0) {
            throw new ConfigurationException("Invalid configuration for '" + Configuration.LISTENER_DRAIN_TIMEOUT_MILLIS
                    + "': must be positive but was " + drainTimeout);
        }
        this.drainTimeoutMillis = drainTimeout;
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
    public void discoveryCompleted(final Optional<Task> root) {
        safe.onDiscoveryCompleted(root);
    }

    @Override
    public void publish(final TaskEvent event) {
        enqueue(event);
    }

    /**
     * Records a warning as a run diagnostic immediately and queues it for {@link Listener#onWarning(String)}.
     *
     * <p>Recording happens on the calling thread, so the diagnostic is kept even when a stuck listener never receives
     * the queued warning.
     *
     * @param message the warning; must not be {@code null}
     */
    @Override
    public void warning(final String message) {
        if (finished.get()) {
            return;
        }
        record("warning: " + message);
        enqueue(new Warning(message));
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
            if (item == END || discarding) {
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
                safe.onWarning(warning.message);
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
     * Ends event delivery and reports the completed run.
     *
     * <p>Queued events are drained for at most {@link Configuration#LISTENER_DRAIN_TIMEOUT_MILLIS}. When the listener
     * does not drain in time, the remaining events are discarded, the dispatcher thread is interrupted, and a
     * {@value #DRAIN_TIMEOUT_DIAGNOSTIC} diagnostic is recorded. The completed result — the supplied result plus every
     * listener diagnostic recorded so far — is then passed to {@link Listener#onRunCompleted(Result)} on the calling
     * thread. Diagnostics recorded while {@code onRunCompleted} itself runs are appended to the returned result only.
     *
     * @param result the engine's result; must not be {@code null}
     * @return the completed result; never {@code null}
     * @throws NullPointerException if {@code result} is {@code null}
     * @throws InterruptedException if interrupted while waiting for the queue to drain
     */
    public Result finish(final Result result) throws InterruptedException {
        Objects.requireNonNull(result, "result is null");
        if (finished.compareAndSet(false, true)) {
            drain();
        }
        final var completed = result.withAdditionalDiagnostics(collectDiagnostics());
        safe.onRunCompleted(completed);
        return completed.withAdditionalDiagnostics(collectDiagnostics());
    }

    private void drain() throws InterruptedException {
        final var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(drainTimeoutMillis);
        if (queue.offer(END, drainTimeoutMillis, TimeUnit.MILLISECONDS)) {
            final var remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            // Thread.join(0) waits forever, so the remaining wait is never allowed to reach zero.
            thread.join(Math.max(1L, remainingMillis));
        }
        if (thread.isAlive()) {
            discarding = true;
            final var undelivered = undeliveredCount();
            thread.interrupt();
            record(DRAIN_TIMEOUT_DIAGNOSTIC + ": listener did not drain queued events within " + drainTimeoutMillis
                    + "ms; " + undelivered + " queued item(s) were discarded");
        }
    }

    private int undeliveredCount() {
        var count = 0;
        for (Object item : queue) {
            if (item != END) {
                count++;
            }
        }
        return count;
    }

    private List<String> collectDiagnostics() {
        final var collected = new ArrayList<String>();
        synchronized (diagnosticsLock) {
            collected.addAll(diagnostics);
            diagnostics.clear();
        }
        collected.addAll(safe.drainDiagnostics());
        return collected;
    }

    /**
     * Stops the dispatcher without reporting a completed run.
     */
    @Override
    public void close() {
        if (finished.compareAndSet(false, true)) {
            discarding = true;
            queue.offer(END);
            thread.interrupt();
        }
    }

    private record Warning(String message) {}
}
