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

package nonapi.org.paramixel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorOrder;
import nonapi.org.paramixel.action.ExecutionNode;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.listener.Listeners;
import nonapi.org.paramixel.support.BackoffDelay;
import nonapi.org.paramixel.support.StackTracePruner;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.exception.FailException;

/**
 * Bounded priority scheduler for Paramixel action execution.
 *
 * <p>Execution is backed by one fixed-size worker pool bounded by
 * {@code paramixel.parallelism}. Terminal action execution is additionally bounded by permits so only
 * leaf actions consume global work capacity.
 *
 * <p>The global ready queue is ordered by the compiled structural order of the descriptor tree
 * ({@link DescriptorOrder}), yielding deterministic DFS-like subtree preference (continuations
 * before lateral siblings) while preserving global and local parallelism caps.
 *
 * <p>Coordinator threads that block on a child future via {@link #managedJoin(CompletableFuture)}
 * work-steal from the ready queue while waiting, parking with a bounded backoff when the queue is
 * momentarily empty.
 */
@SuppressWarnings("removal")
public final class Scheduler implements AutoCloseable {

    /**
     * Callback interface for lifecycle events during scheduled action execution.
     *
     * <p>Implementations are invoked by the scheduler at defined points during admission, execution
     * start, and execution completion. Callbacks are invoked on the scheduler worker thread that
     * executes the action.
     */
    public interface ExecutionCallback {

        /**
         * Called when the action has been admitted into the scheduler's running set.
         *
         * <p>This is invoked exactly once per scheduled action, before execution begins. The default
         * implementation does nothing.
         */
        default void onAdmitted() {
            // Intentionally empty
        }

        /**
         * Called just before the action's {@code perform} method is invoked.
         */
        void onExecutionStart();

        /**
         * Called after the action's execution has completed, regardless of success or failure.
         *
         * @param error the throwable that caused execution failure, or {@code null} if execution
         *     succeeded
         */
        void onExecutionComplete(Throwable error);
    }

    private static final String THREAD_NAME = "paramixel-scheduler";
    private static final long THREAD_KEEP_ALIVE_SECONDS = 30L;
    private static final long EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 5L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1_024;
    private static final long MANAGED_JOIN_BACKOFF_FLOOR_NANOS = 1_000L; // 1 microsecond
    /** Park interval for a thread waiting on leaf capacity outside the admission path. */
    private static final long LEAF_CAPACITY_PARK_NANOS = 1_000L; // 1 microsecond

    private static final long MANAGED_JOIN_BACKOFF_CEILING_NANOS = 1_000_000_000L; // 1 second
    private static final double MANAGED_JOIN_BACKOFF_GROWTH_FACTOR = 4.0;
    private static final long ISOLATION_JOIN_PARK_CEILING_NANOS = 10_000_000L; // 10 ms

    /**
     * Maximum nesting of continuations executed inline. Bounds stack depth when a continuation
     * synchronously executes skipped or aborted descendants, each of which may complete inline in
     * turn.
     */
    private static final int INLINE_CONTINUATION_MAX_DEPTH = 16;

    private static final Comparator<Runnable> PRIORITY_COMPARATOR = (left, right) -> {
        if (left instanceof ScheduledTask leftTask && right instanceof ScheduledTask rightTask) {
            // Ranks are compiled per tree, so they are only comparable within one compilation.
            // A scheduler driving more than one root tree therefore falls back to compilation
            // order, which keeps the total order deterministic across trees.
            var treeComparison = Long.compare(leftTask.orderTreeId(), rightTask.orderTreeId());
            if (treeComparison != 0) {
                return treeComparison;
            }
            var rankComparison = Long.compare(leftTask.orderRank(), rightTask.orderRank());
            return rankComparison != 0 ? rankComparison : Long.compare(leftTask.sequence(), rightTask.sequence());
        }
        throw new ClassCastException("PRIORITY_COMPARATOR requires PrioritizedTask or ContinuationTask, got: "
                + (left != null ? left.getClass().getName() : "null")
                + " and "
                + (right != null ? right.getClass().getName() : "null"));
    };

    /**
     * Total order over queued descriptor tasks: compiled structural rank, then submission sequence.
     *
     * <p>Deferred-work queues (isolation deferrals and relinquished ready tasks) use this order so
     * that depth-first preference (R3) survives deferral, not just the ready queue.
     */
    private static final Comparator<PrioritizedTask> TASK_ORDER =
            (left, right) -> PRIORITY_COMPARATOR.compare(left, right);

    private final int queueCapacity;
    private final Semaphore queuePermits;
    private final Semaphore leafPermits;
    private final int parallelism;
    /**
     * Maximum number of leaf action bodies running at the same time across the whole tree.
     *
     * <p>Independent of the thread pool size: effective global concurrency is
     * {@code min(parallelism, concurrencyCap)}. {@link Integer#MAX_VALUE} means unlimited, so the
     * thread pool is the only global bound.
     */
    private final int concurrencyCap;
    /**
     * Effective global leaf concurrency: {@code min(parallelism, concurrencyCap)}.
     */
    private final int effectiveLeafConcurrency;
    /**
     * Serializes leaf-capacity reservations so that starting a leaf is atomic with respect to every
     * capped ancestor and the global cap. Also guards {@link #cappedNodes}, {@link #leafCapChains},
     * {@link #gatedLeaves}, and {@link #admittedLeafBodies}.
     */
    private final ReentrantLock leafGateLock = new ReentrantLock();
    /**
     * Running-leaf counters for capped nodes, keyed by the capped descriptor.
     *
     * <p>Guarded by {@link #leafGateLock}.
     */
    private final IdentityHashMap<MutableDescriptor, CapState> cappedNodes = new IdentityHashMap<>();
    /**
     * Cached capped-ancestor chains, keyed by leaf descriptor.
     *
     * <p>Guarded by {@link #leafGateLock}.
     */
    private final IdentityHashMap<MutableDescriptor, List<CapState>> leafCapChains = new IdentityHashMap<>();
    /**
     * Leaves that have been admitted but are held back because the global cap or a capped ancestor
     * is at its limit. Ordered by compiled rank so the fill pass takes the highest-priority
     * candidate first, exactly like the ready queue.
     *
     * <p>Guarded by {@link #leafGateLock}.
     */
    private final PriorityQueue<PrioritizedTask> gatedLeaves = new PriorityQueue<>(TASK_ORDER);
    /**
     * Number of leaf bodies holding a capacity reservation: running, or reserved and about to run.
     *
     * <p>Guarded by {@link #leafGateLock}.
     */
    private int admittedLeafBodies;
    /**
     * Coordination descriptors that are currently executing their admission step, keyed by compiled
     * order.
     *
     * <p>A leaf is not promoted out of the gated ready set while an earlier-ranked coordination
     * descriptor is still expanding, because that subtree may be about to make an earlier leaf
     * ready. Without this, a later sibling's leaf can take the only slot during the hand-off that
     * expands the earlier sibling, which inverts depth-first order at low thread counts. The wait is
     * bounded by one admission step: the entry is removed as soon as the descriptor parks or
     * completes, and removing it triggers a fill pass.
     */
    private final java.util.concurrent.ConcurrentSkipListSet<ExpansionKey> expandingCoordination =
            new java.util.concurrent.ConcurrentSkipListSet<>();
    /**
     * The innermost coordination descriptor currently expanding on this thread, so that inline leaf
     * execution can suspend it: a descriptor running a child body inline is no longer expanding, and
     * holding its entry would idle later siblings for the whole duration of that body.
     */
    private final ThreadLocal<ExpansionKey> currentExpansion = new ThreadLocal<>();
    /**
     * Coordination descriptor tasks that are queued and have not started yet, keyed by compiled
     * order.
     *
     * <p>Part of the same depth-first guard as {@link #expandingCoordination}: a queued coordination
     * task may still make an earlier leaf ready, so a later sibling's leaf must not take the last
     * slot ahead of it. A queued coordination task is never cap-blocked, so the wait it imposes is
     * bounded by one hand-off.
     */
    private final java.util.concurrent.ConcurrentSkipListSet<ExpansionKey> pendingCoordination =
            new java.util.concurrent.ConcurrentSkipListSet<>();

    private final boolean allowCoreThreadTimeout;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService delayExecutor;
    private final ConcurrentHashMap<ExecutionNode, ScheduledFuture<?>> delayedContinuations = new ConcurrentHashMap<>();
    private final AtomicInteger workerThreadCounter = new AtomicInteger();
    private final AtomicInteger runningLeafCounter = new AtomicInteger();
    private final AtomicLong taskSequence = new AtomicLong(0);
    private final ThreadLocal<Boolean> schedulerWorker = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Integer> inlineContinuationDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Integer> managedJoinDepth = ThreadLocal.withInitial(() -> 0);

    /**
     * Threads currently parked in {@link #managedJoin}. Waking them when work becomes available
     * bounds hand-off latency by the enqueue path instead of by the park backoff ceiling (up to
     * one second, or ten milliseconds while isolation frames are held).
     */
    private final Set<Thread> parkedWaiters = ConcurrentHashMap.newKeySet();

    private final AtomicLong parkedWaiterWakeupCounter = new AtomicLong();
    private final AtomicLong inlineChildExecutionCounter = new AtomicLong();

    private final AtomicLong inlineContinuationCounter = new AtomicLong();
    private final ThreadLocal<ArrayList<IsolationFrame>> isolationFrames = ThreadLocal.withInitial(ArrayList::new);

    /**
     * Serializes capacity bookkeeping: the admission waiter registry and the parked ready tasks
     * FIFO. Critical sections are short; dispatch and retry work happens outside the monitor.
     */
    private final Object capacityMonitor = new Object();

    /**
     * Execution nodes whose internal child admission was deferred because ready capacity was
     * exhausted. Each registered node is woken at most once per deferred admission; the node
     * re-registers when its retry defers again.
     */
    private final Set<ExecutionNode> admissionWaiters = new LinkedHashSet<>();

    /**
     * Relinquished queued initial tasks waiting to be redispatched when capacity becomes
     * available. A relinquished task has released its queue permit without executing or
     * terminalizing; it is redispatched by re-acquiring a permit.
     */
    private final Queue<PrioritizedTask> parkedReadyTasks = new PriorityQueue<>(TASK_ORDER);

    /**
     * Result of an internal child admission attempt.
     */
    enum ChildAdmissionResult {
        /** The child was dispatched to the executor; a completion event will follow. */
        DISPATCHED,
        /** Ready capacity was exhausted; the child was left unscheduled and the parent node is
         * registered for a capacity wakeup. */
        DEFERRED,
        /** The scheduler is closing or the child was already terminal; the child was
         * terminalized and {@code childCompleted} was published. */
        REJECTED
    }

    /**
     * A task submitted to this scheduler's priority queue.
     *
     * <p>Every queued task exposes the compiled order rank, the identity of the compilation that
     * produced it, the submission sequence, and the descriptor it advances. Modeling the four
     * properties as one type keeps the priority comparator and the work-stealing eligibility check
     * free of per-implementation type tests.
     */
    private interface ScheduledTask extends Runnable {

        /**
         * Returns the identity of the compilation that produced {@link #orderRank()}.
         *
         * @return the compilation identity
         */
        long orderTreeId();

        /**
         * Returns the compiled structural rank of the descriptor this task advances.
         *
         * <p>Ranks are tree-local and totally ordered: a lower rank is served first.
         *
         * @return the order rank, always non-negative
         */
        long orderRank();

        /**
         * Returns the submission sequence used to break ordering-key ties.
         *
         * @return the submission sequence
         */
        long sequence();

        /**
         * Returns the descriptor this task advances.
         *
         * @return the descriptor; never {@code null}
         */
        MutableDescriptor descriptor();
    }

    /**
     * Shared work queue for continuations during executor shutdown.
     *
     * <p>During shutdown, continuations cannot be submitted to the executor (it rejects new
     * tasks). Instead they are added to this queue. {@link #managedJoin} polls this queue while
     * waiting for a future, ensuring that deferred descriptors complete even when the executor
     * is shut down. This avoids recursive inline continuation execution and enables cross-thread
     * continuation processing: a continuation added by Thread-B can be processed by Thread-A's
     * {@code managedJoin}.
     */
    private final ConcurrentLinkedQueue<ExecutionNode> shutdownDrain = new ConcurrentLinkedQueue<>();

