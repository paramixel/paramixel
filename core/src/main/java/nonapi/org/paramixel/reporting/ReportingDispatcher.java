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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import nonapi.org.paramixel.execution.EventPublisher;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.Failure;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Task;
import org.paramixel.api.TaskEvent;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Bounded, asynchronous event delivery.
 *
 * <p>The normal queue limit applies backpressure to admission. A bounded execution reserve allows the scheduler to
 * keep publishing events from work already in flight, timeouts, cancellation, and cleanup without blocking. The
 * dispatcher delivers events in FIFO order, then invokes {@link Listener#onRunCompleted(Result)} on the same thread.
 * A listener that stops making progress under pressure or during final delivery is interrupted and the run reports a
 * {@value #DRAIN_TIMEOUT_DIAGNOSTIC} infrastructure failure.
 */
public final class ReportingDispatcher implements EventPublisher, AutoCloseable {

    /** The diagnostic code recorded when listeners did not drain within the configured timeout. */
    public static final String DRAIN_TIMEOUT_DIAGNOSTIC = Listener.DRAIN_TIMEOUT_DIAGNOSTIC;

    /** The drain timeout used when {@link Configuration#LISTENER_DRAIN_TIMEOUT_MILLIS} is unset. */
    public static final long DEFAULT_DRAIN_TIMEOUT_MILLIS = 30_000L;

    private static final int DEFAULT_QUEUE_CAPACITY = 8192;

    private final SafeListener safe;
    private final Thread thread;
    private final int capacity;
    private final long stallBudgetNanos;

    // Guarded by deliveryLock. No listener callback is invoked while holding it.
    private final Object deliveryLock = new Object();
    private ArrayDeque<Object> queue = new ArrayDeque<>();
    private final List<String> diagnostics = new ArrayList<>();
    private long queueLimit;
    private long lastProgressNanos;
    private Runnable engineWakeup;
    private Result completion;
    private boolean delivered;
    private boolean stopped;
    private Failure deliveryFailure;
    private VirtualMachineError fatalListenerError;

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
        this.capacity = capacity;
        this.queueLimit = capacity;
        this.stallBudgetNanos = TimeUnit.MILLISECONDS.toNanos(drainTimeout);
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
        synchronized (deliveryLock) {
            diagnostics.add("warning: " + message);
        }
        enqueue(new Warning(message));
    }

    @Override
    public void beginExecution(final long reserve, final Runnable wakeup) {
        synchronized (deliveryLock) {
            queueLimit = capacity + reserve;
            engineWakeup = Objects.requireNonNull(wakeup, "wakeup is null");
        }
    }

    @Override
    public boolean isBackpressured() {
        synchronized (deliveryLock) {
            UnrecoverableErrors.rethrowIfUnrecoverable(fatalListenerError);
            return underPressure();
        }
    }

    @Override
    public long nanosUntilStall() {
        synchronized (deliveryLock) {
            return underPressure() ? remainingNanos() : Long.MAX_VALUE;
        }
    }

    @Override
    public boolean executionStalled() {
        synchronized (deliveryLock) {
            UnrecoverableErrors.rethrowIfUnrecoverable(fatalListenerError);
            if (underPressure() && remainingNanos() <= 0) {
                failDelivery("listener made no progress while the event queue was full");
            }
            return deliveryFailure != null;
        }
    }

    private boolean underPressure() {
        return !stopped && queue.size() >= capacity;
    }

    private long remainingNanos() {
        return stallBudgetNanos - (System.nanoTime() - lastProgressNanos);
    }

    private void enqueue(final Object item) {
        synchronized (deliveryLock) {
            try {
                // Discovery and compilation are synchronous, before the engine owns any running callbacks.
                while (engineWakeup == null && underPressure()) {
                    final var remaining = remainingNanos();
                    if (remaining <= 0) {
                        failDelivery("listener made no progress while the event queue was full");
                        break;
                    }
                    TimeUnit.NANOSECONDS.timedWait(deliveryLock, remaining);
                }
                UnrecoverableErrors.rethrowIfUnrecoverable(fatalListenerError);
                if (stopped || completion != null) {
                    return;
                }
                if (queue.size() >= queueLimit) {
                    failDelivery("the execution event reserve was exhausted");
                    return;
                }
                queue.addLast(item);
                if (queue.size() == capacity) {
                    lastProgressNanos = System.nanoTime();
                }
                deliveryLock.notifyAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failDelivery("event delivery was interrupted");
            }
        }
    }

    private void drainLoop() {
        try {
            while (true) {
                final Object item;
                synchronized (deliveryLock) {
                    while (!stopped && queue.isEmpty() && completion == null) {
                        deliveryLock.wait();
                    }
                    if (stopped) {
                        return;
                    }
                    if (queue.isEmpty()) {
                        item = completion;
                    } else {
                        item = queue.removeFirst();
                        if (queue.size() == capacity - 1) {
                            signalProgress();
                        }
                    }
                }
                deliver(item);
                // A callback may restore its interrupt flag before returning; it must not end delivery.
                Thread.interrupted();
                synchronized (deliveryLock) {
                    lastProgressNanos = System.nanoTime();
                    if (item instanceof Result) {
                        delivered = true;
                        deliveryLock.notifyAll();
                        return;
                    }
                    deliveryLock.notifyAll();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (VirtualMachineError e) {
            synchronized (deliveryLock) {
                fatalListenerError = e;
                stopDelivery();
            }
        }
    }

    private void deliver(final Object item) {
        if (item instanceof TaskEvent event) {
            safe.onTaskEvent(event);
        } else if (item instanceof Warning warning) {
            safe.onWarning(warning.message);
        } else if (item instanceof Result result) {
            safe.onRunCompleted(result.withAdditionalDiagnostics(listenerDiagnostics()));
        }
    }

    private void signalProgress() {
        deliveryLock.notifyAll();
        if (engineWakeup != null) {
            engineWakeup.run();
        }
    }

    /**
     * Drains the event stream, then delivers completion only if all queued events were processed. The no-progress
     * timeout also bounds the completion callback itself. Reporting failures are independent of the task outcome.
     *
     * @param result the engine's result; must not be {@code null}
     * @return the result with listener diagnostics and any delivery failure; never {@code null}
     * @throws NullPointerException if {@code result} is {@code null}
     * @throws InterruptedException if interrupted while waiting for the listener
     */
    public Result finish(final Result result) throws InterruptedException {
        Objects.requireNonNull(result, "result is null");
        synchronized (deliveryLock) {
            engineWakeup = null;
            if (!stopped) {
                if (!underPressure()) {
                    lastProgressNanos = System.nanoTime();
                }
                completion = result;
                deliveryLock.notifyAll();
                try {
                    while (!delivered && !stopped) {
                        final var remaining = remainingNanos();
                        if (remaining <= 0) {
                            failDelivery("listener made no progress during final delivery");
                            break;
                        }
                        TimeUnit.NANOSECONDS.timedWait(deliveryLock, remaining);
                    }
                } catch (InterruptedException e) {
                    stopDelivery();
                    throw e;
                }
            }
            UnrecoverableErrors.rethrowIfUnrecoverable(fatalListenerError);
            final var completed = result.withAdditionalDiagnostics(listenerDiagnostics());
            return deliveryFailure == null ? completed : completed.withInfrastructureFailure(deliveryFailure);
        }
    }

    private List<String> listenerDiagnostics() {
        synchronized (deliveryLock) {
            final var collected = new ArrayList<>(diagnostics);
            collected.addAll(safe.diagnostics());
            return collected;
        }
    }

    private void failDelivery(final String reason) {
        if (stopped) {
            return;
        }
        final var message = "Listener reporting is incomplete: " + reason + "; " + queue.size()
                + " queued item(s) were discarded and further delivery was stopped";
        deliveryFailure = Failure.of(DRAIN_TIMEOUT_DIAGNOSTIC, message, null, true);
        stopDelivery();
    }

    /** Stops the dispatcher without reporting a completed run. */
    @Override
    public void close() {
        synchronized (deliveryLock) {
            if (!stopped && !delivered) {
                stopDelivery();
            }
        }
    }

    private void stopDelivery() {
        stopped = true;
        // A callback that ignores interruption must not retain the backlog or its backing array.
        queue = null;
        completion = null;
        thread.interrupt();
        signalProgress();
        engineWakeup = null;
    }

    private record Warning(String message) {}
}
