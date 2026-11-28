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

package nonapi.org.paramixel.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.compiler.CompiledOccurrence;
import org.paramixel.api.ExecutionMode;
import org.paramixel.api.Failure;
import org.paramixel.api.PhaseResult;
import org.paramixel.api.Status;
import org.paramixel.api.TaskResult;

/**
 * Mutable per-run execution state of one compiled occurrence.
 *
 * <p>All mutable scheduling fields are owned by the engine thread. The exceptions are the worker binding, which a
 * worker publishes under {@link #workerLock} so cooperative cancellation interrupts only the callback that is actually
 * running, and {@link #activeJobs}, which is atomic.
 *
 * <p>An occurrence owns one {@link Attempt} at a time. A retried or repeated occurrence completes several attempts;
 * each attempt has its own instance, phases, failures, children, and result node.
 */
final class OccurrenceState {

    /** The lifecycle stage of the occurrence, in execution order. */
    enum Stage {
        NEW,
        ADMITTED,
        CONDITION,
        BEFORE,
        BODY,
        CHILDREN,
        AFTER,
        TERMINAL
    }

    final ExecutionEngine engine;
    final CompiledOccurrence compiled;
    final OccurrenceState parent;
    final String id;
    final String path;
    final String name;
    final Instant queuedAt;
    final boolean discoveryRoot;

    Stage stage = Stage.NEW;
    Status status = Status.PENDING;
    // The occurrence-level reason: a timeout, a cancellation, or a never-admitted skip. A reason that belongs to one
    // attempt (a false condition) lives in Attempt.reason, so it cannot leak into a later attempt's outcome.
    String reason;
    final Cancellation cancellation = new Cancellation();
    boolean timedOut;
    final Admission admission = new Admission();
    Attempt attempt;
    final List<TaskResult> attemptNodes = new ArrayList<>();
    final List<Status> attemptStatuses = new ArrayList<>();
    int attemptCount;
    int outstandingJobs;
    long admittedNanos;
    long finalizationStartNanos;
    Instant admittedAt;
    Instant startedAt;
    Instant completedAt;
    long completedNanos;
    int graceGeneration;

    /**
     * The pending timeout timer of the current attempt, or {@code null}.
     */
    ScheduledFuture<?> timeoutTimer;

    /**
     * The pending grace timer, or {@code null}.
     */
    ScheduledFuture<?> graceTimer;

    /**
     * The pending retry or repeat delay timer, or {@code null}.
     */
    ScheduledFuture<?> retryTimer;

    /**
     * Guards {@link #activeThread} so an interrupt is delivered only while a callback of this occurrence is bound to
     * the worker thread.
     */
    private final Object workerLock = new Object();

    /**
     * The worker thread currently running a callback for this occurrence, if any. Guarded by {@link #workerLock}.
     */
    private Thread activeThread;

    /**
     * The number of callbacks of this occurrence that are currently running.
     */
    final AtomicInteger activeJobs = new AtomicInteger();

    OccurrenceState(
            final ExecutionEngine engine,
            final CompiledOccurrence compiled,
            final OccurrenceState parent,
            final Instant queuedAt,
            final boolean discoveryRoot) {
        this.engine = engine;
        this.compiled = compiled;
        this.parent = parent;
        this.id = compiled.id();
        this.path = compiled.path();
        this.name = compiled.name();
        this.queuedAt = queuedAt;
        this.discoveryRoot = discoveryRoot;
    }

    /**
     * Returns whether the occurrence reached a terminal stage.
     *
     * @return {@code true} when terminal
     */
    /**
     * Binds the calling worker thread to this occurrence before a callback runs.
     *
     * <p>The worker's interrupt status is cleared before the thread is published, so a stale interrupt from an
     * unrelated job cannot leak in and an interrupt requested after publication is never cleared.
     */
    void bindWorker() {
        synchronized (workerLock) {
            Thread.interrupted();
            activeThread = Thread.currentThread();
        }
    }

    /**
     * Unbinds the calling worker thread after a callback completes and clears any interrupt aimed at that callback.
     */
    void unbindWorker() {
        synchronized (workerLock) {
            activeThread = null;
            Thread.interrupted();
        }
    }

    /**
     * Interrupts the worker thread currently running a callback for this occurrence, if any.
     */
    void interruptWorker() {
        synchronized (workerLock) {
            if (activeThread != null) {
                activeThread.interrupt();
            }
        }
    }

    boolean isTerminal() {
        return stage == Stage.TERMINAL;
    }

    /**
     * Returns whether the occurrence or an ancestor was canceled.
     *
     * @return {@code true} when canceled
     */
    boolean isCanceled() {
        if (cancellation.requested) {
            return true;
        }
        return parent != null && parent.isCanceled();
    }

    /**
     * Returns the cancellation reason of this occurrence or the nearest canceled ancestor.
     *
     * @return the reason, or {@code null}
     */
    String cancellationReason() {
        if (cancellation.reason != null) {
            return cancellation.reason;
        }
        return parent == null ? null : parent.cancellationReason();
    }

    /**
     * Starts a new attempt, materializing the child occurrences that belong to it.
     *
     * @return the new attempt; never {@code null}
     */
    Attempt beginAttempt() {
        attemptCount++;
        final var fresh = new Attempt(attemptCount);
        for (CompiledOccurrence child : compiled.children()) {
            fresh.children.add(new OccurrenceState(engine, child, this, Instant.now(), false));
        }
        if (compiled.beforePhase() != null) {
            fresh.beforeChild = new OccurrenceState(engine, compiled.beforePhase(), this, Instant.now(), false);
        }
        if (compiled.afterPhase() != null) {
            fresh.afterChild = new OccurrenceState(engine, compiled.afterPhase(), this, Instant.now(), false);
        }
        final var now = Instant.now();
        fresh.startedAt = now;
        fresh.startNanos = System.nanoTime();
        if (startedAt == null) {
            startedAt = now;
        }
        attempt = fresh;
        return fresh;
    }

