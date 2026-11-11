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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.compiler.CompiledPlan;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.ExecutionMode;
import org.paramixel.api.Failure;
import org.paramixel.api.PhaseResult;
import org.paramixel.api.ResourceDeclaration;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.TaskCallback;
import org.paramixel.api.TaskPhase;
import org.paramixel.api.TaskResult;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Executes a compiled plan.
 *
 * <h2>Design</h2>
 *
 * <p>All scheduling decisions happen on one engine thread. User callbacks run on a bounded worker pool; each callback
 * is a separate job and completion is reported back to the engine as an event. Consequently:
 *
 * <ul>
 *   <li>No parent occupies a worker while waiting for its children: a parent advances only when the engine handles a
 *       child completion event.</li>
 *   <li>No lock is held while user code runs, so listener dispatch and reporting cannot deadlock the scheduler.</li>
 *   <li>Ready jobs are dispatched in compiled depth-first order, so an admitted branch's descendants are preferred over
 *       an unopened sibling. This is an admission preference, never a preemption promise.</li>
 *   <li>Pool permits and exclusive locks are logical counters. Waiting never pins a worker.</li>
 * </ul>
 *
 * <h2>Honest timeout behavior</h2>
 *
 * <p>Timeouts are cooperative. The engine interrupts the callback that is actually running, stops admitting new work in
 * the timed-out subtree, and waits for quiescence before running finalizers, because running {@code after} while the
 * body is still mutating the same resources is unsafe. If a callback ignores interruption past the configured grace
 * period, the run is reported <strong>non-quiescent</strong>: stuck work may still be running, finalizers were withheld,
 * and permits remain held. Java 17 cannot forcibly stop arbitrary code; claiming otherwise would be dishonest.
 *
 * <h2>Thread reuse</h2>
 *
 * <p>A worker thread's interrupt status is cleared before each job so one callback's cancellation cannot leak into an
 * unrelated task. Interruption is delivered only to callbacks of the canceled subtree.
 */
public final class ExecutionEngine {

    private static final long WATCHDOG_MILLIS = 30_000L;

    private final String runId;
    private final CompiledPlan plan;
    private final Configuration configuration;
    private final EventPublisher publisher;
    private final int workerLimit;
    private final Duration grace;
    private final boolean failFast;
    private final boolean failureOnSkip;
    private final boolean failureOnAbort;
    private final Long discoveryShuffleSeed;

    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final PriorityQueue<Job> ready = new PriorityQueue<>(
            Comparator.comparingInt((Job job) -> job.priority).thenComparingLong(job -> job.sequence));
    private final ResourceLedger<OccurrenceState> pools;
    private final ResourceLedger<OccurrenceState> locks;
    private final List<String> diagnostics = Collections.synchronizedList(new ArrayList<>());
    private final List<Failure> runFailures = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger pendingTimers = new AtomicInteger();

    private ExecutorService workers;
    private ScheduledExecutorService timers;
    private OccurrenceState root;
    private long eventSequence;
    private long jobSequence;
    private int runningJobs;
    private boolean finished;
    private boolean incomplete;
    private boolean runCanceled;
    private String runCancellationReason;
    private boolean rootFailureSeen;
    private Throwable fatal;
    private volatile boolean cancellationRequested;
    private volatile String cancellationRequestReason;

    /**
     * Creates an engine.
     *
     * @param runId the stable run identifier; must not be {@code null}
     * @param plan the compiled plan; must not be {@code null}
     * @param configuration the effective configuration; must not be {@code null}
     * @param publisher the event publisher; must not be {@code null}
     * @throws NullPointerException if an argument is {@code null}
     * @throws org.paramixel.api.exception.ConfigurationException if a numeric setting is invalid
     */
    public ExecutionEngine(
            final String runId,
            final CompiledPlan plan,
            final Configuration configuration,
            final EventPublisher publisher) {
        this(runId, plan, configuration, publisher, null);
    }