    /**
     * Shared work queue for admission retries during executor shutdown.
     *
     * <p>Admission retries (capacity wakeups) cannot be submitted to the executor during
     * shutdown, so they are added to this queue. {@link #managedJoin} polls this queue while
     * waiting for a future, ensuring that coordination nodes with a deferred child admission
     * are woken and their children terminalized during close.
     */
    private final ConcurrentLinkedQueue<ExecutionNode> admissionDrain = new ConcurrentLinkedQueue<>();

    private volatile boolean closing;
    private volatile boolean failFast;
    private volatile boolean failureOccurred;
    private volatile Throwable unrecoverableFailure;
    private final ReentrantLock closingLock = new ReentrantLock();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IsolationState> isolationStates = new ConcurrentHashMap<>();

    /**
     * Creates a scheduler with the given parallelism.
     *
     * <p>{@code parallelism} is the maximum number of concurrently executing leaf actions.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @throws IllegalArgumentException if {@code parallelism} is not positive
     */
    public Scheduler(final int parallelism) {
        this(parallelism, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Creates a scheduler with the given parallelism and queue capacity.
     *
     * <p>{@code queueCapacity} sets the initial array size for the global executor's internal
     * priority queue and determines the number of global admission permits for initial descriptor
     * scheduling. The actual bound on queued tasks is enforced by a semaphore —
     * {@link java.util.concurrent.PriorityBlockingQueue} is unbounded and never rejects elements.
     * If the semaphore is exhausted, subsequent submissions are rejected immediately.
     *
     * <p>Continuation tasks (submitted by coordination strategies via
     * {@link #executeContinuation(ExecutionNode)}) are not gated by this semaphore; they are
     * necessary for forward progress and the executor's backing queue is unbounded.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     */
    Scheduler(final int parallelism, final int queueCapacity) {
        this(parallelism, queueCapacity, true, null, Integer.MAX_VALUE);
    }

    /**
     * Creates a scheduler with the given parallelism, queue capacity, and maximum overall leaf
     * concurrency.
     *
     * <p>{@code concurrencyCap} bounds the number of leaf action bodies running at the same time
     * across the whole tree, independently of the thread pool size: effective global leaf
     * concurrency is {@code min(parallelism, concurrencyCap)}. {@link Integer#MAX_VALUE} means the
     * pool size is the only global bound.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @param concurrencyCap the maximum concurrent leaf count across the tree (must be positive)
     * @throws IllegalArgumentException if any argument is not positive
     */
    Scheduler(final int parallelism, final int queueCapacity, final int concurrencyCap) {
        this(parallelism, queueCapacity, true, null, concurrencyCap);
    }

    /**
     * Creates a scheduler with the given parallelism, queue capacity, and core thread timeout
     * policy.
     *
     * <p>When {@code allowCoreThreadTimeout} is {@code true} (the default), idle scheduler worker
     * threads are terminated after a timeout, freeing resources during quiet periods. Subsequent
     * bursts pay thread recreation cost. When {@code false}, core threads remain alive, reducing
     * burst latency at the cost of holding threads during idle periods.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @param allowCoreThreadTimeout whether idle scheduler threads may be terminated
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     */
    Scheduler(final int parallelism, final int queueCapacity, final boolean allowCoreThreadTimeout) {
        this(parallelism, queueCapacity, allowCoreThreadTimeout, null, Integer.MAX_VALUE);
    }

    /**
     * Creates a scheduler with the given parallelism, queue capacity, core thread timeout
     * policy, and worker thread factory.
     *
     * <p>The thread factory is used to create executor worker threads. The scheduler wraps
     * each created thread with its own lifecycle tracking. The factory must produce daemon
     * threads with appropriate naming for correct scheduler operation.
     *
     * @param parallelism the maximum concurrent leaf execution count (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @param allowCoreThreadTimeout whether idle scheduler threads may be terminated
     * @param threadFactory the factory for executor worker threads; must not be {@code null}
     * @throws IllegalArgumentException if {@code parallelism} or {@code queueCapacity} is not
     *     positive
     * @throws NullPointerException if {@code threadFactory} is {@code null}
     */
    Scheduler(
            final int parallelism,
            final int queueCapacity,
            final boolean allowCoreThreadTimeout,
            final ThreadFactory threadFactory) {
        this(parallelism, queueCapacity, allowCoreThreadTimeout, threadFactory, Integer.MAX_VALUE);
    }

    /**
     * Creates a scheduler with every option explicit.
     *
     * @param parallelism the thread pool size and default global leaf bound (must be positive)
     * @param queueCapacity global admission permit count and initial queue allocation (must be
     *     positive)
     * @param allowCoreThreadTimeout whether idle scheduler threads may be terminated
     * @param threadFactory the factory for executor worker threads, or {@code null} for the default
     * @param concurrencyCap the maximum concurrent leaf count across the tree; {@link
     *     Integer#MAX_VALUE} for no separate overall cap
     * @throws IllegalArgumentException if {@code parallelism}, {@code queueCapacity}, or {@code
     *     concurrencyCap} is not positive
     */
    private Scheduler(
            final int parallelism,
            final int queueCapacity,
            final boolean allowCoreThreadTimeout,
            final ThreadFactory threadFactory,
            final int concurrencyCap) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive, was: " + queueCapacity);
        }
        if (concurrencyCap <= 0) {
            throw new IllegalArgumentException("concurrencyCap must be positive, was: " + concurrencyCap);
        }
        this.parallelism = parallelism;
        this.queueCapacity = queueCapacity;
        this.concurrencyCap = concurrencyCap;
        this.effectiveLeafConcurrency = Math.min(parallelism, concurrencyCap);
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
        this.queuePermits = new Semaphore(queueCapacity);
        this.leafPermits = new Semaphore(effectiveLeafConcurrency);
        this.executor = createExecutor(threadFactory != null ? threadFactory : defaultThreadFactory());
        this.delayExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, THREAD_NAME + "-delay");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sets the fail-fast policy for this scheduler.
     *
     * <p>When {@code true} and a failure or abort occurs during execution,
     * subsequent {@link #schedule} calls for direct children of the root
     * descriptor are skipped.
     *
     * @param failFast whether to enable fail-fast behavior
     */
    void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    Throwable unrecoverableFailure() {
        return unrecoverableFailure;
    }

    /**
     * Captures the first unrecoverable error ({@code VirtualMachineError} other than
     * {@code StackOverflowError}) so {@link ConcreteRunner} can surface it after the root
     * completes.
     *
     * <p>This does <em>not</em> rethrow: rethrowing from a worker's catch block would skip the
     * post-finally completion code and leave the descriptor's scheduling future incomplete,
     * which is worse for liveness than capturing-and-completing-exceptionally. Every terminal
     * error path (action throw, framework throw, continuation throw, finalize throw, and the
     * {@link #schedule} generic-catch) must invoke this so an unrecoverable error observed
     * anywhere is always surfaced to the runner.
     *
     * @param throwable the throwable to inspect and capture if unrecoverable; may be {@code null}
     */
    private void recordUnrecoverableFailure(final Throwable throwable) {
        if (UnrecoverableErrors.isUnrecoverable(throwable) && unrecoverableFailure == null) {
            unrecoverableFailure = throwable;
        }
    }

    private void recordExecutionFailureForFailFast(final Throwable throwable) {
        if (failFast && throwable != null) {
            closingLock.lock();
            try {
                failureOccurred = true;
            } finally {
                closingLock.unlock();
            }
        }
    }

    /**
     * Schedules a descriptor for execution with no lifecycle callback.
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @return a future that completes with the descriptor on success, or completes exceptionally on
     *     failure
     * @throws NullPointerException if any required argument is {@code null}
     * @see #schedule(MutableDescriptor, ExecutionMode, ConcreteContext, ExecutionCallback)
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor, final ExecutionMode mode, final ConcreteContext parentContext) {
        return schedule(descriptor, mode, parentContext, null);
    }

    /**
     * Schedules a descriptor for execution with an optional lifecycle callback.
     *
     * <p>If the scheduler is closing, returns a failed future immediately.
     *
     * @param descriptor the descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @param callback optional lifecycle callback, may be {@code null}
     * @return a future that completes with the descriptor on success, or completes exceptionally on
     *     failure
     * @throws NullPointerException if {@code descriptor}, {@code mode}, or
     *     {@code parentContext} is {@code null}
     */
    public CompletableFuture<Descriptor> schedule(
            final MutableDescriptor descriptor,
            final ExecutionMode mode,
            final ConcreteContext parentContext,
            final ExecutionCallback callback) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        Objects.requireNonNull(mode, "mode is null");
        Objects.requireNonNull(parentContext, "parentContext is null");

        var outcome = prepareScheduledTask(descriptor, parentContext);
        if (outcome.future != null) {
            return outcome.future;
        }
        var verdict = admitPreparedTask(descriptor, outcome.context, mode, callback, null, false);
        if (verdict == ChildAdmissionResult.REJECTED) {
            // External direct submissions keep their documented capacity-rejection semantics.
            var rejected = new RejectedExecutionException(
                    "Scheduler queue capacity exceeded (capacity=" + queueCapacity + ")");
            ensureTerminalStatus(outcome.context, rejected);
            publishRejectedCompletion(descriptor, callback, rejected);
            return CompletableFuture.failedFuture(rejected);
        }
        return descriptor.scheduledFuture();
    }

    /**
     * Attempts to dispatch one internally scheduled child descriptor for a coordination strategy.
     *
     * <p>This is the framework child-admission path used by execution strategies. Unlike
     * {@link #schedule(MutableDescriptor, ExecutionMode, ConcreteContext, ExecutionCallback)},
     * transient ready-capacity exhaustion does <em>not</em> fail the child: the child stays
     * unscheduled (its status remains pending and its scheduling future incomplete) and the
     * parent node is registered so its {@code admissionRetry} runs when capacity becomes
     * available.
     *
     * @param descriptor the child descriptor to schedule (must not be {@code null})
     * @param mode the execution mode (must not be {@code null})
     * @param parentContext the parent execution context (must not be {@code null})
     * @param parentNode the coordination node whose strategy owns the admission cursor
     * @return {@link ChildAdmissionResult#DISPATCHED} when the child was queued,
     *     {@link ChildAdmissionResult#DEFERRED} when ready capacity was exhausted, or
     *     {@link ChildAdmissionResult#REJECTED} when the scheduler is closing or the child was
     *     already terminal (in which case {@code childCompleted} has already been published)
     * @throws NullPointerException if any required argument is {@code null}
     */
    ChildAdmissionResult dispatchChild(
            final MutableDescriptor descriptor,
            final ExecutionMode mode,
            final ConcreteContext parentContext,
            final ExecutionNode parentNode) {
        Objects.requireNonNull(descriptor, "descriptor is null");
        Objects.requireNonNull(mode, "mode is null");
        Objects.requireNonNull(parentContext, "parentContext is null");
        Objects.requireNonNull(parentNode, "parentNode is null");

        var outcome = prepareScheduledTask(descriptor, parentContext);
        if (outcome.future != null) {
            return ChildAdmissionResult.REJECTED;
        }
        return admitPreparedTask(descriptor, outcome.context, mode, null, parentNode, true);
    }

    /**
     * Result of preparing a descriptor for scheduling.
     *
     * @param future a non-null value when the descriptor was already terminal or the scheduler
     *     is closing (the caller must return it verbatim); {@code null} when the descriptor is
     *     ready to be admitted
     * @param context the execution context when {@code future} is {@code null}
     */
    private record PreparationOutcome(CompletableFuture<Descriptor> future, ConcreteContext context) {}

    private PreparationOutcome prepareScheduledTask(
            final MutableDescriptor descriptor, final ConcreteContext parentContext) {
        // Compilation is idempotent and does not interact with the scheduler's closing state,
        // so it can be done outside the lock to reduce contention. It also freezes the tree,
        // which is what makes the compiled order immutable for the rest of the run.
        DescriptorOrder.compile(descriptor);

        if (descriptor.isCompleted()) {
            // Descriptor was cancelled/aborted before submission (e.g. by a Timeout cascade).
            // Avoid pushing it through the executor only to be skipped; return a failed future so
            // the caller's rolling window drains promptly.
            childCompleted(descriptor);
            return new PreparationOutcome(
                    CompletableFuture.failedFuture(new RejectedExecutionException("descriptor already terminal: "
                            + descriptor.status().name())),
                    null);
        }

        // Create context early so ensureTerminalStatus can be called in the closing
        // and admission failure paths. Without this, a descriptor rejected at the
        // first closing check stays PENDING, causing composite actions (e.g. Parallel)
        // to aggregate a non-terminal RUNNING status.
        var context = new ConcreteContext(
                parentContext.configuration(),
                parentContext.listener(),
                descriptor,
                this,
                parentContext.instanceHolder());

        closingLock.lock();
        try {
            if (closing) {
                var closingException = new IllegalStateException("Scheduler is closing");
                ensureTerminalStatus(context, closingException);
                childCompleted(descriptor);
                return new PreparationOutcome(CompletableFuture.failedFuture(closingException), null);
            }
        } finally {
            closingLock.unlock();
        }
        return new PreparationOutcome(null, context);
    }

    /**
     * Acquires a ready-capacity reservation and, on success, marks the descriptor scheduled and
     * enqueues its execution task.
     *
     * @param descriptor the descriptor to admit; must not be {@code null}
     * @param context the execution context; must not be {@code null}
     * @param mode the execution mode; must not be {@code null}
     * @param callback optional lifecycle callback, may be {@code null}
     * @param parentNode the coordination node for internal admission, or {@code null} for direct
     *     submission
     * @param internalAdmission whether this is a framework child admission
     * @return {@link ChildAdmissionResult#DISPATCHED}, {@link ChildAdmissionResult#DEFERRED}, or
     *     {@link ChildAdmissionResult#REJECTED}
     */
    private ChildAdmissionResult admitPreparedTask(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final ExecutionMode mode,
            final ExecutionCallback callback,
            final ExecutionNode parentNode,
            final boolean internalAdmission) {
        if (!queuePermits.tryAcquire()) {
            if (internalAdmission && parentNode != null) {
                if (relinquishIneligibleQueuedTaskIfNeeded() && queuePermits.tryAcquire()) {
                    return markScheduledAndEnqueue(descriptor, context, mode, callback);
                }
                // Register only when the admission actually remains deferred. Registering
                // before the relinquish attempt could leave a stale waiter behind when the
                // attempt succeeds, turning the next capacity signal into a spurious
                // admission retry (double admission for cursor-based strategies).
                // Lost wakeups stay impossible: registration happens under the capacity
                // monitor and the post-registration recheck observes any concurrent release.
                registerCapacityWaiter(parentNode);
                return ChildAdmissionResult.DEFERRED;
            }
            return ChildAdmissionResult.REJECTED;
        }
        return markScheduledAndEnqueue(descriptor, context, mode, callback);
    }

    private ChildAdmissionResult markScheduledAndEnqueue(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final ExecutionMode mode,
            final ExecutionCallback callback) {
        final CompletableFuture<Descriptor> future;
        try {
            future = descriptor.markScheduled();
        } catch (Throwable t) {
            // Defensive: a child is only ever dispatched once, but release the reservation and
            // terminalize rather than leaking either.
            queuePermits.release();
            ensureTerminalStatus(context, t);
            childCompleted(descriptor);
            return ChildAdmissionResult.REJECTED;
        }
        enqueueTask(descriptor, context, future, mode, callback);
        return ChildAdmissionResult.DISPATCHED;
    }

    private void enqueueTask(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionMode mode,
            final ExecutionCallback callback) {
        var sequence = taskSequence.getAndIncrement();
        var task = new PrioritizedTask(sequence, descriptor, context, mode, future, callback, queuePermits, this);
        if (descriptor.isLeafAction() && mode == ExecutionMode.RUN) {
            // A runnable leaf is held in the gated ready set until the global cap and every capped
            // ancestor have room, so a cap-blocked leaf can never occupy a thread or block a
            // lower-ranked eligible leaf in the executor queue.
            offerGatedLeaf(task);
            return;
        }
        enqueueGatedTask(task);
    }

    /**
     * Queues a leaf task whose capacity has already been reserved, or a task that is not gated.
     *
     * @param task the task to queue; must not be {@code null}
     */
    private void enqueueGatedTask(final PrioritizedTask task) {
        closingLock.lock();
        try {
            if (closing) {
                rejectTask(task, new IllegalStateException("Scheduler is closing"));
                return;
            }
            try {
                if (!task.descriptor.isLeafAction()) {
                    pendingCoordination.add(new ExpansionKey(task.orderTreeId(), task.orderRank()));
                }
                executor.execute(task);
                wakeParkedWaiters();
            } catch (RejectedExecutionException rejected) {
                clearPendingCoordination(task);
                rejectTask(task, rejected);
            } catch (Throwable t) {
                clearPendingCoordination(task);
                rejectTask(task, t);
                recordUnrecoverableFailure(t);
                UnrecoverableErrors.rethrowIfUnrecoverable(t);
            }
        } finally {
            closingLock.unlock();
        }
    }

    /**
     * Returns the configured global leaf parallelism.
     *
     * <p>This is the maximum number of leaf actions that may execute concurrently across all branches.
     *
     * @return the parallelism level, always positive
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the current number of leaf actions executing.
     *
     * @return the running leaf count, always non-negative
     */
    public int running() {
        return runningLeafCounter.get();
    }

    /**
     * Attempts to reserve leaf capacity for {@code leaf}: one global slot and one slot on every
     * capped ancestor.
     *
     * <p>The reservation is all-or-nothing and is made atomically with respect to every other leaf
     * start, so a leaf can never be admitted past a cap because two threads raced.
     *
     * @param leaf the leaf descriptor about to run; must not be {@code null}
     * @return {@code true} when the reservation was made and must be released with
     *     {@link #releaseLeafCapacity(MutableDescriptor)}
     */
    boolean tryReserveLeafCapacity(final MutableDescriptor leaf) {
        leafGateLock.lock();
        try {
            return canReserveLocked(leaf) && reserveLocked(leaf);
        } finally {
            leafGateLock.unlock();
        }
    }

    /**
     * Releases the reservation made by {@link #tryReserveLeafCapacity(MutableDescriptor)} and then
     * fills any leaf that the release made eligible.
     *
     * @param leaf the leaf descriptor that finished; must not be {@code null}
     */
    void releaseLeafCapacity(final MutableDescriptor leaf) {
        leafGateLock.lock();
        try {
            for (var state : capChainLocked(leaf)) {
                if (state.running > 0) {
                    state.running--;
                }
            }
            if (admittedLeafBodies > 0) {
                admittedLeafBodies--;
            }
        } finally {
            leafGateLock.unlock();
        }
        pumpGatedLeaves();
        wakeParkedWaiters();
    }

    /**
     * Offers an admitted leaf to the gated ready set and fills whatever capacity is available.
     *
     * @param task the leaf task to hold until capacity allows it to run; must not be {@code null}
     */
    private void offerGatedLeaf(final PrioritizedTask task) {
        leafGateLock.lock();
        try {
            gatedLeaves.add(task);
        } finally {
            leafGateLock.unlock();
        }
        pumpGatedLeaves();
    }

    /**
     * Fills free leaf capacity from the gated ready set: the lowest-ranked leaf that is eligible
     * right now is promoted into the executor queue, and a leaf blocked by a capped ancestor is
     * skipped rather than dropped, so it never blocks a lower-ranked eligible leaf.
     *
     * <p>Promotion reserves the leaf's capacity, so a promoted leaf is guaranteed a slot by the
     * time it runs and the gated set can never hold a leaf that a thread is waiting on.
     */
    private void pumpGatedLeaves() {
        List<PrioritizedTask> promoted = null;
        leafGateLock.lock();
        try {
            while (!gatedLeaves.isEmpty() && canReserveGloballyLocked()) {
                PrioritizedTask candidate = null;
                List<PrioritizedTask> skipped = null;
                while (!gatedLeaves.isEmpty()) {
                    var next = gatedLeaves.poll();
                    if (!earlierExpansionInFlight(next.descriptor) && canReserveLocked(next.descriptor)) {
                        candidate = next;
                        break;
                    }
                    if (skipped == null) {
                        skipped = new ArrayList<>();
                    }
                    skipped.add(next);
                }
                if (skipped != null) {
                    gatedLeaves.addAll(skipped);
                }
                if (candidate == null) {
                    break;
                }
                reserveLocked(candidate.descriptor);
                if (promoted == null) {
                    promoted = new ArrayList<>();
                }
                promoted.add(candidate);
            }
        } finally {
            leafGateLock.unlock();
        }
        if (promoted != null) {
            for (var task : promoted) {
                task.capacityReserved = true;
                enqueueGatedTask(task);
            }
            wakeParkedWaiters();
        }
    }

    /**
     * Returns whether the global cap has room. Callers must hold {@link #leafGateLock}.
     *
     * @return {@code true} when another leaf may be admitted
     */
    private boolean canReserveGloballyLocked() {
        return admittedLeafBodies < effectiveLeafConcurrency;
    }

    /**
     * Returns whether every capped ancestor of {@code leaf} has room. Callers must hold
     * {@link #leafGateLock}.
     *
     * @param leaf the candidate leaf
     * @return {@code true} when the leaf is eligible to start
     */
    private boolean canReserveLocked(final MutableDescriptor leaf) {
        if (!canReserveGloballyLocked()) {
            return false;
        }
        for (var state : capChainLocked(leaf)) {
            if (state.running >= state.limit) {
                return false;
            }
        }
        return true;
    }

    /**
     * Increments the global and per-ancestor running counters for {@code leaf}. Callers must hold
     * {@link #leafGateLock} and must have checked {@link #canReserveLocked}.
     *
     * @param leaf the leaf being admitted
     * @return always {@code true}
     */
    private boolean reserveLocked(final MutableDescriptor leaf) {
        for (var state : capChainLocked(leaf)) {
            state.running++;
        }
        admittedLeafBodies++;
        return true;
    }

    /**
     * Returns the capped ancestors of {@code leaf}, creating their counters on first use. Callers
     * must hold {@link #leafGateLock}.
     *
     * <p>The chain is computed once per leaf from the descriptor tree, so a leaf's eligibility test
     * is a walk over a short array rather than over its ancestry.
     *
     * @param leaf the leaf descriptor
     * @return the capped ancestors, root first
     */
    private List<CapState> capChainLocked(final MutableDescriptor leaf) {
        var cached = leafCapChains.get(leaf);
        if (cached != null) {
            return cached;
        }
        var chain = new ArrayList<CapState>(2);
        for (var parent = leaf.parent().orElse(null);
                parent != null;
                parent = parent.parent().orElse(null)) {
            if (!(parent instanceof MutableDescriptor capped)) {
                continue;
            }
            if (!(capped.action() instanceof Parallel parallel)) {
                continue;
            }
            var declared = parallel.parallelism();
            if (declared == Integer.MAX_VALUE) {
                continue;
            }
            var limit = Math.max(1, Math.min(declared, parallelism));
            chain.add(cappedNodes.computeIfAbsent(capped, ignored -> new CapState(limit)));
        }
        // Ancestors were collected bottom-up; reverse so the order is root-first and stable.
        for (int i = 0, j = chain.size() - 1; i < j; i++, j--) {
            var swap = chain.get(i);
            chain.set(i, chain.get(j));
            chain.set(j, swap);
        }
        var result = List.copyOf(chain);
        leafCapChains.put(leaf, result);
        return result;
    }

    /**
     * Returns whether an earlier-ranked coordination descriptor is still expanding.
     *
     * <p>Only descriptors from the same compilation are comparable; an entry from another tree is
     * ignored, matching the ready queue's ordering rule.
     *
     * @param leaf the candidate leaf
     * @return {@code true} when an earlier coordination step is in flight
     */
    private boolean earlierExpansionInFlight(final MutableDescriptor leaf) {
        return hasEarlierKey(expandingCoordination, leaf) || hasEarlierKey(pendingCoordination, leaf);
    }

    /**
     * Returns whether {@code keys} holds an entry from the same compilation ranked below {@code leaf}.
     *
     * @param keys the candidate entries
     * @param leaf the leaf being considered for promotion
     * @return {@code true} when an earlier-ranked entry exists
     */
    private static boolean hasEarlierKey(
            final java.util.NavigableSet<ExpansionKey> keys, final MutableDescriptor leaf) {
        if (keys.isEmpty()) {
            return false;
        }
        for (var key : keys) {
            if (key.treeId() == leaf.orderTreeId()) {
                if (key.rank() < leaf.orderRank()) {
                    return true;
                }
            } else if (key.treeId() > leaf.orderTreeId()) {
                break;
            }
        }
        return false;
    }

    /**
     * Drops the queued-coordination marker for {@code task}.
     *
     * @param task the task that is starting, or that was rejected
     */
    private void clearPendingCoordination(final PrioritizedTask task) {
        if (!task.descriptor.isLeafAction()) {
            pendingCoordination.remove(new ExpansionKey(task.orderTreeId(), task.orderRank()));
        }
    }

    /**
     * Compiled order of a descriptor whose admission step is in flight.
     *
     * @param treeId the compilation identity
     * @param rank the compiled rank within that compilation
     */
    private record ExpansionKey(long treeId, long rank) implements Comparable<ExpansionKey> {

        @Override
        public int compareTo(final ExpansionKey other) {
            var byTree = Long.compare(treeId, other.treeId);
            return byTree != 0 ? byTree : Long.compare(rank, other.rank);
        }
    }

    /**
     * Per-capped-node running leaf count and limit.
     *
     * <p>Mutable {@link #running} is guarded by {@link #leafGateLock}.
     */
    private static final class CapState {

        private final int limit;
        private int running;

        private CapState(final int limit) {
            this.limit = limit;
        }
    }

    /**
     * Returns the global admission permit count, which also sets the initial array size for the
     * executor's priority queue.
     *
     * <p>This is not a hard queue bound — admission is controlled by a semaphore with this many
     * permits.
     *
     * @return configured admission permit count, always positive
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * Returns the number of queued tasks in the global executor.
     *
     * @return non-negative total queued task count
     */
    public int readyQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * Returns how many continuations have been advanced inline on a completing thread rather than
     * submitted to the executor.
     *
     * <p>Observability for tests and benchmarks: inline continuations avoid a queue submission and
     * a hand-off, so this counter distinguishes the inline path from the queued path.
     *
     * @return the number of inline continuation executions since construction
     */
    long inlineContinuations() {
        return inlineContinuationCounter.get();
    }

    /**
     * Returns how many times parked waiters have been woken because work became available or a
     * node completed.
     *
     * <p>Observability for tests: without the wakeups, a waiter only re-checks when its backoff
     * park expires.
     *
     * @return the number of wakeup broadcasts since construction
     */
    long parkedWaiterWakeups() {
        return parkedWaiterWakeupCounter.get();
    }

    /**
     * Returns how many leaf children have been executed inline on a completing thread rather than
     * submitted to the executor.
     *
     * @return the number of inline leaf executions since construction
     */
    long inlineChildren() {
        return inlineChildExecutionCounter.get();
    }

    /**
     * Returns the fair re-entrant lock associated with the given name, creating it
     * if it does not already exist.
     *
     * <p>The lock is used by {@link org.paramixel.api.action.Isolated} actions to
     * serialize execution of actions that share the same lock name.
     *
     * @param lockName the lock name; must not be {@code null}
     * @return a fair re-entrant lock for the given name; never {@code null}
     * @throws NullPointerException if {@code lockName} is {@code null}
     */
    ReentrantLock getLock(final String lockName) {
        Objects.requireNonNull(lockName, "lockName is null");
        return locks.computeIfAbsent(lockName, k -> new ReentrantLock(true));
    }

    /**
     * Enters an {@link Isolated} lock for synchronous action execution.
     *
     * @param lockName the isolation lock name; must not be {@code null}
     * @param lock the backing re-entrant lock; must not be {@code null}
     * @param descriptor the descriptor entering the lock; must not be {@code null}
     */
    void enterIsolation(final String lockName, final ReentrantLock lock, final MutableDescriptor descriptor) {
        Objects.requireNonNull(lockName, "lockName is null");
        Objects.requireNonNull(lock, "lock is null");
        Objects.requireNonNull(descriptor, "descriptor is null");
        var existingFrame = currentIsolationFrame(lockName, descriptor);
        if (existingFrame != null) {
            return;
        }
        var state = isolationState(lockName);
        synchronized (state) {
            if (state.ownerThread == Thread.currentThread()
                    && DescriptorOrder.isSameOrDescendant(descriptor, state.ownerDescriptor)) {
                lock.lock();
                pushIsolationFrame(lockName, descriptor, lock);
                return;
            }
            if (state.ownerDescriptor == null && state.dispatchedTask == null && state.deferredTasks.isEmpty()) {
                lock.lock();
                state.ownerDescriptor = descriptor;
                state.ownerThread = Thread.currentThread();
                pushIsolationFrame(lockName, descriptor, lock);
                return;
            }
        }
        lock.lock();
        synchronized (state) {
            state.ownerDescriptor = descriptor;
            state.ownerThread = Thread.currentThread();
            state.dispatchedTask = null;
            pushIsolationFrame(lockName, descriptor, lock);
        }
    }

    /**
     * Exits an {@link Isolated} lock previously entered by the current thread.
     *
     * @param lockName the isolation lock name; must not be {@code null}
     * @param lock the backing re-entrant lock; must not be {@code null}
     * @param descriptor the descriptor exiting the lock; must not be {@code null}
     */
    void exitIsolation(final String lockName, final ReentrantLock lock, final MutableDescriptor descriptor) {
        Objects.requireNonNull(lockName, "lockName is null");
        Objects.requireNonNull(lock, "lock is null");
        Objects.requireNonNull(descriptor, "descriptor is null");
        var frame = currentIsolationFrame(lockName, descriptor);
        if (frame == null) {
            return;
        }
        exitIsolationFrame(frame);
    }

    private boolean tryEnterScheduledIsolation(final PrioritizedTask task) {
        var lockName = task.scheduledIsolationLockName();
        if (lockName == null || wouldSkipForFailFast(task.context)) {
            return true;
        }
        var lock = getLock(lockName);
        var state = isolationState(lockName);
        synchronized (state) {
            if (state.ownerDescriptor == null && (state.deferredTasks.isEmpty() || state.dispatchedTask == task)) {
                if (!lock.tryLock()) {
                    deferIsolationTask(state, task);
                    return false;
                }
                state.ownerDescriptor = task.descriptor;
                state.ownerThread = Thread.currentThread();
                state.dispatchedTask = null;
                pushIsolationFrame(lockName, task.descriptor, lock);
                return true;
            }
            if (state.ownerThread == Thread.currentThread()
                    && DescriptorOrder.isSameOrDescendant(task.descriptor, state.ownerDescriptor)) {
                lock.lock();
                pushIsolationFrame(lockName, task.descriptor, lock);
                return true;
            }
            if (state.ownerDescriptor != null
                    && DescriptorOrder.isSameOrDescendant(task.descriptor, state.ownerDescriptor)) {
                state.ownerTasks.add(task);
                LockSupport.unpark(state.ownerThread);
                return false;
            }
            deferIsolationTask(state, task);
            return false;
        }
    }

    private void exitScheduledIsolationIfHeld(final PrioritizedTask task) {
        var lockName = task.scheduledIsolationLockName();
        if (lockName == null) {
            return;
        }
        var frame = currentIsolationFrame(lockName, task.descriptor);
        if (frame != null) {
            exitIsolationFrame(frame);
        }
    }

    private void exitIsolationFrame(final IsolationFrame frame) {
        var lock = frame.lock();
        var outermostRelease = lock.getHoldCount() == 1;
        try {
            lock.unlock();
        } finally {
            popIsolationFrame(frame.lockName(), frame.descriptor());
            if (outermostRelease) {
                releaseIsolationOwner(frame.lockName(), frame.descriptor());
            }
        }
    }

    private void releaseIsolationOwner(final String lockName, final MutableDescriptor descriptor) {
        var state = isolationState(lockName);
        PrioritizedTask nextTask = null;
        synchronized (state) {
            if (state.ownerDescriptor == descriptor) {
                state.deferredTasks.addAll(state.ownerTasks);
                state.ownerTasks.clear();
                state.ownerDescriptor = null;
                state.ownerThread = null;
                nextTask = state.deferredTasks.poll();
                state.dispatchedTask = nextTask;
            }
        }
        if (nextTask != null) {
            try {
                executor.execute(nextTask);
                wakeParkedWaiters();
            } catch (RejectedExecutionException e) {
                rejectTask(nextTask, e);
            }
        }
        // Lock release may free capacity for relinquished tasks that were parked while this
        // owner held its lock.
        signalCapacityAvailable();
    }

    /**
     * Terminalizes a task that will never execute: its reservation (if any) is released, its
     * descriptor is given a terminal status, its scheduling future is failed, and the parent is
     * notified. Used for tasks relinquished before dispatch and for tasks deferred behind an
     * isolation lock whose redispatch was rejected during shutdown.
     *
     * @param task the task that will not run; must not be {@code null}
     * @param cause the reason the task will not run; must not be {@code null}
     */
    private void rejectTask(final PrioritizedTask task, final Throwable cause) {
        releaseReservationIfHeld(task);
        ensureTerminalStatus(task.context, cause);
        completeExceptionally(task.descriptor, task.future, cause);
        publishRejectedCompletion(task.descriptor, task.callback, cause);
    }

    /**
     * Releases a parked task's queue reservation if it still holds one.
     *
     * @param task the task whose reservation to release
     */
    private static void releaseReservationIfHeld(final PrioritizedTask task) {
        if (task.reservationHeld) {
            task.reservationHeld = false;
            task.queuePermits.release();
        }
    }

    private void processRemovedTasks(final List<Runnable> removed) {
        if (removed == null || removed.isEmpty()) {
            return;
        }
        for (var task : removed) {
            try {
                if (task instanceof PrioritizedTask pt) {
                    rejectTask(pt, schedulerClosingException());
                } else if (task instanceof ContinuationTask ct) {
                    runContinuationOnce(ct.node());
                } else if (task instanceof AdmissionWakeupTask at) {
                    admissionDrain.add(at.node());
                }
                // Unexpected runnable type: skip (defensive)
            } catch (Throwable ignored) {
                // Don't let one failing task strand the rest
            }
        }
    }

    /**
     * Registers a coordination node whose internal child admission was deferred so its
     * {@code admissionRetry} runs when ready capacity becomes available.
     *
     * @param node the node waiting for capacity; must not be {@code null}
     */
    private void registerCapacityWaiter(final ExecutionNode node) {
        synchronized (capacityMonitor) {
            admissionWaiters.add(node);
        }
        // Recheck after registration to prevent a lost wakeup between the failed acquire and the
        // next permit release.
        if (queuePermits.availablePermits() > 0) {
            dispatchAdmissionWakeup(node);
        }
    }

    /**
     * Called whenever a queue permit is released. Retries deferred child admissions and
     * redispatches relinquished tasks that can now obtain capacity.
     */
    private void signalCapacityAvailable() {
        boolean anythingPending;
        synchronized (capacityMonitor) {
            anythingPending = !admissionWaiters.isEmpty() || !parkedReadyTasks.isEmpty();
        }
        if (!anythingPending || queuePermits.availablePermits() == 0) {
            return;
        }
        List<ExecutionNode> waiters = List.of();
        synchronized (capacityMonitor) {
            if (!admissionWaiters.isEmpty()) {
                waiters = new ArrayList<>(admissionWaiters);
            }
        }
        for (var node : waiters) {
            dispatchAdmissionWakeup(node);
        }
        redispatchParkedTask();
    }

    /**
     * Queues one admission wakeup for a node, coalesced to at most one outstanding wakeup per
     * node. The node re-registers when its retry defers again.
     *
     * @param node the node to wake; must not be {@code null}
     */
    private void dispatchAdmissionWakeup(final ExecutionNode node) {
        synchronized (capacityMonitor) {
            admissionWaiters.remove(node);
        }
        if (!node.admissionWakeupPending.compareAndSet(false, true)) {
            return;
        }
        if (executor.isShutdown()) {
            admissionDrain.add(node);
            return;
        }
        var sequence = taskSequence.getAndIncrement();
        try {
            executor.execute(new AdmissionWakeupTask(sequence, node, this));
            wakeParkedWaiters();
        } catch (RejectedExecutionException e) {
            node.admissionWakeupPending.set(false);
            admissionDrain.add(node);
        }
    }

    /**
     * Runs one admission retry once: clears the coalescing flag and invokes the node's
     * strategy-provided retry.
     *
     * @param node the node whose admission should be retried; must not be {@code null}
     */
    private void runAdmissionRetryOnce(final ExecutionNode node) {
        node.admissionWakeupPending.set(false);
        try {
            var retry = node.admissionRetry;
            if (retry != null) {
                retry.run();
            }
        } catch (Throwable t) {
            recordUnrecoverableFailure(Throwables.unwrap(t));
        }
    }

    /**
     * When the current thread holds isolation frames and ready capacity is exhausted, relinquishes
     * one queued initial task that does not belong to the held isolation subtree, freeing its
     * reservation so the lock owner can admit its own descendants.
     *
     * <p>The relinquished task is not executed or terminalized; it is parked and redispatched by
     * {@link #redispatchParkedTask()} once capacity is available again.
     *
     * @return {@code true} when one queued task was relinquished
     */
    private boolean relinquishIneligibleQueuedTaskIfNeeded() {
        var frames = isolationFrames.get();
        if (frames.isEmpty()) {
            return false;
        }
        var innermostDescriptor = frames.get(frames.size() - 1).descriptor();
        var queue = executor.getQueue();
        PrioritizedTask victim = null;
        for (var candidate : queue.toArray()) {
            if (candidate instanceof PrioritizedTask pt
                    && !DescriptorOrder.isSameOrDescendant(pt.descriptor, innermostDescriptor)) {
                victim = pt;
                break;
            }
        }
        if (victim == null || !queue.remove(victim)) {
            return false;
        }
        synchronized (capacityMonitor) {
            parkedReadyTasks.add(victim);
        }
        releaseReservationIfHeld(victim);
        return true;
    }

    /**
     * Redispatches one relinquished queued task when a queue permit is available, re-acquiring
     * the reservation before re-enqueueing.
     */
    private void redispatchParkedTask() {
        PrioritizedTask task;
        synchronized (capacityMonitor) {
            if (parkedReadyTasks.isEmpty() || queuePermits.availablePermits() == 0) {
                return;
            }
            task = parkedReadyTasks.poll();
        }
        if (task == null) {
            return;
        }
        if (!queuePermits.tryAcquire()) {
            synchronized (capacityMonitor) {
                parkedReadyTasks.add(task);
            }
            return;
        }
        task.reservationHeld = true;
        closingLock.lock();
        try {
            if (closing) {
                rejectTask(task, schedulerClosingException());
                return;
            }
            try {
                executor.execute(task);
                wakeParkedWaiters();
            } catch (RejectedExecutionException e) {
                rejectTask(task, e);
            }
        } finally {
            closingLock.unlock();
        }
    }

    private void deferIsolationTask(final IsolationState state, final PrioritizedTask task) {
        if (state.dispatchedTask == task) {
            state.dispatchedTask = null;
        }
        state.deferredTasks.add(task);
    }

    private IsolationState isolationState(final String lockName) {
        return isolationStates.computeIfAbsent(lockName, ignored -> new IsolationState());
    }

    private IsolationFrame currentIsolationFrame(final String lockName, final MutableDescriptor descriptor) {
        var frames = isolationFrames.get();
        if (frames.isEmpty()) {
            return null;
        }
        var frame = frames.get(frames.size() - 1);
        if (frame.lockName().equals(lockName) && frame.descriptor() == descriptor) {
            return frame;
        }
        return null;
    }

    private boolean wouldSkipForFailFast(final ConcreteContext context) {
        if (!failFast
                || context.descriptor()
                        .parent()
                        .filter(parent -> parent.parent().isEmpty())
                        .isEmpty()) {
            return false;
        }
        closingLock.lock();
        try {
            return failureOccurred;
        } finally {
            closingLock.unlock();
        }
    }

    /**
     * Blocks until the given future completes, work-stealing queued tasks while waiting.
     *
     * <p>While the future is incomplete the caller repeatedly polls the executor queue and runs
     * any stolen task itself, preserving deadlock-avoidance under nested blocking. When the queue
     * is momentarily empty the caller parks with a bounded multiplicative backoff
     * ({@link BackoffDelay}) rather than busy-spinning: the park starts at 1 000 ns (1 microsecond)
     * and grows toward 1 000 000 000 ns (1 second), resetting to the floor on every successful
     * steal. Worst-case steal latency is therefore ~1 second, reached only after a sustained empty
     * queue; transient empties resolve at the floor.
     *
     * <p>A stray interrupt flag on the calling thread is consumed before parking to prevent
     * {@code parkNanos} from returning immediately in a busy-spin; the backoff is reset to the
     * floor when this occurs so the first actual park retains low-latency cadence.
     *
     * <p>The loop does not exit on thread interruption; it relies on the watched future
     * reaching a terminal state. During executor shutdown, if the future becomes
     * uncompletable (practically unreachable - all normal completion paths complete the
     * future), the calling thread may remain in the loop as a daemon thread until JVM exit.
     *
     * @param <T> the future result type
     * @param future the future to wait on (must not be {@code null})
     * @return the result of the future
     * @throws NullPointerException if {@code future} is {@code null}
     * @throws CompletionException if the future completed exceptionally
     */
    public <T> T managedJoin(final CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future is null");
        var framesHeld = !isolationFrames.get().isEmpty();
        var backoff = new BackoffDelay(
                MANAGED_JOIN_BACKOFF_FLOOR_NANOS,
                MANAGED_JOIN_BACKOFF_CEILING_NANOS,
                MANAGED_JOIN_BACKOFF_GROWTH_FACTOR);
        var waitDepth = managedJoinDepth.get();
        managedJoinDepth.set(waitDepth + 1);
        if (waitDepth == 0) {
            parkedWaiters.add(Thread.currentThread());
        }
        try {
            while (!future.isDone()) {
                var ownedIsolationTask = pollOwnedIsolationTask();
                if (ownedIsolationTask != null) {
                    backoff.reset();
                    runQueuedTask(ownedIsolationTask);
                    continue;
                }
                var task = stealEligibleReadyTask();
                if (task != null) {
                    backoff.reset();
                    runQueuedTask(task);
                    continue;
                }
                // During shutdown, continuations are added to the shared shutdownDrain
                // queue instead of the executor. Poll that queue to make progress.
                if (executor.isShutdown()) {
                    var node = shutdownDrain.poll();
                    if (node != null) {
                        backoff.reset();
                        runContinuationOnce(node);
                        continue;
                    }
                    var admissionNode = admissionDrain.poll();
                    if (admissionNode != null) {
                        backoff.reset();
                        runAdmissionRetryOnce(admissionNode);
                        continue;
                    }
                }
                // Once the executor is fully terminated and no work remains
                // in the drain queues, no further progress is possible. The future
                // should already be done (all scheduler workers have terminated).
                // If it is not, cancel it to avoid blocking forever in join().
                if (executor.isTerminated() && shutdownDrain.isEmpty() && admissionDrain.isEmpty()) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                    break;
                }
                // ParkNanos returns immediately without blocking when the interrupt flag
                // is set, turning the backoff-gated park into a busy-spin. Consume any
                // stray flag and reset the backoff so the next park actually blocks.
                if (Thread.interrupted()) {
                    backoff.reset();
                }
                // A lock owner parks only when no eligible work is available; new eligible work can
                // arrive at any time (e.g. an inter-iteration delay continuation), so cap the park so
                // eligibility changes are noticed promptly instead of after a full backoff ceiling.
                var parkNanos = backoff.nextDelayNanos();
                if (framesHeld && parkNanos > ISOLATION_JOIN_PARK_CEILING_NANOS) {
                    parkNanos = ISOLATION_JOIN_PARK_CEILING_NANOS;
                }
                LockSupport.parkNanos(parkNanos);
            }
            return future.join();
        } finally {
            if (waitDepth == 0) {
                parkedWaiters.remove(Thread.currentThread());
                managedJoinDepth.remove();
            } else {
                managedJoinDepth.set(waitDepth);
            }
        }
    }

    /**
     * Wakes threads parked in {@link #managedJoin} so they re-check for work.
     *
     * <p>Called whenever new work becomes available or a node completes. Without this, a parked
     * waiter only re-checks when its backoff park expires — up to one second, or ten milliseconds
     * while it holds isolation frames. A thread that is not parked simply keeps the permit and
     * returns immediately from its next park.
     *
     * <p>Internal API for {@link nonapi.org.paramixel.action.ExecutionNode}.
     */
    public void wakeParkedWaiters() {
        if (parkedWaiters.isEmpty()) {
            return;
        }
        parkedWaiterWakeupCounter.incrementAndGet();
        for (var waiter : parkedWaiters) {
            LockSupport.unpark(waiter);
        }
    }

    /**
     * Polls one eligible task from the ready queue for this thread to run inline.
     *
     * <p>When the calling thread holds isolation frames it must only execute work that belongs to
     * its own isolation subtree. An unrelated coordinator may schedule a same-lock descendant
     * that is deferred behind the held lock; the stolen coordinator would then block forever on
     * the lock release that its own stack is preventing. When no isolation frames are held any
     * task may be stolen (unrestricted work stealing).
     *
     * @return a task to run inline, or {@code null} when the queue holds no eligible work
     */
    private Runnable stealEligibleReadyTask() {
        var frames = isolationFrames.get();
        if (frames.isEmpty()) {
            return executor.getQueue().poll();
        }
        // Work selection must respect every active isolation frame. Frames are strictly nested
        // in the descriptor tree, so the innermost frame's subtree is the eligibility bound.
        var innermostDescriptor = frames.get(frames.size() - 1).descriptor();
        var queue = executor.getQueue();
        // Fast path: the head is eligible.
        var head = queue.peek();
        if (head != null && taskEligible(head, innermostDescriptor)) {
            var polled = queue.poll();
            if (polled != null && taskEligible(polled, innermostDescriptor)) {
                return polled;
            }
            if (polled != null) {
                // Another worker removed the eligible head and we polled an ineligible task
                // instead: put it back and fall through to the full scan. The backing queue is
                // an unbounded PriorityBlockingQueue, so the offer cannot be rejected.
                queue.offer(polled);
            }
        }
        // PriorityBlockingQueue iteration is not sorted and arbitrary removal is O(n); the queue
        // is only scanned this way when the owner holds isolation frames and the head is not
        // eligible, which is rare. Find the highest-priority eligible task anywhere in the queue
        // so an ineligible head cannot starve eligible work behind it.
        Runnable best = null;
        for (var candidate : queue.toArray()) {
            if (!(candidate instanceof Runnable runnable) || !taskEligible(runnable, innermostDescriptor)) {
                continue;
            }
            if (best == null || PRIORITY_COMPARATOR.compare(runnable, best) < 0) {
                best = runnable;
            }
        }
        if (best != null && queue.remove(best)) {
            return best;
        }
        return null;
    }

    private static boolean taskEligible(final Runnable task, final MutableDescriptor innermostFrameDescriptor) {
        var descriptor = taskDescriptorOf(task);
        return descriptor != null && DescriptorOrder.isSameOrDescendant(descriptor, innermostFrameDescriptor);
    }

    private static MutableDescriptor taskDescriptorOf(final Runnable task) {
        return task instanceof ScheduledTask scheduled ? scheduled.descriptor() : null;
    }

    /**
     * Shuts down the scheduler and waits for executor termination.
     *
     * <p>Tasks already submitted may continue running until interrupted by shutdown escalation.
     *
     * <p>Leaf permit waiters are unblocked by releasing enough permits. After all executor threads
     * have terminated, excess permits are drained to preserve the invariant
     * {@code availablePermits <= parallelism}.
     *
     * <p>Work-stealing via {@link #managedJoin(CompletableFuture)} during shutdown cannot cause
     * leaf-permit leaks: every {@code acquire()} in
     * {@link #executeDescriptorWithCompletion} has a paired {@code release()} in a finally
     * block guarded by a local {@code acquiredPermit} flag. Work-stolen tasks that run during
     * the window between permit release and drain acquire and release permits on the
     * work-stealing thread, and the finally-block release executes before the thread returns
     * control, so no permit is permanently lost.
     *
     * <p>If the calling thread is interrupted during shutdown, the interrupt flag is restored
     * and the method returns normally rather than throwing. Callers can check
     * {@link Thread#isInterrupted()} after this method returns to detect interruption.
     */
    @Override
    public void close() {
        closingLock.lock();
        try {
            closing = true;
            // Release enough permits to unblock any leaf workers waiting on leafPermits.acquire()
            // so they can finish during the graceful shutdown phase.
            leafPermits.release(parallelism);
            delayExecutor.shutdownNow();
            executor.shutdown();
            for (var entry : delayedContinuations.entrySet()) {
                if (delayedContinuations.remove(entry.getKey(), entry.getValue())) {
                    entry.getValue().cancel(false);
                    executeContinuation(entry.getKey());
                }
            }
            // Wake coordination nodes whose internal child admissions were deferred so their
            // children are terminalized during shutdown, and terminalize relinquished queued
            // tasks so no retained scheduler state outlives close.
            List<ExecutionNode> waiters;
            List<PrioritizedTask> parked;
            synchronized (capacityMonitor) {
                waiters = new ArrayList<>(admissionWaiters);
                admissionWaiters.clear();
                parked = new ArrayList<>(parkedReadyTasks);
                parkedReadyTasks.clear();
            }
            // Leaves still waiting for capacity would otherwise be stranded: the gated ready set is
            // not visible to the executor, so shutdown must terminalize them explicitly.
            List<PrioritizedTask> gated;
            leafGateLock.lock();
            try {
                gated = new ArrayList<>(gatedLeaves);
                gatedLeaves.clear();
            } finally {
                leafGateLock.unlock();
            }
            parked.addAll(gated);
            for (var node : waiters) {
                dispatchAdmissionWakeup(node);
            }
            for (var task : parked) {
                rejectTask(task, schedulerClosingException());
            }
        } finally {
            closingLock.unlock();
        }

        // Blocking wait happens outside closingLock to avoid deadlock with schedule()
        // callers (e.g. Parallel coordinators) that need the lock to check closing.
        try {
            if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                var removed = executor.shutdownNow();
                processRemovedTasks(removed);
                executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var removed = executor.shutdownNow();
            processRemovedTasks(removed);
            try {
                executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
            }
        }
        // All worker threads have terminated. No more leafPermits.release()
        // calls can occur. Drain any excess permits to restore the invariant.
        leafPermits.drainPermits();
    }

    /**
     * Called by an {@link ExecutionNode} when its pending child count reaches zero.
     *
     * <p>Schedules the node's continuation runnable as a work item on the executor
     * queue. The continuation runs on a scheduler worker thread with proper thread
     * naming and lifecycle management.
     *
     * <p>This method does not acquire an admission permit from the global semaphore.
     * Continuations are necessary for forward progress and rejection is not an option;
     * the executor's backing {@link java.util.concurrent.PriorityBlockingQueue} is
     * unbounded.
     *
     * @param node the execution node whose continuation should run; must not be
     *     {@code null}
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public void executeContinuation(final ExecutionNode node) {
        Objects.requireNonNull(node, "node is null");
        if (executor.isShutdown()) {
            // Scheduler shutting down — add to the shared shutdown drain queue.
            // managedJoin() polls this queue while waiting, so any thread blocked
            // on a deferred descriptor's nodeCompletion will process the continuation.
            shutdownDrain.add(node);
            return;
        }
        if (canRunContinuationInline()) {
            // The completing thread advances its own parent; this avoids a queue submission and
            // a hand-off per child completion. Ordering is unaffected: the continuation only
            // admits work, and admitted work still competes by compiled structural rank.
            runContinuationInline(node);
            return;
        }
        var sequence = taskSequence.getAndIncrement();
        try {
            executor.execute(new ContinuationTask(sequence, node, this));
            wakeParkedWaiters();
        } catch (RejectedExecutionException e) {
            // Race: shut down between the isShutdown() check and execute().
            shutdownDrain.add(node);
        }
    }

    /**
     * Returns whether the current thread may advance a continuation inline.
     *
     * <p>Only scheduler workers inline, and only within {@link #INLINE_CONTINUATION_MAX_DEPTH}
     * levels of nesting. The nesting bound matters because a continuation can synchronously
     * execute skipped or aborted descendants, each of which completes and may itself inline.
     *
     * @return {@code true} when the current thread may run a continuation directly
     */
    private boolean canRunContinuationInline() {
        return Boolean.TRUE.equals(schedulerWorker.get())
                && inlineContinuationDepth.get() < INLINE_CONTINUATION_MAX_DEPTH;
    }

    /**
     * Returns whether the calling scheduler worker should execute a leaf child directly instead of
     * submitting it to the executor.
     *
     * <p>Eligibility is deliberately narrow:
     *
     * <ul>
     *   <li>leaf children only — a coordination child would park the calling thread in
     *       {@code managedJoin}, trading a hand-off for an occupied worker;
     *   <li>the caller must be a scheduler worker, and inline nesting must be below
     *       {@link #INLINE_CONTINUATION_MAX_DEPTH};
     *   <li>the child must outrank everything already queued, so inlining cannot jump earlier
     *       work (R3). The ready queue is a min-heap, so peeking the head answers that exactly.
     * </ul>
     *
     * <p>Callers must not use this while admitting a burst of children (a {@code Parallel}
     * window): running each child to completion inside the admission loop would serialize the
     * fan-out.
     *
     * @param child the child descriptor; must not be {@code null}
     * @return {@code true} when the child can run on the calling thread
     */
    boolean canExecuteChildInline(final MutableDescriptor child) {
        if (!Boolean.TRUE.equals(schedulerWorker.get())
                || inlineContinuationDepth.get() >= INLINE_CONTINUATION_MAX_DEPTH) {
            return false;
        }
        var head = executor.getQueue().peek();
        if (head == null) {
            return true;
        }
        if (!(head instanceof ScheduledTask scheduled)
                || scheduled.orderTreeId() != child.orderTreeId()
                || child.orderRank() == DescriptorOrder.UNCOMPILED) {
            // Ranks from different compilations are not comparable, and an uncompiled descriptor
            // has no rank yet: queue the child rather than inline it out of order.
            return false;
        }
        return child.orderRank() < scheduled.orderRank();
    }

    /**
     * Suspends the innermost expansion registered on this thread.
     *
     * <p>Called when a coordination descriptor runs a child body inline: it delegates a thread to
     * user code, so it is no longer expanding its own children and must not keep later siblings
     * waiting for a ready leaf.
     */
    private void suspendExpansion() {
        var key = currentExpansion.get();
        if (key != null) {
            expandingCoordination.remove(key);
            currentExpansion.remove();
            // Leaves held back only by this entry are now dispatchable.
            pumpGatedLeaves();
        }
    }

    /**
     * Restores the expansion entry of the enclosing descriptor after a nested one finished.
     *
     * @param key the enclosing descriptor's expansion entry, or {@code null}
     */
    private void restoreExpansion(final ExpansionKey key) {
        if (key == null) {
            currentExpansion.remove();
        } else {
            currentExpansion.set(key);
        }
    }

    /**
     * Blocks until leaf capacity has been reserved for {@code leaf}.
     *
     * <p>Only reachable for a leaf that arrives at execution without a promotion reservation, which
     * in practice means the root leaf. Nothing else is running at that point, so the first attempt
     * succeeds; the loop exists so the invariant holds even for unusual entry points.
     *
     * @param leaf the leaf descriptor about to run; must not be {@code null}
     */
    private void awaitLeafCapacity(final MutableDescriptor leaf) {
        while (!tryReserveLeafCapacity(leaf)) {
            // Consume a stray interrupt flag so the park below actually blocks.
            Thread.interrupted();
            LockSupport.parkNanos(LEAF_CAPACITY_PARK_NANOS);
        }
    }

    /**
     * Executes a leaf child inline on the calling thread, accounting for it as an admission.
     *
     * <p>The caller has already incremented the parent's pending-child count, so the child's
     * completion decrements it and advances the parent exactly as for a queued child.
     *
     * @param child the child descriptor to execute; must not be {@code null}
     * @param mode the execution mode; must not be {@code null}
     * @param parentContext the parent's execution context; must not be {@code null}
     */
    boolean executeChildInline(
            final MutableDescriptor child, final ExecutionMode mode, final ConcreteContext parentContext) {
        var outcome = prepareScheduledTask(child, parentContext);
        if (outcome.future != null) {
            // Already terminal or the scheduler is closing; prepareScheduledTask has reported the
            // child as completed.
            return true;
        }
        var reserveCapacity = mode == ExecutionMode.RUN && child.isLeafAction();
        if (reserveCapacity && !tryReserveLeafCapacity(child)) {
            // The global cap or a capped ancestor is full: leave the child unscheduled so the
            // caller queues it, where it waits in rank order for capacity instead of occupying
            // this thread.
            return false;
        }
        try {
            child.markScheduled();
        } catch (Throwable t) {
            if (reserveCapacity) {
                releaseLeafCapacity(child);
            }
            throw t;
        }
        inlineChildExecutionCounter.incrementAndGet();
        inlineContinuationDepth.set(inlineContinuationDepth.get() + 1);
        try {
            // Running a child body inline means the parent is no longer expanding: drop its entry so
            // later siblings are not held back for the duration of this body. The parent re-enters
            // the set only if it starts another admission step on this thread.
            suspendExpansion();
            executeDescriptorWithCompletion(child, outcome.context, mode, null, null, reserveCapacity);
        } finally {
            var depth = inlineContinuationDepth.get() - 1;
            if (depth <= 0) {
                inlineContinuationDepth.remove();
            } else {
                inlineContinuationDepth.set(depth);
            }
        }
        return true;
    }

    /**
     * Runs one continuation on the current thread and maintains the inline nesting depth.
     *
     * @param node the node whose continuation to advance; must not be {@code null}
     */
    private void runContinuationInline(final ExecutionNode node) {
        inlineContinuationCounter.incrementAndGet();
        inlineContinuationDepth.set(inlineContinuationDepth.get() + 1);
        try {
            runContinuationOnce(node);
        } finally {
            var depth = inlineContinuationDepth.get() - 1;
            if (depth <= 0) {
                inlineContinuationDepth.remove();
            } else {
                inlineContinuationDepth.set(depth);
            }
        }
    }

    void executeContinuationAfter(final ExecutionNode node, final long delay, final TimeUnit unit) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(unit, "unit is null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be negative, was: " + delay);
        }
        if (closing || delayExecutor.isShutdown()) {
            executeContinuation(node);
            return;
        }
        try {
            var future = delayExecutor.schedule(
                    () -> {
                        delayedContinuations.remove(node);
                        executeContinuation(node);
                    },
                    delay,
                    unit);
            var existing = delayedContinuations.putIfAbsent(node, future);
            if (existing != null) {
                future.cancel(false);
            } else if (closing && delayedContinuations.remove(node, future)) {
                future.cancel(false);
                executeContinuation(node);
            }
        } catch (RejectedExecutionException e) {
            executeContinuation(node);
        }
    }

    /**
     * Runs an arbitrary task once after the given delay on the scheduler's delay executor.
     *
     * <p>The task is cancelled if the scheduler is closed before it fires. Used for strategy
     * deadlines (such as {@code Timeout}) that must apply while a child admission is deferred.
     *
     * @param task the task to run; must not be {@code null}
     * @param delay the delay; must not be negative
     * @param unit the delay unit; must not be {@code null}
     * @throws NullPointerException if {@code task} or {@code unit} is {@code null}
     * @throws IllegalArgumentException if {@code delay} is negative
     */
    void executeAfterDelay(final Runnable task, final long delay, final TimeUnit unit) {
        Objects.requireNonNull(task, "task is null");
        Objects.requireNonNull(unit, "unit is null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be negative, was: " + delay);
        }
        if (closing || delayExecutor.isShutdown()) {
            return;
        }
        try {
            delayExecutor.schedule(task, delay, unit);
        } catch (RejectedExecutionException ignored) {
            // Closing raced the schedule; the task is dropped, matching close-time cancellation.
        }
    }

    /**
     * Runs one continuation once: advances the node's strategy, then finalizes the descriptor
     * (listener, scheduling future, parent notification) if the node completed.
     *
     * @param node the execution node whose continuation to run; must not be {@code null}
     */
    private void runContinuationOnce(final ExecutionNode node) {
        var descriptor = node.descriptor;
        if (descriptor.executionNode() != node) {
            return;
        }
        var originalThreadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(Listeners.formatIdPath(descriptor));

            // During shutdown, advancing the strategy would schedule more children that the
            // closing scheduler rejects, each rejection re-triggering this continuation —
            // O(children) work per node that races with close()'s termination and can leave
            // the tree non-terminal. Instead, finalize the node once immediately so the
            // descriptor tree completes in O(depth) and callers never hang.
            if (executor.isShutdown()) {
                if (node.finalized.compareAndSet(false, true)) {
                    finalizeNode(node, descriptor, schedulerClosingException());
                }
                return;
            }

            node.continuation.run();

            // If the continuation cleared the executionNode, the node was completed.
            // Use CAS on the finalized flag to ensure only one continuation finalizes.
            if (descriptor.executionNode() != node && node.finalized.compareAndSet(false, true)) {
                finalizeNode(node, descriptor, null);
            }
        } catch (Throwable t) {
            var cause = Throwables.unwrap(t);
            if (node.finalized.compareAndSet(false, true)) {
                finalizeNode(node, descriptor, cause);
            }
        } finally {
            Thread.currentThread().setName(originalThreadName);
        }
    }

    /**
     * Finalizes a deferred execution node by completing {@code nodeCompletion} so that
     * {@code executeDescriptorWithCompletion}'s {@code managedJoin} returns.
     *
     * <p>{@code onAfterExecution}, callback invocation, scheduling future completion,
     * and {@code childCompleted} are NOT called here. They are handled by
     * {@code executeDescriptorWithCompletion} after {@code managedJoin} returns,
     * preserving the main-branch ordering and the same-thread listener bracket
     * contract.
     *
     * @param node the execution node to finalize
     * @param descriptor the descriptor being finalized
     * @param cause {@code null} for successful completion, non-null for failure;
     *     when non-null the descriptor's execution node is cleared and its status
     *     is set to terminal
     */
    private void finalizeNode(final ExecutionNode node, final MutableDescriptor descriptor, final Throwable cause) {
        if (cause != null) {
            StackTracePruner.prune(cause);
            recordUnrecoverableFailure(cause);
            if (cause instanceof RejectedExecutionException) {
                abortIncompleteDescendants(descriptor, cause);
            }
            descriptor.setExecutionNode(null);
            ensureTerminalStatus(descriptor, cause);
        }
        node.nodeCompletion.complete(null);
        wakeParkedWaiters();
    }

    /**
     * Called when a child descriptor has completed execution.
     *
     * <p>Decrements the parent's pending child count and schedules the parent's
     * continuation via {@link #executeContinuation(ExecutionNode)}. The continuation
     * runs after every child completion so that coordination strategies can admit
     * more children (Parallel), advance to the next child (Sequential), or transition
     * lifecycle phases.
     *
     * <p>If the parent has no execution node (e.g., it's a synchronous-only coordination
     * node), this method is a no-op for that parent — such parents handle child
     * completion inline.
     *
     * @param child the completed child descriptor; must not be {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public void childCompleted(final Descriptor child) {
        Objects.requireNonNull(child, "child is null");
        var parent = child.parent().orElse(null);
        if (parent == null) {
            return;
        }
        if (!(parent instanceof MutableDescriptor mutableParent)) {
            return;
        }
        var parentNode = mutableParent.executionNode();
        if (parentNode == null) {
            return;
        }
        var remainingChildren = parentNode.decrementPendingChildren();
        if (remainingChildren < 0) {
            return;
        }
        if (parentNode.continueOnEveryChildCompletion || remainingChildren == 0) {
            executeContinuation(parentNode);
        }
    }

    /**
     * Executes a descriptor synchronously on the calling thread.
     *
     * @param descriptor the descriptor to execute; must not be {@code null}
     * @param context the execution context; must not be {@code null}
     * @param mode the execution mode; must not be {@code null}
     */
    public void executeDescriptor(
            final MutableDescriptor descriptor, final ConcreteContext context, final ExecutionMode mode) {
        executeDescriptorWithCompletion(descriptor, context, mode, null, null, false);
    }

    private void executeDescriptorWithCompletion(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final ExecutionMode mode,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback,
            final boolean leafCapacityReserved) {
        Objects.requireNonNull(mode, "mode is null");
        // Entry point for descriptors that reach execution without passing admission
        // (`executeDescriptor` on a root). Compiling here keeps every descriptor that can produce
        // queued work — and every descriptor tested for ancestry — covered by the same gate.
        DescriptorOrder.compile(descriptor);
        if (descriptor.isCompleted()) {
            if (future != null) {
                completeExceptionally(
                        descriptor, future, new RejectedExecutionException("descriptor already terminal"));
                childCompleted(descriptor);
            } else {
                descriptor.completeFuture();
            }
            return;
        }
        Throwable executionError = null;
        var leafAction = descriptor.isLeafAction();
        var acquiredPermit = false;
        var executed = false;
        var callbackLifecycleStarted = false;
        var deferred = false;
        var capacityReserved = leafCapacityReserved;
        ExpansionKey expansion = null;
        var priorExpansion = currentExpansion.get();

        try {
            if (!leafAction && mode == ExecutionMode.RUN) {
                expansion = new ExpansionKey(descriptor.orderTreeId(), descriptor.orderRank());
                expandingCoordination.add(expansion);
                currentExpansion.set(expansion);
            }
            if (leafAction && mode == ExecutionMode.RUN && !capacityReserved) {
                // A leaf that reaches execution without passing admission (a root leaf, or an
                // inline leaf whose reservation was made by its parent) reserves its capacity here
                // so the global cap and every capped ancestor are still honoured.
                awaitLeafCapacity(descriptor);
                capacityReserved = true;
            }
            if (leafAction) {
                leafPermits.acquire();
                acquiredPermit = true;
                runningLeafCounter.incrementAndGet();
            }

            if (descriptor instanceof ConcreteDescriptor concrete) {
                concrete.setExecutingThread(Thread.currentThread());
            }

            executed = true;
            var originalThreadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(Listeners.formatIdPath(descriptor));
                context.listener().onBeforeExecution(descriptor);
                descriptor.setStatus(Status.RUNNING);
                var skipExecution = wouldSkipForFailFast(context);
                if (skipExecution) {
                    // Cascade SKIP to the subtree so no descendant is left PENDING,
                    // matching normal SKIP-mode propagation (skipAction -> runChildren).
                    // Deliberately outside closingLock: the cascade executes children
                    // synchronously and may block on leaf permits.
                    context.runChildren(ExecutionMode.SKIP);
                    descriptor.setStatus(Status.skipped("fail fast"));
                }
                if (!skipExecution && callback != null) {
                    callbackLifecycleStarted = true;
                    callback.onAdmitted();
                    callback.onExecutionStart();
                }
                if (!skipExecution) {
                    try {
                        var actionStatus = executeAction(descriptor, context, mode);
                        if (actionStatus.isRunning()) {
                            deferred = true;
                        } else {
                            descriptor.setStatus(actionStatus);
                        }
                    } catch (Throwable t) {
                        var cause = Throwables.unwrap(t);
                        if (cause instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        recordUnrecoverableFailure(cause);
                        StackTracePruner.prune(cause);
                        descriptor.setStatus(Status.fromThrowable(UserCodeException.wrap(cause)));
                    }
                }
                if (deferred) {
                    // The admission step is finished: this node is no longer expanding, so leaves
                    // that were held back only to preserve depth-first order may proceed.
                    if (expansion != null) {
                        expandingCoordination.remove(expansion);
                        expansion = null;
                        restoreExpansion(priorExpansion);
                        pumpGatedLeaves();
                    }
                    // Wait for the continuation to set the descriptor's terminal status.
                    // managedJoin work-steals from the executor queue (and the shutdown
                    // drain during close()) so the worker does useful work while waiting.
                    // After this returns, listener/callback/future/parent publication still
                    // happens below on this original descriptor execution thread.
                    var node = descriptor.executionNode();
                    if (node != null) {
                        context.scheduler().managedJoin(node.nodeCompletion);
                    }
                }
                // onAfterExecution fires here, on the same thread as
                // onBeforeExecution, with the descriptor in terminal status.
                // This preserves the listener bracket contract.
                context.listener().onAfterExecution(descriptor);
            } finally {
                Thread.currentThread().setName(originalThreadName);
            }
        } catch (Throwable t) {
            executionError = Throwables.unwrap(t);
            if (executionError instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordUnrecoverableFailure(executionError);
        } finally {
            if (descriptor instanceof ConcreteDescriptor concrete) {
                concrete.setExecutingThread(null);
            }
            if (!executed || executionError != null) {
                ensureTerminalStatus(context, executionError);
            }
            if (acquiredPermit) {
                runningLeafCounter.decrementAndGet();
                leafPermits.release();
            }
            if (expansion != null) {
                expandingCoordination.remove(expansion);
                restoreExpansion(priorExpansion);
                // This descriptor stopped expanding without deferring (for example it completed
                // synchronously): leaves it was holding back may now be dispatched.
                pumpGatedLeaves();
            }
        }

        // Consume any lingering interrupt.
        Thread.interrupted();

        try {
            publishDescriptorCompletion(
                    descriptor, context, future, callback, callbackLifecycleStarted, executionError);
        } finally {
            if (capacityReserved) {
                // Released only after this descriptor's completion is published, so a leaf that
                // starts next observes this leaf's status (fail-fast in particular) instead of
                // racing ahead of it. Frees the global and per-ancestor slots and fills whatever
                // became eligible.
                releaseLeafCapacity(descriptor);
            }
        }
    }

    private void publishDescriptorCompletion(
            final MutableDescriptor descriptor,
            final ConcreteContext context,
            final CompletableFuture<Descriptor> future,
            final ExecutionCallback callback,
            final boolean callbackLifecycleStarted,
            final Throwable initialExecutionError) {
        var executionError = initialExecutionError;
        if (executionError == null) {
            executionError = extractPostExecutionError(context);
        }

        recordExecutionFailureForFailFast(executionError);

        Throwable callbackFailure = null;
        if (callbackLifecycleStarted && callback != null) {
            try {
                callback.onExecutionComplete(executionError);
            } catch (Throwable t) {
                callbackFailure = Throwables.unwrap(t);
            }
        }

        try {
            if (executionError == null) {
                completeSuccessfully(descriptor, future);
            } else {
                completeExceptionally(descriptor, future, executionError);
            }
        } finally {
            childCompleted(descriptor);
        }

        if (callbackFailure != null) {
            handleCallbackFailure(callbackFailure);
        }
    }

    private static Status executeAction(
            final MutableDescriptor descriptor, final ConcreteContext context, final ExecutionMode mode) {
        var status =
                switch (mode) {
                    case RUN -> runAction(descriptor, context);
                    case SKIP -> skipAction(context);
                    case ABORT -> {
                        context.runChildren(ExecutionMode.ABORT);
                        yield Status.ABORTED;
                    }
                };
        if (status == null) {
            throw new IllegalStateException("Action returned null status");
        }
        if (status.isPending()) {
            throw new IllegalStateException("Action returned non-terminal status: " + status.name());
        }
        return status;
    }

    private static Status runAction(final MutableDescriptor descriptor, final ConcreteContext context) {
        return ActionExecutionStrategies.execute(descriptor.action(), context);
    }

    private static Status skipAction(final ConcreteContext context) {
        context.runChildren(ExecutionMode.SKIP);
        return Status.SKIPPED;
    }

    private static void ensureTerminalStatus(final ConcreteContext context, final Throwable throwable) {
        var descriptor = context.descriptor();
        if (descriptor.status().isPending()) {
            // The descriptor state machine requires PENDING -> RUNNING -> terminal.
            descriptor.setStatus(Status.RUNNING);
        }
        ensureTerminalStatus(descriptor, throwable);
    }

    private static void ensureTerminalStatus(final MutableDescriptor descriptor, final Throwable throwable) {
        try {
            var status = descriptor.status();
            if (status.isPending() || status.isRunning()) {
                descriptor.setStatus(failedStatus(throwable));
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to set terminal status on descriptor " + descriptor.id(), e);
        }
    }

    private static Status failedStatus(final Throwable throwable) {
        var failure = throwable != null ? throwable : new IllegalStateException("execution failed before action work");
        StackTracePruner.prune(failure);
        return Status.failed(failure.getMessage() != null ? failure.getMessage() : "action failed", failure);
    }

    private static RejectedExecutionException schedulerClosingException() {
        return new RejectedExecutionException("Scheduler is closing");
    }

    private static void abortIncompleteDescendants(final MutableDescriptor descriptor, final Throwable cause) {
        var message = cause.getMessage() != null ? cause.getMessage() : "Scheduler is closing";
        var descendantStatus = Status.aborted("cancelled by ancestor: " + message, cause);
        descriptor.before().ifPresent(child -> abortIfIncomplete(child, descendantStatus, cause));
        for (var child : descriptor.children()) {
            abortIfIncomplete(child, descendantStatus, cause);
        }
        descriptor.after().ifPresent(child -> abortIfIncomplete(child, descendantStatus, cause));
    }

    private static void abortIfIncomplete(
            final Descriptor descriptor, final Status descendantStatus, final Throwable cause) {
        if (descriptor instanceof MutableDescriptor mutableDescriptor && !mutableDescriptor.isCompleted()) {
            mutableDescriptor.abort(descendantStatus, cause);
        }
    }

    private static Throwable extractPostExecutionError(final ConcreteContext context) {
        var status = context.descriptor().status();
        if (status.isFailed() || status.isAborted()) {
            return status.throwable().orElse(new FailException(status.message().orElse("action failed")));
        }
        return null;
    }

    private static void completeSuccessfully(
            final MutableDescriptor descriptor, final CompletableFuture<Descriptor> future) {
        if (future != null) {
            future.complete(descriptor);
        } else {
            descriptor.completeFuture();
        }
    }

    private static void completeExceptionally(
            final MutableDescriptor descriptor, final CompletableFuture<Descriptor> future, final Throwable throwable) {
        if (future != null) {
            future.completeExceptionally(throwable);
        } else {
            descriptor.completeFutureExceptionally(throwable);
        }
    }

    private void publishRejectedCompletion(
            final MutableDescriptor descriptor, final ExecutionCallback callback, final Throwable error) {
        var callbackFailure = notifyRejectedCompletion(callback, error);
        try {
            childCompleted(descriptor);
        } finally {
            if (callbackFailure != null) {
                handleCallbackFailure(callbackFailure);
            }
        }
    }

    private static Throwable notifyRejectedCompletion(final ExecutionCallback callback, final Throwable error) {
        if (callback == null) {
            return null;
        }
        try {
            callback.onExecutionComplete(error);
            return null;
        } catch (Throwable callbackFailure) {
            return Throwables.unwrap(callbackFailure);
        }
    }

    private void handleCallbackFailure(final Throwable callbackFailure) {
        recordUnrecoverableFailure(callbackFailure);
        UnrecoverableErrors.rethrowIfUnrecoverable(callbackFailure);
        System.err.println("ExecutionCallback.onExecutionComplete threw exception: " + callbackFailure.getMessage());
        callbackFailure.printStackTrace(System.err);
    }

    private ThreadFactory defaultThreadFactory() {
        return runnable -> {
            Runnable lifecycleTrackedRunnable = () -> {
                schedulerWorker.set(true);
                try {
                    runnable.run();
                } finally {
                    schedulerWorker.remove();
                }
            };
            var thread =
                    new Thread(lifecycleTrackedRunnable, THREAD_NAME + "-" + workerThreadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private ThreadPoolExecutor createExecutor(final ThreadFactory threadFactory) {
        var executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(queueCapacity, PRIORITY_COMPARATOR),
                threadFactory);
        executor.allowCoreThreadTimeOut(allowCoreThreadTimeout);
        return executor;
    }

    private void runQueuedTask(final Runnable task) {
        var wasSchedulerWorker = Boolean.TRUE.equals(schedulerWorker.get());
        schedulerWorker.set(true);
        try {
            task.run();
        } finally {
            if (wasSchedulerWorker) {
                schedulerWorker.set(true);
            } else {
                schedulerWorker.remove();
            }
        }
    }

    private void pushIsolationFrame(
            final String lockName, final MutableDescriptor descriptor, final ReentrantLock lock) {
        isolationFrames.get().add(new IsolationFrame(lockName, descriptor, lock));
    }

    private PrioritizedTask pollOwnedIsolationTask() {
        var frames = isolationFrames.get();
        for (var i = frames.size() - 1; i >= 0; i--) {
            var frame = frames.get(i);
            var state = isolationStates.get(frame.lockName());
            if (state == null) {
                continue;
            }
            synchronized (state) {
                if (state.ownerThread == Thread.currentThread()) {
                    var task = state.ownerTasks.poll();
                    if (task != null) {
                        return task;
                    }
                }
            }
        }
        return null;
    }

    private void popIsolationFrame(final String lockName, final MutableDescriptor descriptor) {
        var frames = isolationFrames.get();
        if (frames.isEmpty()) {
            throw new IllegalStateException("No isolation lock frame to release: " + lockName);
        }
        var frame = frames.remove(frames.size() - 1);
        if (!frame.lockName().equals(lockName) || frame.descriptor() != descriptor) {
            throw new IllegalStateException("Isolation lock frame release order mismatch: " + lockName);
        }
        if (frames.isEmpty()) {
            isolationFrames.remove();
        }
    }

    /**
     * Wrapper that applies compiled-order ranking to a task and holds execution fields directly
     * to avoid per-task lambda allocation.
     */
    private static final class PrioritizedTask implements ScheduledTask {
        private final long orderTreeId;
        private final long orderRank;
        private final long sequence;
        private final MutableDescriptor descriptor;
        private final ConcreteContext context;
        private final ExecutionMode mode;
        private final CompletableFuture<Descriptor> future;
        private final ExecutionCallback callback;
        private final Semaphore queuePermits;
        private final Scheduler scheduler;

        /**
         * Whether this task currently holds a queue reservation. A reservation is acquired at
         * scheduling time, released when the task starts running, released when the task is
         * relinquished (parked), and re-acquired when a relinquished task is redispatched.
         */
        private volatile boolean reservationHeld;

        /**
         * Whether this task holds a leaf-capacity reservation made when it was promoted out of the
         * gated ready set. Released when the leaf body completes.
         */
        private volatile boolean capacityReserved;

        /**
         * Drops this task's queued-coordination marker. Idempotent: the marker is only registered
         * for a non-leaf task and only once.
         */
        void clearPendingCoordination() {
            scheduler.clearPendingCoordination(this);
        }

        PrioritizedTask(
                final long sequence,
                final MutableDescriptor descriptor,
                final ConcreteContext context,
                final ExecutionMode mode,
                final CompletableFuture<Descriptor> future,
                final ExecutionCallback callback,
                final Semaphore queuePermits,
                final Scheduler scheduler) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
            // Taken from the descriptor rather than passed in: it is compiled and frozen before a
            // task is built, so this task cannot disagree with the descriptor it ranks for.
            this.orderTreeId = descriptor.orderTreeId();
            this.orderRank = descriptor.orderRank();
            this.sequence = sequence;
            this.context = Objects.requireNonNull(context, "context is null");
            this.mode = Objects.requireNonNull(mode, "mode is null");
            this.future = future;
            this.callback = callback;
            this.queuePermits = Objects.requireNonNull(queuePermits, "queuePermits is null");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
            this.reservationHeld = true;
        }

        @Override
        public void run() {
            if (!scheduler.tryEnterScheduledIsolation(this)) {
                // Deferred behind an isolation lock: the reservation stays held until the task is
                // redispatched and actually starts.
                return;
            }
            try {
                reservationHeld = false;
                queuePermits.release();
                clearPendingCoordination();
                scheduler.signalCapacityAvailable();
                scheduler.executeDescriptorWithCompletion(
                        descriptor, context, mode, future, callback, capacityReserved);
            } finally {
                scheduler.exitScheduledIsolationIfHeld(this);
            }
        }

        @Override
        public long orderTreeId() {
            return orderTreeId;
        }

        @Override
        public long orderRank() {
            return orderRank;
        }

        @Override
        public long sequence() {
            return sequence;
        }

        @Override
        public MutableDescriptor descriptor() {
            return descriptor;
        }

        private String scheduledIsolationLockName() {
            if (mode != ExecutionMode.RUN || descriptor.isCompleted()) {
                return null;
            }
            if (descriptor.action() instanceof Isolated isolated) {
                return isolated.lockName();
            }
            return null;
        }
    }

    /**
     * Base class for queued tasks that advance one execution node.
     *
     * <p>Holds the node's compiled order metadata, submission sequence, node, and owning scheduler
     * shared by {@link ContinuationTask} and {@link AdmissionWakeupTask}; subclasses supply only the
     * action to perform on the node. The order is read once from the node's descriptor, which is
     * compiled and immutable for the rest of the run.
     */
    private abstract static class NodeTask implements ScheduledTask {
        private final long orderTreeId;
        private final long orderRank;
        private final long sequence;
        private final ExecutionNode node;
        private final Scheduler scheduler;

        NodeTask(final long sequence, final ExecutionNode node, final Scheduler scheduler) {
            var requiredNode = Objects.requireNonNull(node, "node is null");
            this.orderTreeId = requiredNode.descriptor.orderTreeId();
            this.orderRank = requiredNode.descriptor.orderRank();
            this.sequence = sequence;
            this.node = requiredNode;
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
        }

        @Override
        public final long orderTreeId() {
            return orderTreeId;
        }

        @Override
        public final long orderRank() {
            return orderRank;
        }

        @Override
        public final long sequence() {
            return sequence;
        }

        @Override
        public final MutableDescriptor descriptor() {
            return node.descriptor;
        }

        final ExecutionNode node() {
            return node;
        }

        final Scheduler scheduler() {
            return scheduler;
        }
    }

    /**
     * A work item that runs an execution node's continuation.
     *
     * <p>The continuation computes strategy state and terminal status, then completes the node's
     * internal completion future. Listener, callback, scheduled-future, and parent-notification
     * publication remains owned by the original descriptor execution thread.
     */
    private static final class ContinuationTask extends NodeTask {

        ContinuationTask(final long sequence, final ExecutionNode node, final Scheduler scheduler) {
            super(sequence, node, scheduler);
        }

        @Override
        public void run() {
            scheduler().runContinuationOnce(node());
        }
    }

    /**
     * A work item that wakes a coordination node whose internal child admission was deferred,
     * allowing its strategy to retry the admission now that capacity is available.
     */
    private static final class AdmissionWakeupTask extends NodeTask {

        AdmissionWakeupTask(final long sequence, final ExecutionNode node, final Scheduler scheduler) {
            super(sequence, node, scheduler);
        }

        @Override
        public void run() {
            scheduler().runAdmissionRetryOnce(node());
        }
    }

    private record IsolationFrame(String lockName, MutableDescriptor descriptor, ReentrantLock lock) {}

    private static final class IsolationState {
        private final Queue<PrioritizedTask> deferredTasks = new PriorityQueue<>(TASK_ORDER);
        private final Queue<PrioritizedTask> ownerTasks = new PriorityQueue<>(TASK_ORDER);
        private MutableDescriptor ownerDescriptor;
        private Thread ownerThread;
        private PrioritizedTask dispatchedTask;
    }
}