    /**
     * Returns the current attempt.
     *
     * @return the attempt; never {@code null} once the occurrence was admitted
     */
    Attempt currentAttempt() {
        return attempt;
    }

    /**
     * Returns whether this occurrence owns a context scope.
     *
     * <p>A scope is created at the run root, at every direct child of a {@code parallel} group, and at every task that
     * carries {@code retry} or {@code repeat}. A {@code before} or {@code after} subtree never owns a scope. Ownership
     * is a static property of the tree, so the compiler and the runtime cannot disagree about it.
     *
     * @return {@code true} when this occurrence owns a scope
     */
    boolean isScopeOwner() {
        if (parent == null) {
            return true;
        }
        if (compiled.slot() != CompiledOccurrence.PhaseSlot.CHILD) {
            // A before or after subtree belongs to its owner's sequential chain: it must read and write the owner's
            // scope even when the owner is parallel, whose ordinary direct children each own a scope of their own.
            return false;
        }
        final var task = compiled.task();
        return parent.compiled.task().mode() == ExecutionMode.PARALLEL
                || task.retry().isPresent()
                || task.repeat().isPresent();
    }

    /**
     * Returns this occurrence's nearest scope-owning self-or-ancestor.
     *
     * @return the owning occurrence; never {@code null}, because the root owns a scope
     */
    OccurrenceState scopeOwner() {
        var current = this;
        while (current != null && !current.isScopeOwner()) {
            current = current.parent;
        }
        return current;
    }

    /**
     * Returns the current attempt of this occurrence's scope owner.
     *
     * @return the owning attempt, or {@code null} when no attempt is materialized
     */
    Attempt scopeAttempt() {
        final var owner = scopeOwner();
        return owner == null ? null : owner.attempt;
    }

    /**
     * Finds a context entry in this scope or an enclosing scope, nearest first.
     *
     * @param name the entry name; must not be {@code null}
     * @return the value, or {@code null}
     */
    Object findContextEntry(final String name) {
        // A runtime entry wins over a plan parameter of the same name, so a hook can override a bound value for the
        // rest of its scope and Context.remove(name) reveals the binding again.
        for (var current = this; current != null; current = current.parent) {
            final var candidate = current.attempt;
            if (candidate != null) {
                final var value = candidate.context.get(name);
                if (value != null) {
                    return value;
                }
            }
        }
        // Bindings are read from the immutable plan along the occurrence chain, nearest first, so a descendant's
        // binding shadows an ancestor's only inside its own subtree and never leaks to a sibling.
        for (var current = this; current != null; current = current.parent) {
            final var value = current.compiled.task().bindings().get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Cancellation requested for this occurrence itself; ancestors are consulted through {@link #isCanceled()}.
     *
     * <p>The engine thread writes these fields while user callbacks read them on worker threads through
     * {@link org.paramixel.api.Context#isCanceled()}, so both are volatile: a callback polling for cancellation must
     * observe the request. The engine writes {@code reason} before {@code requested}, so a reader that sees the request
     * also sees its reason.
     */
    static final class Cancellation {

        volatile boolean requested;
        volatile String reason;
    }

    /**
     * Admission state: the pool permit and exclusive lock this occurrence holds or is waiting for.
     */
    static final class Admission {

        boolean permitHeld;
        String permitName;
        boolean lockHeld;
        String lockName;
        boolean waiting;
        String waitingFor;
    }

    /**
     * One execution attempt of an occurrence.
     */
    static final class Attempt {

        final int index;
        final List<OccurrenceState> children = new ArrayList<>();
        final List<PhaseResult> phases = new ArrayList<>();
        final List<Failure> failures = new ArrayList<>();

        /**
         * The occurrence of this attempt's {@code before} subtree, or {@code null} when the slot is absent or holds a
         * hook.
         */
        OccurrenceState beforeChild;

        /**
         * The occurrence of this attempt's {@code after} subtree, or {@code null} when the slot is absent or holds a
         * hook.
         */
        OccurrenceState afterChild;

        /**
         * The named context entries of the scope this attempt owns.
         */
        final ConcurrentHashMap<String, Object> context = new ConcurrentHashMap<>();

        /**
         * Set when the attempt finished and its scope was cleared; a later write from an abandoned callback is
         * discarded instead of repopulating a map nobody will read.
         */
        volatile boolean contextClosed;

        Status bodyStatus;
        Status conditionStatus;
        String reason;
        int childCursor;
        Status attemptStatus;
        Instant startedAt;
        Instant completedAt;
        long startNanos;
        long finalizationStartNanos;
        long completedNanos;

        Attempt(final int index) {
            this.index = index;
        }

        /**
         * Records a failure, marking the first one as primary.
         *
         * @param phase the phase name; must not be {@code null}
         * @param message the description; must not be {@code null}
         * @param throwable the cause, or {@code null}
         * @return the recorded failure; never {@code null}
         */
        Failure fail(final String phase, final String message, final Throwable throwable) {
            final var failure = Failure.of(phase, message, throwable, failures.isEmpty());
            failures.add(failure);
            return failure;
        }
    }
}