    /**
     * Creates an engine.
     *
     * @param runId the stable run identifier; must not be {@code null}
     * @param plan the compiled plan; must not be {@code null}
     * @param configuration the effective configuration; must not be {@code null}
     * @param publisher the event publisher; must not be {@code null}
     * @param discoveryShuffleSeed the discovery shuffle seed to report, or {@code null}
     * @throws NullPointerException if a required argument is {@code null}
     */
    public ExecutionEngine(
            final String runId,
            final CompiledPlan plan,
            final Configuration configuration,
            final EventPublisher publisher,
            final Long discoveryShuffleSeed) {
        this.discoveryShuffleSeed = discoveryShuffleSeed;
        this.runId = Objects.requireNonNull(runId, "runId is null");
        this.plan = Objects.requireNonNull(plan, "plan is null");
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.publisher = Objects.requireNonNull(publisher, "publisher is null");
        this.workerLimit =
                positiveSetting(Configuration.WORKERS, Runtime.getRuntime().availableProcessors());
        final long graceMillis = positiveSetting(Configuration.SHUTDOWN_GRACE_MILLIS, 10_000);
        this.grace = Duration.ofMillis(graceMillis);
        this.failFast = configuration.getBoolean(Configuration.FAIL_FAST).orElse(false);
        this.failureOnSkip =
                configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(true);
        this.failureOnAbort =
                configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true);
        final var poolCapacities = new LinkedHashMap<String, Integer>();
        poolCapacities.putAll(plan.poolCapacities());
        this.pools =
                new ResourceLedger<>(poolCapacities, Comparator.comparingInt(state -> state.compiled.preOrderIndex()));
        final var lockCapacities = new LinkedHashMap<String, Integer>();
        for (String lock : plan.lockNames()) {
            lockCapacities.put(lock, 1);
        }
        this.locks =
                new ResourceLedger<>(lockCapacities, Comparator.comparingInt(state -> state.compiled.preOrderIndex()));
    }

    private int positiveSetting(final String key, final int fallback) {
        final var value = configuration.getInteger(key).orElse(fallback);
        if (value <= 0) {
            throw new org.paramixel.api.exception.ConfigurationException(
                    "Invalid configuration for '" + key + "': must be positive but was " + value);
        }
        return value;
    }

    private long positiveSetting(final String key, final long fallback) {
        final var value = configuration.getLong(key).orElse(fallback);
        if (value <= 0) {
            throw new org.paramixel.api.exception.ConfigurationException(
                    "Invalid configuration for '" + key + "': must be positive but was " + value);
        }
        return value;
    }

    /**
     * Returns the run identifier.
     *
     * @return the identifier; never {@code null}
     */
    public String runId() {
        return runId;
    }

    /**
     * Returns the effective configuration.
     *
     * @return the configuration; never {@code null}
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * Requests cooperative cancellation from another thread. Idempotent.
     *
     * @param reason the cancellation reason; must not be {@code null}
     */
    public void requestCancellation(final String reason) {
        if (cancellationRequested) {
            return;
        }
        cancellationRequestReason = Objects.requireNonNull(reason, "reason is null");
        cancellationRequested = true;
        events.add(new CancelRun(cancellationRequestReason));
    }

    /**
     * Executes the plan and returns its immutable result.
     *
     * @return the result; never {@code null}
     * @throws org.paramixel.api.exception.ParamixelException if the run aborted because of an unrecoverable VM error
     */
    public Result run() {
        final var startedAt = Instant.now();
        final var threadCounter = new AtomicInteger();
        final var threadFactory = daemonFactory("paramixel-worker-" + shortId(runId) + "-", threadCounter);
        final var timerCounter = new AtomicInteger();
        workers = Executors.newFixedThreadPool(workerLimit, threadFactory);
        timers = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("paramixel-timer-" + shortId(runId) + "-", timerCounter));
        try {
            root = new OccurrenceState(this, plan.root(), null, Instant.now(), true);
            admit(root);
            loop();
        } finally {
            workers.shutdownNow();
            timers.shutdownNow();
        }
        final var result = buildResult(startedAt);
        if (fatal instanceof Error error) {
            throw error;
        }
        return result;
    }

    // --------------------------------------------------------------------------------------------
    // Scheduler loop
    // --------------------------------------------------------------------------------------------

    private void loop() {
        while (!finished) {
            dispatchReady();
            if (finished) {
                break;
            }
            if (runningJobs == 0
                    && ready.isEmpty()
                    && pendingTimers.get() == 0
                    && !hasPendingWaiters()
                    && events.isEmpty()) {
                // Nothing can make progress and nothing will post an event: a framework invariant is broken.
                recordRunFailure("SCHEDULER_STALLED", "Scheduler stalled with no runnable or waiting work", null);
                incomplete = true;
                abandon();
                break;
            }
            try {
                final var event = events.poll(WATCHDOG_MILLIS, TimeUnit.MILLISECONDS);
                if (event == null) {
                    if (runningJobs == 0 && ready.isEmpty() && pendingTimers.get() == 0) {
                        recordRunFailure(
                                "SCHEDULER_WATCHDOG",
                                "Scheduler made no progress for " + WATCHDOG_MILLIS + "ms with no running work",
                                null);
                        incomplete = true;
                        abandon();
                    }
                    continue;
                }
                handle(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordRunFailure("SCHEDULER_INTERRUPTED", "Engine thread was interrupted", e);
                incomplete = true;
                abandon();
                break;
            }
        }
        if (incomplete) {
            markRemainingTerminal();
        }
        finishListeners();
    }

    private void dispatchReady() {
        while (!finished && runningJobs < workerLimit && !ready.isEmpty()) {
            dispatch(ready.poll());
        }
    }

    private boolean hasPendingWaiters() {
        for (String pool : plan.poolNames()) {
            if (!pools.pending(pool).isEmpty()) {
                return true;
            }
        }
        for (String lock : plan.lockNames()) {
            if (!locks.pending(lock).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void dispatch(final Job job) {
        runningJobs++;
        final var state = job.state;
        workers.execute(() -> {
            JobOutcome outcome;
            final var thread = Thread.currentThread();
            state.activeThread = thread;
            state.activeJobs.incrementAndGet();
            Thread.interrupted();
            try {
                outcome = job.work.execute();
            } catch (Throwable t) {
                outcome = JobOutcome.error(t);
            } finally {
                state.activeThread = null;
                state.activeJobs.decrementAndGet();
                Thread.interrupted();
            }
            events.add(new JobCompleted(job, outcome));
        });
    }

    private void handle(final Object event) {
        if (event instanceof CancelRun cancel) {
            onRunCanceled(cancel.reason);
        } else if (event instanceof JobCompleted completed) {
            onJobCompleted(completed);
        } else if (event instanceof ResourceAvailable available) {
            onResourceAvailable(available);
        } else if (event instanceof ChildCompleted childCompleted) {
            onChildCompleted(childCompleted.child);
        } else if (event instanceof TimeoutFired timeout) {
            onTimeout(timeout);
        } else if (event instanceof GraceExpired graceExpired) {
            onGraceExpired(graceExpired);
        } else if (event instanceof RetryReady retryReady) {
            onRetryReady(retryReady);
        }
    }

    private void onJobCompleted(final JobCompleted completed) {
        runningJobs--;
        final var state = completed.job.state;
        state.outstandingJobs--;
        final var outcome = completed.outcome;
        if (outcome.error != null && UnrecoverableErrors.isUnrecoverable(outcome.error)) {
            fatal = outcome.error;
            recordRunFailure(
                    "FATAL_VM_ERROR", "Unrecoverable VM error in " + state.path + ": " + outcome.error, outcome.error);
            incomplete = true;
            abandon();
            return;
        }
        if (state.isTerminal()) {
            return;
        }
        switch (completed.job.kind) {
            case PREPARE -> onPrepared(state, outcome);
            case CONDITION -> onCondition(state, outcome);
            case BEFORE -> onBefore(state, completed.job.index, outcome);
            case BODY -> onBody(state, outcome);
            case AFTER -> onAfter(state, completed.job.index, outcome);
            case RELEASE -> onReleased(state, outcome);
        }
        dispatchReady();
    }

    private void onRunCanceled(final String reason) {
        if (finished || runCanceled) {
            return;
        }
        runCanceled = true;
        runCancellationReason = reason;
        publisher.warning("Run cancellation requested: " + reason);
        cancelSubtree(root, reason);
        if (root.isTerminal()) {
            finished = true;
            return;
        }
        if (activeJobCount(root) == 0) {
            skipNormalWork(root);
        } else {
            scheduleGrace(root);
        }
        dispatchReady();
    }

    // --------------------------------------------------------------------------------------------
    // Admission
    // --------------------------------------------------------------------------------------------

    private void admit(final OccurrenceState state) {
        if (state.isTerminal() || finished) {
            return;
        }
        if (state.isCanceled()) {
            markTerminal(state, Status.canceled(reasonOr(state, "canceled")), reasonOr(state, "canceled"));
            return;
        }
        if (failFast && state.parent != null && state.parent.compiled.failFastScope() && rootFailureSeen) {
            markTerminal(state, Status.skipped("failFast: a root task already failed"), "failFast");
            return;
        }
        if (state.parent != null && state.parent.compiled.mode() == ExecutionMode.PARALLEL && !state.permitHeld) {
            final var pool = state.parent.compiled.poolName();
            if (!pools.tryAcquire(pool)) {
                state.waiting = true;
                state.waitingFor = pool;
                pools.await(pool, state);
                return;
            }
            state.permitHeld = true;
            state.permitName = pool;
        }
        final var lockName = state.compiled.task().exclusiveLock().orElse(null);
        if (lockName != null && !state.lockHeld) {
            if (!locks.tryAcquire(lockName)) {
                state.waiting = true;
                state.waitingFor = lockName;
                locks.await(lockName, state);
                return;
            }
            state.lockHeld = true;
            state.lockName = lockName;
        }
        state.waiting = false;
        state.waitingFor = null;
        state.stage = OccurrenceState.Stage.ADMITTED;
        state.status = Status.RUNNING;
        state.admittedAt = Instant.now();
        state.admittedNanos = System.nanoTime();
        final var attempt = state.beginAttempt();
        emit(state, TaskPhase.ADMITTED, Status.RUNNING, attempt.index);
        scheduleTimeout(state);
        schedulePrepare(state);
    }

    private void releaseAdmission(final OccurrenceState state) {
        if (state.lockHeld) {
            state.lockHeld = false;
            final var name = state.lockName;
            locks.release(name);
            drainLock(name);
        }
        if (state.permitHeld) {
            state.permitHeld = false;
            final var name = state.permitName;
            pools.release(name);
            drainPool(name);
        }
    }

    private void drainPool(final String name) {
        while (pools.hasCapacity(name)) {
            if (!pools.tryAcquire(name)) {
                return;
            }
            final var waiter = pools.pollWaiter(name);
            if (waiter == null) {
                pools.release(name);
                return;
            }
            waiter.permitHeld = true;
            waiter.permitName = name;
            waiter.waiting = false;
            events.add(new ResourceAvailable(waiter));
        }
    }

    private void drainLock(final String name) {
        while (locks.hasCapacity(name)) {
            if (!locks.tryAcquire(name)) {
                return;
            }
            final var waiter = locks.pollWaiter(name);
            if (waiter == null) {
                locks.release(name);
                return;
            }
            waiter.lockHeld = true;
            waiter.lockName = name;
            waiter.waiting = false;
            events.add(new ResourceAvailable(waiter));
        }
    }

    // --------------------------------------------------------------------------------------------
    // Phases
    // --------------------------------------------------------------------------------------------

    private void schedulePrepare(final OccurrenceState state) {
        state.stage = OccurrenceState.Stage.PREPARING;
        submit(state, JobKind.PREPARE, 0, () -> prepare(state));
    }

    private JobOutcome prepare(final OccurrenceState state) throws Throwable {
        final var attempt = state.attempt;
        final var task = state.compiled.task();
        final var context = new ExecutionContextImpl(this, state, attempt.index);
        for (ResourceDeclaration declaration : task.resources()) {
            final var value = declaration.factory().create(context);
            if (value == null) {
                throw new IllegalStateException(
                        "Resource factory returned null for " + declaration.key() + " at " + state.path);
            }
            attempt.resources.put(declaration.key(), value);
            attempt.acquired.add(declaration);
        }
        final var factory = task.instanceFactory().orElse(null);
        if (factory != null) {
            final var instance = factory.create();
            if (instance == null) {
                throw new IllegalStateException("Primary instance factory returned null at " + state.path);
            }
            attempt.instance = instance;
        }
        return JobOutcome.ok();
    }

    private void onPrepared(final OccurrenceState state, final JobOutcome outcome) {
        final var attempt = state.attempt;
        if (outcome.error != null) {
            final var status = classify(outcome.error, state);
            attempt.fail("RESOURCES", messageFor(status, outcome.error, state), outcome.error);
            attempt.phases.add(
                    PhaseResult.of(TaskPhase.RESOURCES_ACQUIRED, "resources", status, List.of(lastFailure(attempt))));
            emit(state, TaskPhase.RESOURCES_ACQUIRED, status, attempt.index);
            releaseResources(state);
            return;
        }
        attempt.phases.add(PhaseResult.of(TaskPhase.RESOURCES_ACQUIRED, "resources", Status.PASSED, List.of()));
        emit(state, TaskPhase.RESOURCES_ACQUIRED, Status.PASSED, attempt.index);
        if (state.isCanceled()) {
            skipNormalWork(state);
            return;
        }
        final var condition = state.compiled.task().condition().orElse(null);
        if (condition != null) {
            state.stage = OccurrenceState.Stage.CONDITION;
            submit(
                    state,
                    JobKind.CONDITION,
                    0,
                    () -> JobOutcome.value(condition.test(new ExecutionContextImpl(this, state, attempt.index))));
            return;
        }
        startBefore(state);
    }

    private void onCondition(final OccurrenceState state, final JobOutcome outcome) {
        final var attempt = state.attempt;
        final var status = outcome.error != null
                ? classify(outcome.error, state)
                : (Boolean.TRUE.equals(outcome.value) ? Status.PASSED : Status.skipped("condition was false"));
        attempt.conditionStatus = status;
        if (outcome.error != null) {
            attempt.fail("CONDITION", messageFor(status, outcome.error, state), outcome.error);
        }
        attempt.phases.add(PhaseResult.of(
                TaskPhase.CONDITION,
                "condition",
                status,
                outcome.error == null ? List.of() : List.of(lastFailure(attempt))));
        emit(state, TaskPhase.CONDITION, status, attempt.index);
        if (!status.isPassed()) {
            state.reason = status.message().orElse("condition did not pass");
            skipNormalWork(state);
            return;
        }
        if (state.isCanceled()) {
            skipNormalWork(state);
            return;
        }
        startBefore(state);
    }

    private void startBefore(final OccurrenceState state) {
        state.stage = OccurrenceState.Stage.BEFORE;
        scheduleNextBefore(state);
    }

    private void scheduleNextBefore(final OccurrenceState state) {
        final var attempt = state.attempt;
        final var callbacks = state.compiled.task().before();
        if (attempt.beforeIndex >= callbacks.size()) {
            startBody(state);
            return;
        }
        final var index = attempt.beforeIndex++;
        final TaskCallback callback = callbacks.get(index);
        submit(state, JobKind.BEFORE, index, () -> {
            callback.execute(new ExecutionContextImpl(this, state, attempt.index));
            return JobOutcome.ok();
        });
    }

    private void onBefore(final OccurrenceState state, final int index, final JobOutcome outcome) {
        final var attempt = state.attempt;
        final var status = outcome.error == null ? Status.PASSED : classify(outcome.error, state);
        if (outcome.error != null) {
            attempt.fail("BEFORE[" + index + "]", messageFor(status, outcome.error, state), outcome.error);
        }
        attempt.phases.add(PhaseResult.of(
                TaskPhase.BEFORE,
                "before[" + index + "]",
                status,
                outcome.error == null ? List.of() : List.of(lastFailure(attempt))));
        emit(state, TaskPhase.BEFORE, status, attempt.index);
        if (!status.isPassed()) {
            attempt.beforeFailedAtIndex = index;
            final var remaining = state.compiled.task().before();
            for (var i = index + 1; i < remaining.size(); i++) {
                attempt.phases.add(PhaseResult.of(
                        TaskPhase.BEFORE,
                        "before[" + i + "]",
                        Status.skipped("a previous before callback did not pass"),
                        List.of()));
            }
            attempt.beforeIndex = remaining.size();
            skipChildren(state, "before did not pass");
            goAfter(state);
            return;
        }
        if (state.isCanceled()) {
            skipNormalWork(state);
            return;
        }
        scheduleNextBefore(state);
    }

    private void startBody(final OccurrenceState state) {
        final var body = state.compiled.task().body().orElse(null);
        if (body == null) {
            startChildren(state);
            return;
        }
        state.stage = OccurrenceState.Stage.BODY;
        final var attempt = state.attempt;
        submit(state, JobKind.BODY, 0, () -> {
            body.execute(new ExecutionContextImpl(this, state, attempt.index));
            return JobOutcome.ok();
        });
    }

    private void onBody(final OccurrenceState state, final JobOutcome outcome) {
        final var attempt = state.attempt;
        final var status = outcome.error == null ? Status.PASSED : classify(outcome.error, state);
        attempt.bodyStatus = status;
        if (outcome.error != null) {
            attempt.fail("BODY", messageFor(status, outcome.error, state), outcome.error);
        }
        attempt.phases.add(PhaseResult.of(
                TaskPhase.BODY, "body", status, outcome.error == null ? List.of() : List.of(lastFailure(attempt))));
        emit(state, TaskPhase.BODY, status, attempt.index);
        if (!status.isPassed()) {
            skipChildren(state, "body " + status.kind());
            goAfter(state);
            return;
        }
        if (state.isCanceled()) {
            skipNormalWork(state);
            return;
        }
        startChildren(state);
    }

    private void startChildren(final OccurrenceState state) {
        final var attempt = state.attempt;
        if (attempt.children.isEmpty()) {
            goAfter(state);
            return;
        }
        state.stage = OccurrenceState.Stage.CHILDREN;
        if (state.compiled.mode() == ExecutionMode.PARALLEL) {
            for (OccurrenceState child : attempt.children) {
                admit(child);
            }
            return;
        }
        admit(attempt.children.get(attempt.childCursor));
    }

    private void onChildCompleted(final OccurrenceState child) {
        final var parent = child.parent;
        if (parent == null || parent.isTerminal() || parent.stage != OccurrenceState.Stage.CHILDREN) {
            return;
        }
        final var attempt = parent.attempt;
        if (parent.compiled.failFastScope() && !child.status.isPassed()) {
            rootFailureSeen = true;
        }
        if (!child.status.isPassed()) {
            parent.status = Status.RUNNING;
        }
        if (parent.compiled.mode() == ExecutionMode.PARALLEL) {
            if (attempt.children.stream().allMatch(OccurrenceState::isTerminal)) {
                completeChildren(parent);
            }
            return;
        }
        attempt.childCursor++;
        if (!child.status.isPassed()) {
            if (parent.isCanceled()) {
                cancelRemaining(parent, reasonOr(parent, "parent canceled"));
                completeChildren(parent);
                return;
            }
            if (parent.compiled.task().dependent()) {
                skipRemaining(parent, "dependency not satisfied: " + child.name + " " + child.status.kind());
                completeChildren(parent);
                return;
            }
        }
        if (attempt.childCursor < attempt.children.size()) {
            admit(attempt.children.get(attempt.childCursor));
        } else {
            completeChildren(parent);
        }
    }

    private void completeChildren(final OccurrenceState parent) {
        final var attempt = parent.attempt;
        final var statuses = new ArrayList<Status>();
        for (OccurrenceState child : attempt.children) {
            statuses.add(child.status);
        }
        final var aggregate = Status.mostSevere(statuses);
        attempt.phases.add(PhaseResult.of(TaskPhase.CHILDREN, "children", aggregate, List.of()));
        emit(parent, TaskPhase.CHILDREN, aggregate, attempt.index);
        goAfter(parent);
    }

    private void skipChildren(final OccurrenceState state, final String reason) {
        final var attempt = state.attempt;
        if (attempt == null) {
            return;
        }
        while (attempt.childCursor < attempt.children.size()) {
            final var child = attempt.children.get(attempt.childCursor);
            attempt.childCursor++;
            if (!child.isTerminal()) {
                markTerminal(child, Status.skipped(reason), reason);
            }
        }
    }

    private void skipRemaining(final OccurrenceState parent, final String reason) {
        final var attempt = parent.attempt;
        for (int i = attempt.childCursor; i < attempt.children.size(); i++) {
            final var child = attempt.children.get(i);
            if (!child.isTerminal()) {
                markTerminal(child, Status.skipped(reason), reason);
            }
        }
        attempt.childCursor = attempt.children.size();
    }

    private void cancelRemaining(final OccurrenceState parent, final String reason) {
        final var attempt = parent.attempt;
        for (int i = attempt.childCursor; i < attempt.children.size(); i++) {
            final var child = attempt.children.get(i);
            if (!child.isTerminal()) {
                cancelSubtree(child, reason);
                markTerminal(child, Status.canceled(reason), reason);
            }
        }
        attempt.childCursor = attempt.children.size();
    }

    private void goAfter(final OccurrenceState state) {
        final var attempt = state.attempt;
        state.stage = OccurrenceState.Stage.AFTER;
        attempt.afterIndex = state.compiled.task().after().size() - 1;
        attempt.finalizationStartNanos = System.nanoTime();
        state.finalizationStartNanos = attempt.finalizationStartNanos;
        scheduleNextAfter(state);
    }

    private void scheduleNextAfter(final OccurrenceState state) {
        final var attempt = state.attempt;
        final var callbacks = state.compiled.task().after();
        if (attempt.afterIndex < 0) {
            releaseResources(state);
            return;
        }
        final var index = attempt.afterIndex--;
        final TaskCallback callback = callbacks.get(index);
        submit(state, JobKind.AFTER, index, () -> {
            callback.execute(new ExecutionContextImpl(this, state, attempt.index));
            return JobOutcome.ok();
        });
    }

    private void onAfter(final OccurrenceState state, final int index, final JobOutcome outcome) {
        final var attempt = state.attempt;
        final var status = outcome.error == null ? Status.PASSED : classify(outcome.error, state);
        if (outcome.error != null) {
            attempt.fail("AFTER[" + index + "]", messageFor(status, outcome.error, state), outcome.error);
        }
        attempt.phases.add(PhaseResult.of(
                TaskPhase.AFTER,
                "after[" + index + "]",
                status,
                outcome.error == null ? List.of() : List.of(lastFailure(attempt))));
        emit(state, TaskPhase.AFTER, status, attempt.index);
        scheduleNextAfter(state);
    }

    private void releaseResources(final OccurrenceState state) {
        final var attempt = state.attempt;
        if (attempt.acquired.isEmpty()) {
            finishAttempt(state);
            return;
        }
        state.stage = OccurrenceState.Stage.RELEASING;
        submit(state, JobKind.RELEASE, 0, () -> {
            final var errors = new ArrayList<Throwable>();
            final var names = new ArrayList<String>();
            final var context = new ExecutionContextImpl(this, state, attempt.index);
            for (int i = attempt.acquired.size() - 1; i >= 0; i--) {
                final var declaration = attempt.acquired.get(i);
                final var value = attempt.resources.get(declaration.key());
                final var releaser = declaration.releaser().orElse(null);
                if (releaser == null || value == null) {
                    continue;
                }
                try {
                    releaser.release(value, context);
                } catch (Throwable t) {
                    errors.add(t);
                    names.add(declaration.key().qualifiedName());
                }
            }
            return JobOutcome.errors(errors, names);
        });
    }

    private void onReleased(final OccurrenceState state, final JobOutcome outcome) {
        final var attempt = state.attempt;
        final var errors = outcome.errors;
        final var statuses = new ArrayList<Status>();
        for (int i = 0; i < errors.size(); i++) {
            final var error = errors.get(i);
            final var key = i < outcome.errorLabels.size() ? outcome.errorLabels.get(i) : "resource";
            attempt.fail(
                    "RESOURCE_RELEASE[" + key + "]",
                    "Failed to release resource " + key + ": " + describe(error),
                    error);
            statuses.add(classify(error, state));
        }
        final var status = statuses.isEmpty() ? Status.PASSED : Status.mostSevere(statuses);
        attempt.phases.add(PhaseResult.of(
                TaskPhase.RESOURCES_RELEASED,
                "resources",
                status,
                errors.isEmpty() ? List.of() : List.of(lastFailure(attempt))));
        emit(state, TaskPhase.RESOURCES_RELEASED, status, attempt.index);
        finishAttempt(state);
    }

    // --------------------------------------------------------------------------------------------
    // Attempts, retry, repeat
    // --------------------------------------------------------------------------------------------

    private void finishAttempt(final OccurrenceState state) {
        final var attempt = state.attempt;
        attempt.attemptStatus = attemptAggregate(state);
        attempt.completedAt = Instant.now();
        final var hasPolicy = state.compiled.task().retry().isPresent()
                || state.compiled.task().repeat().isPresent();
        if (hasPolicy) {
            final var node = attemptNode(state, attempt, attempt.attemptStatus);
            state.attemptNodes.add(node);
            state.attemptStatuses.add(attempt.attemptStatus);
        }
        if (finished) {
            return;
        }
        if (!state.isCanceled() && !state.timedOut) {
            final RepeatPolicy repeat = state.compiled.task().repeat().orElse(null);
            if (repeat != null && !repeat.stopsAfter(attempt.index, attempt.attemptStatus, contextOf(state))) {
                scheduleNextAttempt(state, repeat.delayBefore(attempt.index + 1));
                return;
            }
            final RetryPolicy retry = state.compiled.task().retry().orElse(null);
            if (retry != null
                    && retry.retries(attempt.attemptStatus.kind())
                    && retry.permitsAnotherAttempt(attempt.index)) {
                scheduleNextAttempt(state, retry.delayBefore(attempt.index + 1));
                return;
            }
        }
        terminalize(state, finalStatus(state));
    }

    private void scheduleNextAttempt(final OccurrenceState state, final Duration delay) {
        final var delayNanos = nanosSafely(delay);
        final var generation = state.graceGeneration;
        if (delayNanos <= 0) {
            events.add(new RetryReady(state, generation));
            return;
        }
        pendingTimers.incrementAndGet();
        timers.schedule(() -> events.add(new RetryReady(state, generation)), delayNanos, TimeUnit.NANOSECONDS);
    }

    private void onRetryReady(final RetryReady ready) {
        pendingTimers.decrementAndGet();
        final var state = ready.state;
        if (state.isTerminal() || finished) {
            return;
        }
        if (state.isCanceled()) {
            terminalize(state, finalStatus(state));
            return;
        }
        state.beginAttempt();
        scheduleTimeout(state);
        schedulePrepare(state);
        dispatchReady();
    }

    private Status finalStatus(final OccurrenceState state) {
        final var attempt = state.attempt;
        final var aggregate = attempt == null ? statusOrCanceled(state) : attemptAggregate(state);
        if (state.timedOut) {
            final var timeout = Status.failed("timed out after "
                    + state.compiled.task().timeout().map(Duration::toString).orElse("the configured deadline"));
            return Status.mostSevere(List.of(timeout, aggregate));
        }
        if (state.isCanceled()) {
            return Status.mostSevere(List.of(Status.canceled(reasonOr(state, "canceled")), aggregate));
        }
        final var repeat = state.compiled.task().repeat().orElse(null);
        if (repeat != null && !state.attemptStatuses.isEmpty()) {
            if (repeat.untilPassed()) {
                final var passed = state.attemptStatuses.stream().anyMatch(Status::isPassed);
                return passed ? Status.PASSED : Status.mostSevere(state.attemptStatuses);
            }
            if (repeat.until() == null) {
                return Status.mostSevere(state.attemptStatuses);
            }
            return state.attemptStatuses.get(state.attemptStatuses.size() - 1);
        }
        final var retry = state.compiled.task().retry().orElse(null);
        if (retry != null && !state.attemptStatuses.isEmpty()) {
            return state.attemptStatuses.get(state.attemptStatuses.size() - 1);
        }
        return aggregate;
    }

    private Status statusOrCanceled(final OccurrenceState state) {
        return state.isCanceled() ? Status.canceled(reasonOr(state, "canceled")) : Status.PASSED;
    }

    private Status attemptAggregate(final OccurrenceState state) {
        final var attempt = state.attempt;
        if (attempt == null) {
            return statusOrCanceled(state);
        }
        final var statuses = new ArrayList<Status>();
        for (PhaseResult phase : attempt.phases) {
            statuses.add(phase.status());
        }
        if (attempt.bodyStatus != null) {
            statuses.add(attempt.bodyStatus);
        }
        for (OccurrenceState child : attempt.children) {
            statuses.add(child.status);
        }
        if (state.timedOut) {
            statuses.add(Status.failed("timed out"));
        }
        final var aggregate = Status.mostSevere(statuses);
        if (aggregate.isPassed() && state.isCanceled()) {
            return Status.canceled(reasonOr(state, "canceled"));
        }
        return aggregate;
    }

    private void terminalize(final OccurrenceState state, final Status status) {
        if (state.isTerminal()) {
            return;
        }
        state.status = status;
        state.stage = OccurrenceState.Stage.TERMINAL;
        state.completedAt = Instant.now();
        releaseAdmission(state);
        emit(state, TaskPhase.TERMINAL, status, Math.max(1, state.attemptCount));
        if (state.parent == null) {
            finished = true;
            return;
        }
        events.add(new ChildCompleted(state));
    }

    private void markTerminal(final OccurrenceState state, final Status status, final String reason) {
        if (state.isTerminal()) {
            return;
        }
        if (state.waiting) {
            if (state.waitingFor != null) {
                pools.cancelWait(state.waitingFor, state);
                locks.cancelWait(state.waitingFor, state);
            }
            state.waiting = false;
        }
        state.reason = reason;
        state.status = status;
        state.stage = OccurrenceState.Stage.TERMINAL;
        state.completedAt = Instant.now();
        final var attempt = state.attempt;
        if (attempt != null) {
            for (OccurrenceState child : attempt.children) {
                if (!child.isTerminal()) {
                    markTerminal(child, status.isCanceled() ? Status.canceled(reason) : Status.skipped(reason), reason);
                }
            }
        }
        releaseAdmission(state);
        emit(state, TaskPhase.TERMINAL, status, Math.max(1, state.attemptCount));
        if (state.parent == null) {
            finished = true;
        } else {
            events.add(new ChildCompleted(state));
        }
    }

    // --------------------------------------------------------------------------------------------
    // Timeout and cancellation
    // --------------------------------------------------------------------------------------------

    private void scheduleTimeout(final OccurrenceState state) {
        final var timeout = state.compiled.task().timeout().orElse(null);
        if (timeout == null) {
            return;
        }
        final var generation = ++state.graceGeneration;
        pendingTimers.incrementAndGet();
        timers.schedule(
                () -> events.add(new TimeoutFired(state, generation, timeout)),
                nanosSafely(timeout),
                TimeUnit.NANOSECONDS);
    }

    private void onTimeout(final TimeoutFired timeout) {
        pendingTimers.decrementAndGet();
        final var state = timeout.state;
        if (state.isTerminal() || finished || state.graceGeneration != timeout.generation) {
            return;
        }
        state.timedOut = true;
        final var message = "timed out after " + timeout.timeout;
        if (state.attempt != null) {
            state.attempt.fail("TIMEOUT", message, null);
            state.attempt.phases.add(PhaseResult.of(
                    TaskPhase.TIMEOUT, "timeout", Status.failed(message), List.of(lastFailure(state.attempt))));
        }
        state.reason = message;
        emit(state, TaskPhase.TIMEOUT, Status.failed(message), Math.max(1, state.attemptCount));
        cancelSubtree(state, message);
        if (state.isTerminal()) {
            return;
        }
        if (activeJobCount(state) == 0) {
            skipNormalWork(state);
        } else {
            scheduleGrace(state);
        }
        dispatchReady();
    }

    private void scheduleGrace(final OccurrenceState state) {
        final var generation = ++state.graceGeneration;
        pendingTimers.incrementAndGet();
        timers.schedule(
                () -> events.add(new GraceExpired(state, generation)), nanosSafely(grace), TimeUnit.NANOSECONDS);
    }

    private void onGraceExpired(final GraceExpired expired) {
        pendingTimers.decrementAndGet();
        final var state = expired.state;
        if (finished || state.isTerminal() || state.graceGeneration != expired.generation) {
            return;
        }
        if (activeJobCount(state) == 0) {
            return;
        }
        recordRunFailure(
                "NON_QUIESCENT",
                "Occurrence " + state.path + " did not quiesce within " + grace
                        + " after cancellation; work may still be running and finalizers were withheld",
                null);
        incomplete = true;
        abandon();
    }

    private void onResourceAvailable(final ResourceAvailable available) {
        admit(available.state);
        dispatchReady();
    }

    private void cancelSubtree(final OccurrenceState state, final String reason) {
        if (state.isTerminal()) {
            return;
        }
        state.canceled = true;
        if (state.cancellationReason == null) {
            state.cancellationReason = reason;
        }
        final var thread = state.activeThread;
        if (thread != null) {
            thread.interrupt();
        }
        if (state.waiting) {
            if (state.waitingFor != null) {
                pools.cancelWait(state.waitingFor, state);
                locks.cancelWait(state.waitingFor, state);
            }
            state.waiting = false;
            markTerminal(state, Status.canceled(reason), reason);
            return;
        }
        if (state.attempt != null) {
            for (OccurrenceState child : state.attempt.children) {
                if (!child.isTerminal()) {
                    cancelSubtree(child, reason);
                }
            }
        }
    }

    private void skipNormalWork(final OccurrenceState state) {
        if (state.isTerminal()) {
            return;
        }
        final var attempt = state.attempt;
        if (attempt != null) {
            for (OccurrenceState child : attempt.children) {
                if (!child.isTerminal()) {
                    if (state.isCanceled() || state.timedOut) {
                        cancelSubtree(child, reasonOr(state, "canceled"));
                        markTerminal(child, Status.canceled(reasonOr(state, "canceled")), reasonOr(state, "canceled"));
                    } else {
                        markTerminal(child, Status.skipped("parent normal work did not complete"), "parent skipped");
                    }
                }
            }
        }
        if (attempt == null) {
            terminalize(state, finalStatus(state));
            return;
        }
        switch (state.stage) {
            case NEW, ADMITTED, PREPARING, CONDITION, BEFORE, BODY, CHILDREN -> goAfter(state);
            case AFTER, RELEASING, TERMINAL -> {
                // already finalizing
            }
        }
    }

    private int activeJobCount(final OccurrenceState state) {
        var total = state.outstandingJobs;
        if (state.attempt != null) {
            for (OccurrenceState child : state.attempt.children) {
                total += activeJobCount(child);
            }
        }
        return total;
    }

    private void abandon() {
        finished = true;
        publishDiagnostics();
    }

    private void markRemainingTerminal() {
        if (root != null) {
            markIncomplete(root);
        }
    }

    private void markIncomplete(final OccurrenceState state) {
        if (!state.isTerminal()) {
            state.reason = state.timedOut
                    ? "timeout expired and the callback did not quiesce; finalizers were withheld"
                    : "run reported non-quiescent; remaining work was not completed";
            state.status = state.timedOut ? Status.failed(state.reason) : Status.canceled(state.reason);
            state.stage = OccurrenceState.Stage.TERMINAL;
            state.completedAt = Instant.now();
            if (state.attempt != null) {
                for (OccurrenceState child : state.attempt.children) {
                    markIncomplete(child);
                }
            }
            emit(state, TaskPhase.TERMINAL, state.status, Math.max(1, state.attemptCount));
        }
    }

    // --------------------------------------------------------------------------------------------
    // Job submission helpers
    // --------------------------------------------------------------------------------------------

    private void submit(final OccurrenceState state, final JobKind kind, final int index, final JobWork work) {
        if (state.isTerminal() || finished) {
            return;
        }
        state.outstandingJobs++;
        ready.add(new Job(state, kind, index, work, jobSequence++, state.compiled.preOrderIndex()));
    }

    private Status classify(final Throwable throwable, final OccurrenceState state) {
        final var unwrapped = Throwables.unwrap(throwable);
        if (unwrapped == null) {
            return Status.PASSED;
        }
        UnrecoverableErrors.rethrowIfUnrecoverable(unwrapped);
        if (state.timedOut) {
            return Status.failed("timed out", unwrapped);
        }
        if (unwrapped instanceof AbortException abort) {
            return Status.aborted(abort.getMessage(), unwrapped);
        }
        if (unwrapped instanceof SkipException skip) {
            return Status.skipped(skip.getMessage(), unwrapped);
        }
        if (state.isCanceled()) {
            return Status.canceled(reasonOr(state, "canceled"), unwrapped);
        }
        return Status.failed(describe(unwrapped), unwrapped);
    }

    private static String describe(final Throwable throwable) {
        final var message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName()
                : throwable.getClass().getSimpleName() + ": " + message;
    }

    private String messageFor(final Status status, final Throwable throwable, final OccurrenceState state) {
        return status.message().orElse(describe(throwable) + " at " + state.path);
    }

    private static Failure lastFailure(final OccurrenceState.Attempt attempt) {
        return attempt.failures.isEmpty()
                ? Failure.of("PHASE", "no failure recorded", null, false)
                : attempt.failures.get(attempt.failures.size() - 1);
    }

    private String reasonOr(final OccurrenceState state, final String fallback) {
        final var reason = state.cancellationReason();
        return reason == null ? fallback : reason;
    }

    private ExecutionContextImpl contextOf(final OccurrenceState state) {
        return new ExecutionContextImpl(this, state, Math.max(1, state.attemptCount));
    }

    // --------------------------------------------------------------------------------------------
    // Events and results
    // --------------------------------------------------------------------------------------------

    private void emit(final OccurrenceState state, final TaskPhase phase, final Status status, final int attempt) {
        eventSequence++;
        final var now = Instant.now();
        final Duration elapsed;
        if (phase == TaskPhase.TERMINAL && state.admittedAt != null) {
            elapsed = Duration.between(state.admittedAt, state.completedAt == null ? now : state.completedAt);
        } else if (phase == TaskPhase.TIMEOUT && state.admittedAt != null) {
            elapsed = Duration.between(state.admittedAt, now);
        } else {
            elapsed = null;
        }
        publisher.publish(new org.paramixel.api.TaskEvent(
                eventSequence,
                runId,
                state.id,
                state.parent == null ? null : state.parent.id,
                state.name,
                state.path,
                phase,
                attempt,
                status,
                now,
                elapsed));
    }

    private void recordRunFailure(final String phase, final String message, final Throwable throwable) {
        runFailures.add(Failure.of(phase, message, throwable, runFailures.isEmpty()));
        diagnostics.add(phase + ": " + message);
    }

    private void publishDiagnostics() {
        for (String diagnostic : List.copyOf(diagnostics)) {
            publisher.diagnostic(diagnostic);
        }
        diagnostics.clear();
    }

    private void finishListeners() {
        publishDiagnostics();
    }

    private Result buildResult(final Instant startedAt) {
        final var rootResult = root == null ? null : toResult(root);
        final var status = rootResult == null ? Status.skipped("nothing executed") : rootResult.status();
        final var quiescent = !incomplete;
        final int exitCode = exitCodeFor(status, quiescent);
        final var counters = new int[8];
        if (rootResult != null) {
            countStates(root, counters);
        }
        final var builder = Result.builder()
                .runId(runId)
                .status(status, exitCode, quiescent)
                .timings(startedAt, Instant.now())
                .root(rootResult)
                .failures(List.copyOf(runFailures))
                .diagnostics(List.copyOf(diagnostics))
                .configurationSummary(configurationSummary())
                .shuffleSeed(discoveryShuffleSeed)
                .counters(
                        counters[0],
                        counters[1],
                        counters[2],
                        counters[3],
                        counters[4],
                        counters[5],
                        counters[6],
                        counters[7]);
        return builder.build();
    }

    private int exitCodeFor(final Status status, final boolean quiescent) {
        if (!quiescent) {
            return 1;
        }
        return switch (status.kind()) {
            case PASSED -> 0;
            case SKIPPED -> failureOnSkip ? 1 : 0;
            case ABORTED -> failureOnAbort ? 1 : 0;
            case FAILED, CANCELED -> 1;
            case PENDING, RUNNING -> 1;
        };
    }

    private Map<String, String> configurationSummary() {
        final var summary = new LinkedHashMap<String, String>();
        for (String key : configuration.keySet()) {
            if (!key.startsWith("paramixel.")) {
                continue;
            }
            configuration.getString(key).ifPresent(value -> summary.put(key, redact(key, value)));
        }
        final var poolSummary = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Integer> entry : plan.poolCapacities().entrySet()) {
            poolSummary.put(entry.getKey(), Integer.toString(entry.getValue()));
        }
        if (!poolSummary.isEmpty()) {
            summary.put("pools", poolSummary.toString());
        }
        return summary;
    }

    private static String redact(final String key, final String value) {
        final var lower = key.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("secret")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("apikey")
                || lower.contains("api-key")) {
            return "***";
        }
        return value;
    }

    /**
     * Counts logical occurrences once each.
     *
     * <p>Counters are computed from the runtime occurrence tree rather than the result tree, because the result tree
     * intentionally retains every historical attempt. A retried occurrence therefore contributes one occurrence and
     * one status (its final aggregate), plus one attempt per attempt node; the failed inner steps of an earlier attempt
     * stay visible as {@code ATTEMPT} nodes but are not counted as separate failed occurrences. Counting this way keeps
     * the status counters summing to the occurrence count and prevents a successful retry from inflating failure
     * metrics.
     */
    private void countStates(final OccurrenceState state, final int[] counters) {
        counters[0]++;
        switch (state.status.kind()) {
            case PASSED -> counters[2]++;
            case FAILED -> counters[3]++;
            case SKIPPED -> counters[4]++;
            case ABORTED -> counters[5]++;
            case CANCELED -> counters[6]++;
            default -> {
                // in-flight kinds do not occur in a finished result
            }
        }
        if (state.timedOut) {
            counters[7]++;
        }
        counters[1] += state.attemptNodes.size();
        final var attempt = state.attempt;
        if (attempt != null) {
            for (OccurrenceState child : attempt.children) {
                countStates(child, counters);
            }
        }
    }

    private TaskResult toResult(final OccurrenceState state) {
        final var attempt = state.attempt;
        final var builder = TaskResult.builder()
                .identity(
                        state.id,
                        state.compiled.definitionId(),
                        state.parent == null ? null : state.parent.id,
                        state.name,
                        state.path)
                .tags(state.compiled.task().tags())
                .attributes(
                        state.compiled.mode(),
                        state.compiled.poolName(),
                        state.compiled.task().exclusiveLock().orElse(null),
                        state.compiled.shuffleSeed(),
                        state.compiled.executionOrder())
                .status(state.status, attempt == null ? null : attempt.bodyStatus)
                .reason(state.reason)
                .timedOut(state.timedOut)
                .timings(
                        state.queuedAt,
                        state.admittedAt,
                        state.startedAt,
                        state.completedAt,
                        durationBetween(state.queuedAt, state.admittedAt),
                        state.admittedNanos == 0
                                ? null
                                : durationNanos(state.admittedNanos, state.finalizationStartNanos),
                        state.finalizationStartNanos == 0 ? null : durationNanos(state.finalizationStartNanos, 0));
        if (!state.attemptNodes.isEmpty()) {
            builder.children(state.attemptNodes);
        } else if (attempt != null) {
            builder.children(resultsOf(attempt.children));
        }
        if (attempt != null) {
            builder.phases(attempt.phases).failures(attempt.failures);
        }
        return builder.build();
    }

    private TaskResult attemptNode(
            final OccurrenceState state, final OccurrenceState.Attempt attempt, final Status status) {
        return TaskResult.builder()
                .kind(TaskResult.Kind.ATTEMPT)
                .attemptIndex(attempt.index)
                .identity(
                        state.id + "#" + attempt.index,
                        state.compiled.definitionId(),
                        state.parent == null ? null : state.parent.id,
                        state.name,
                        state.path)
                .tags(state.compiled.task().tags())
                .attributes(
                        state.compiled.mode(),
                        state.compiled.poolName(),
                        state.compiled.task().exclusiveLock().orElse(null),
                        state.compiled.shuffleSeed(),
                        state.compiled.executionOrder())
                .status(status, attempt.bodyStatus)
                .reason(state.attemptCount > attempt.index ? null : state.reason)
                .timedOut(state.timedOut)
                .phases(attempt.phases)
                .children(resultsOf(attempt.children))
                .failures(attempt.failures)
                .timings(
                        state.queuedAt,
                        state.admittedAt,
                        attempt.startedAt,
                        attempt.completedAt,
                        null,
                        attempt.startNanos == 0
                                ? null
                                : durationNanos(attempt.startNanos, attempt.finalizationStartNanos),
                        attempt.finalizationStartNanos == 0 ? null : durationNanos(attempt.finalizationStartNanos, 0))
                .build();
    }

    private List<TaskResult> resultsOf(final List<OccurrenceState> children) {
        final var results = new ArrayList<TaskResult>(children.size());
        for (OccurrenceState child : children) {
            results.add(toResult(child));
        }
        return results;
    }

    private static Duration durationBetween(final Instant from, final Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return Duration.between(from, to);
    }

    private static Duration durationNanos(final long fromNanos, final long toNanosOrZero) {
        final var end = toNanosOrZero == 0 ? System.nanoTime() : toNanosOrZero;
        return Duration.ofNanos(Math.max(0, end - fromNanos));
    }

    private static long nanosSafely(final Duration duration) {
        try {
            return Math.max(0, duration.toNanos());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static ThreadFactory daemonFactory(final String prefix, final AtomicInteger counter) {
        return runnable -> {
            final var thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String shortId(final String runId) {
        return runId.length() <= 8 ? runId : runId.substring(0, 8);
    }

    // --------------------------------------------------------------------------------------------
    // Internal events and jobs
    // --------------------------------------------------------------------------------------------

    private enum JobKind {
        PREPARE,
        CONDITION,
        BEFORE,
        BODY,
        AFTER,
        RELEASE
    }

    @FunctionalInterface
    private interface JobWork {
        JobOutcome execute() throws Throwable;
    }

    private record Job(OccurrenceState state, JobKind kind, int index, JobWork work, long sequence, int priority) {}

    private record JobCompleted(Job job, JobOutcome outcome) {}

    private record ChildCompleted(OccurrenceState child) {}

    private record ResourceAvailable(OccurrenceState state) {}

    private record TimeoutFired(OccurrenceState state, int generation, Duration timeout) {}

    private record GraceExpired(OccurrenceState state, int generation) {}

    private record RetryReady(OccurrenceState state, int generation) {}

    private record CancelRun(String reason) {}

    private static final class JobOutcome {

        private static final JobOutcome OK = new JobOutcome(null, null, List.of(), List.of());

        private final Throwable error;
        private final Object value;
        private final List<Throwable> errors;
        private final List<String> errorLabels;

        private JobOutcome(
                final Throwable error,
                final Object value,
                final List<Throwable> errors,
                final List<String> errorLabels) {
            this.error = error;
            this.value = value;
            this.errors = errors;
            this.errorLabels = errorLabels;
        }

        static JobOutcome ok() {
            return OK;
        }

        static JobOutcome value(final Object value) {
            return new JobOutcome(null, value, List.of(), List.of());
        }

        static JobOutcome error(final Throwable error) {
            return new JobOutcome(error, null, List.of(), List.of());
        }

        static JobOutcome errors(final List<Throwable> errors, final List<String> labels) {
            return new JobOutcome(null, null, List.copyOf(errors), List.copyOf(labels));
        }
    }
}
