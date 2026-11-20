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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.compiler.CompiledOccurrence;
import org.paramixel.api.Failure;
import org.paramixel.api.Key;
import org.paramixel.api.PhaseResult;
import org.paramixel.api.ResourceDeclaration;
import org.paramixel.api.Status;
import org.paramixel.api.TaskResult;

/**
 * Mutable per-run execution state of one compiled occurrence.
 *
 * <p>All mutable scheduling fields are owned by the engine thread. The single exception is
 * {@link #activeThread}, which a worker publishes so cooperative cancellation can interrupt the callback that is
 * actually running, and {@link #activeJobs}, which is atomic so a grace-period check stays correct.
 *
 * <p>An occurrence owns one {@link Attempt} at a time. A retried or repeated occurrence completes several attempts;
 * each attempt has its own instance, resources, phases, failures, children, and result node.
 */
final class OccurrenceState {

    /** The lifecycle stage of the occurrence, in execution order. */
    enum Stage {
        NEW,
        ADMITTED,
        PREPARING,
        CONDITION,
        BEFORE,
        BODY,
        CHILDREN,
        AFTER,
        RELEASING,
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
    int graceGeneration;

    /**
     * The worker thread currently running a callback for this occurrence, if any.
     */
    volatile Thread activeThread;

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
     * Returns the occurrence that owns this occurrence's lexical instance binding.
     *
     * <p>The owner is resolved through the compiled {@link CompiledOccurrence#bindingSlot() binding slot}, the same
     * identity the compiler used to validate typed callbacks, so runtime resolution cannot drift from validation. The
     * owner's instance may still be absent: it is created after the owner's resources, so a resource factory on the
     * owning task runs before it exists.
     *
     * @return the owning occurrence, which may be this occurrence, or {@code null} when no instance is bound in scope
     */
    OccurrenceState bindingOwner() {
        final var slot = compiled.bindingSlot();
        if (slot == null) {
            return null;
        }
        var current = this;
        while (current != null) {
            if (slot.equals(current.id)) {
                return current;
            }
            current = current.parent;
        }
        return null;
    }

    /**
     * Finds a keyed resource in this scope or an ancestor scope.
     *
     * @param key the key; must not be {@code null}
     * @return the resource, or {@code null}
     */
    Object findResource(final Key<?> key) {
        var current = this;
        while (current != null) {
            if (current.attempt != null) {
                final var value = current.attempt.resources.get(key);
                if (value != null) {
                    return value;
                }
            }
            current = current.parent;
        }
        return null;
    }

    /**
     * Cancellation requested for this occurrence itself; ancestors are consulted through {@link #isCanceled()}.
     */
    static final class Cancellation {

        boolean requested;
        String reason;
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
        final LinkedHashMap<Key<?>, Object> resources = new LinkedHashMap<>();
        final List<ResourceDeclaration> acquired = new ArrayList<>();
        final List<PhaseResult> phases = new ArrayList<>();
        final List<Failure> failures = new ArrayList<>();
        Object instance;
        Status bodyStatus;
        Status conditionStatus;
        int beforeIndex;
        int beforeFailedAtIndex = -1;
        int afterIndex;
        int childCursor;
        Status attemptStatus;
        Instant startedAt;
        Instant completedAt;
        long startNanos;
        long finalizationStartNanos;

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
