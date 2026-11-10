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

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.listener.Listeners;
import nonapi.org.paramixel.support.StackTracePruner;
import nonapi.org.paramixel.support.Throwables;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Configuration;
import org.paramixel.api.ContextConsumer;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Until;

/**
 * Depth-first execution engine for a compiled {@link ExecutionPlan}.
 *
 * <p>Execution is recursive. A {@code Parallel} node is run by up to {@code cap} <em>lanes</em> that
 * share a cursor over its children (see {@link ParallelFrame}): the coordinating thread is always
 * the first lane, and further lanes run on worker threads while permits are available. A lane
 * that finishes a child immediately claims the next unstarted one, so children start in declaration
 * order and a child that blocks can never keep its later siblings from starting.
 *
 * <p>{@code parallelism} is a hard ceiling on executing stacks: the calling thread holds one permit
 * for the whole run and every worker holds one while it runs.
 * Nothing ever blocks waiting for a permit. A frame that wants another lane when none is free
 * registers with the {@link Dispatcher}, and the next worker that runs out of work serves it on an
 * empty stack instead of releasing its permit. Coordinators wait only for lanes that are already
 * running, so the wait-for graph always ends at an executing stack. Resource-holding branches are
 * therefore bounded by {@code parallelism}.
 *
 * <p>A {@code Timeout} runs its child on a dedicated thread that takes over the slot of the parked
 * waiting thread, so the deadline can always preempt the child and no permit is needed. Only
 * executing work is bounded by {@code parallelism}; parked waiters are not counted. At the deadline
 * the child's subtree is cancelled, but lifecycle {@code after} actions whose {@code before} was
 * attempted still run, and the {@code Timeout} completes only after that cleanup has finished. If
 * the subtree makes no cleanup progress for a grace period (a leaf ignoring interruption), the
 * {@code Timeout} abandons it and that thread may keep running in the background.
 *
 * <p>Deliberate behavioral differences from the previous queue-based scheduler: inter-iteration
 * delays occupy the executing branch, and there is no work stealing.
 */
final class Engine implements AutoCloseable {

    private static final String THREAD_NAME = "paramixel-engine";
    private static final String TIMED_THREAD_NAME = "paramixel-timed";
    private static final long TERMINATION_TIMEOUT_SECONDS = 5L;
    private static final long CANCELLATION_GRACE_SECONDS = 5L;
    private static final long CANCELLATION_POLL_MILLIS = 25L;

    private final int parallelism;
    private final Dispatcher dispatcher;
    private final ThreadPoolExecutor pool;
    private final ExecutorService timedBranches;
    private final ScheduledExecutorService timers;
    private final Set<ParallelFrame> liveFrames = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> isolationDepth = ThreadLocal.withInitial(() -> 0);
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile boolean closing;
    private volatile boolean failFast;
    private volatile boolean failureOccurred;
    private volatile Throwable unrecoverableFailure;

    Engine(final int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        this.parallelism = parallelism;
        this.dispatcher = new Dispatcher(parallelism);
        this.pool = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                threadFactory());
        this.timedBranches = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable, TIMED_THREAD_NAME + "-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        this.timers = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, THREAD_NAME + "-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    private ThreadFactory threadFactory() {
        return runnable -> {
            var thread = new Thread(runnable, THREAD_NAME + "-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    int parallelism() {
        return parallelism;
    }

    /**
     * Returns the number of unallocated permits.
     *
     * @return the free permit count
     */
    int availablePermits() {
        return dispatcher.availablePermits();
    }

    void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    Throwable unrecoverableFailure() {
        return unrecoverableFailure;
    }

    /**
     * Compiles and executes the supplied descriptor tree on the calling thread.
     *
     * @param root the discovered root descriptor; must not be {@code null}
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     */
    void execute(final MutableDescriptor root, final Configuration configuration, final Listener listener) {
        Objects.requireNonNull(root, "root is null");
        Objects.requireNonNull(configuration, "configuration is null");
        Objects.requireNonNull(listener, "listener is null");
        var plan = ExecutionPlan.compile(root);
        var context = new ConcreteContext(configuration, listener, root, new InstanceHolder());
        // The calling thread is the root lane, so it is charged against the same budget as workers.
        var callerSlot = dispatcher.tryAcquire();
        try {
            run(plan.root(), context, ExecutionMode.RUN);
            root.completeFuture();
        } catch (Throwable t) {
            var cause = Throwables.unwrap(t);
            recordUnrecoverableFailure(cause);
            root.completeFutureExceptionally(cause);
        } finally {
            if (callerSlot) {
                dispatcher.release();
            }
        }
    }

    /**
     * Returns the named fair lock used by {@code Isolated} actions.
     *
     * @param lockName the lock name; must not be {@code null}
     * @return the lock; never {@code null}
     */
    ReentrantLock lock(final String lockName) {
        return locks.computeIfAbsent(
                Objects.requireNonNull(lockName, "lockName is null"), name -> new ReentrantLock(true));
    }

    // ── Node execution ─────────────────────────────────────────────────

    private Status run(final ExecutionPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
        var descriptor = node.descriptor;
        var currentStatus = descriptor.status();
        if (!currentStatus.isPending() && !currentStatus.isRunning()) {
            return currentStatus;
        }
        var thread = Thread.currentThread();
        var originalName = thread.getName();
        var concrete = descriptor instanceof ConcreteDescriptor c ? c : null;
        var leaf = node.kind == ExecutionPlan.Kind.TERMINAL && concrete != null;
        Throwable failure = null;
        Status status = null;
        try {
            thread.setName(Listeners.formatIdPath(descriptor));
            if (leaf) {
                concrete.setExecutingThread(thread);
            }
            context.listener().onBeforeExecution(descriptor);
            // Atomic with abort(): a descriptor cancelled after the check above must not run.
            if (descriptor.tryStart()) {
                status = execute(node, context, mode);
            }
        } catch (Throwable t) {
            failure = Throwables.unwrap(t);
            if (failure instanceof InterruptedException) {
                thread.interrupt();
            }
            recordUnrecoverableFailure(failure);
            StackTracePruner.prune(failure);
            status = Status.fromThrowable(UserCodeException.wrap(failure));
        } finally {
            if (leaf) {
                concrete.setExecutingThread(null);
            }
            Thread.interrupted();
            terminalize(descriptor, status, failure);
            recordFailureForFailFast(descriptor);
            try {
                context.listener().onAfterExecution(descriptor);
            } catch (Throwable ignored) {
                // Listener failures are isolated by SafeListener in production wiring.
            }
            thread.setName(originalName);
        }
        return descriptor.status();
    }

    private Status execute(final ExecutionPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
        // Fail fast takes precedence over a skip mode so the reason is retained on the descriptor.
        if (shouldSkipForFailFast(node.descriptor)) {
            runDescendants(node, context, ExecutionMode.SKIP);
            return Status.skipped("fail fast");
        }
        if (mode == ExecutionMode.SKIP) {
            runDescendants(node, context, ExecutionMode.SKIP);
            return Status.SKIPPED;
        }
        if (mode == ExecutionMode.ABORT) {
            runDescendants(node, context, ExecutionMode.ABORT);
            return Status.ABORTED;
        }
        return switch (node.kind) {
            case TERMINAL -> runTerminal(node, context);
            case SEQUENTIAL -> runSequential(node, context);
            case PARALLEL -> runParallel(node, context);
            case LIFECYCLE -> runLifecycle(node, context);
            case LOOP -> runLoop(node, context);
            case REPEAT -> runRepeat(node, context);
            case UNTIL -> runUntil(node, context);
            case TIMEOUT -> runTimeout(node, context);
            case ISOLATED -> runIsolated(node, context);
            case CONDITIONAL -> runConditional(node, context);
        };
    }

    // ── Controls ───────────────────────────────────────────────────────

    private Status runTerminal(final ExecutionPlan.Node node, final ConcreteContext context) {
        try {
            consumerOf(node.control).accept(context);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return Status.PASSED;
    }

    private static ContextConsumer consumerOf(final Action action) {
        if (action instanceof Step step) {
            return step.throwableConsumer();
        }
        if (action instanceof Assert assertAction) {
            return assertAction.throwableConsumer();
        }
        if (action instanceof Delay delay) {
            return delay.throwableConsumer();
        }
        throw new IllegalStateException(
                "not a terminal action: " + action.getClass().getName());
    }

    private Status runSequential(final ExecutionPlan.Node node, final ConcreteContext context) {
        var aggregator = new StatusAccumulator();
        var mode = ExecutionMode.RUN;
        for (var child : node.children) {
            var status = run(child, childContext(context, child.descriptor), mode);
            aggregator.include(status);
            if (node.dependent && mode == ExecutionMode.RUN && !status.isPassed() && !status.isAborted()) {
                mode = ExecutionMode.SKIP;
            }
        }
        return aggregator.status();
    }

    private Status runParallel(final ExecutionPlan.Node node, final ConcreteContext context) {
        if (node.children.length == 0) {
            return Status.PASSED;
        }
        var frame = new ParallelFrame(node, context, effectiveCap(node));
        liveFrames.add(frame);
        try {
            var first = frame.claim();
            recruit(frame);
            runChild(frame, first);
            runLane(frame);
            if (frame.awaitWorkers()) {
                Thread.currentThread().interrupt();
            }
            return frame.status();
        } finally {
            liveFrames.remove(frame);
            dispatcher.forget(frame);
        }
    }

    private int effectiveCap(final ExecutionPlan.Node node) {
        var cap = Math.min(node.cap, parallelism);
        if (isolationDepth.get() > 0) {
            cap = 1;
        }
        return Math.max(1, cap);
    }

    /**
     * Starts worker lanes for the frame while it has unstarted children, is below its cap and permits
     * are free. If no permit is free the frame is queued with the dispatcher and will be served by
     * the next worker that runs out of work.
     */
    private void recruit(final ParallelFrame frame) {
        while (frame.tryJoin()) {
            if (!dispatcher.acquireOrQueue(frame)) {
                frame.leave();
                return;
            }
            try {
                pool.execute(() -> work(() -> runWorkerLane(frame)));
            } catch (RejectedExecutionException e) {
                dispatcher.release();
                frame.leave();
                return;
            }
        }
    }

    /**
     * Body of a pool task. The caller holds one permit; after the job it keeps serving hungry frames
     * and releases the permit only when none is waiting.
     */
    private void work(final Runnable job) {
        try {
            job.run();
        } catch (Throwable t) {
            recordUnrecoverableFailure(t);
        }
        for (var frame = dispatcher.handOffOrRelease(); frame != null; frame = dispatcher.handOffOrRelease()) {
            try {
                runWorkerLane(frame);
            } catch (Throwable t) {
                recordUnrecoverableFailure(t);
            }
        }
    }

    private void runWorkerLane(final ParallelFrame frame) {
        try {
            recruit(frame);
            runLane(frame);
        } finally {
            frame.leave();
        }
    }

    private void runLane(final ParallelFrame frame) {
        for (var child = frame.claim(); child != null; child = frame.claim()) {
            runChild(frame, child);
        }
    }

    private void runChild(final ParallelFrame frame, final ExecutionPlan.Node child) {
        frame.include(run(child, childContext(frame.context(), child.descriptor), ExecutionMode.RUN));
    }

    private Status runLifecycle(final ExecutionPlan.Node node, final ConcreteContext context) {
        var holder = node.control instanceof Instance ? new InstanceHolder() : context.instanceHolder();
        var scoped = new ConcreteContext(context.configuration(), context.listener(), context.descriptor(), holder);
        var aggregator = new StatusAccumulator();
        if (node.before != null) {
            aggregator.include(run(node.before, childContext(scoped, node.before.descriptor), ExecutionMode.RUN));
        }
        var bodyMode =
                node.before == null || node.before.descriptor.isPassed() ? ExecutionMode.RUN : ExecutionMode.SKIP;
        for (var child : node.children) {
            aggregator.include(run(child, childContext(scoped, child.descriptor), bodyMode));
        }
        if (node.after != null) {
            aggregator.include(run(node.after, childContext(scoped, node.after.descriptor), ExecutionMode.RUN));
        }
        return aggregator.status();
    }

    private Status runLoop(final ExecutionPlan.Node node, final ConcreteContext context) {
        var loop = (Loop) node.control;
        var aggregator = new StatusAccumulator();
        for (var i = 0; i < node.children.length; i++) {
            var child = node.children[i];
            var status = run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
            aggregator.include(status);
            if (status.isAborted()) {
                skipRemaining(node, i + 1, context, aggregator);
                return Status.ABORTED;
            }
            if (loop.until().isPresent()) {
                boolean satisfied;
                try {
                    satisfied = loop.until().get().test(context);
                } catch (Throwable t) {
                    UnrecoverableErrors.rethrowIfUnrecoverable(t);
                    skipRemaining(node, i + 1, context, aggregator);
                    return Status.failed("until predicate failed: " + t.getMessage(), t);
                }
                if (satisfied) {
                    skipRemaining(node, i + 1, context, aggregator);
                    return Status.PASSED;
                }
            }
            if (loop.delay().isPresent() && i + 1 < node.children.length) {
                var nanos = delayNanos(loop.delay().get().delayForIteration(i + 1));
                if (nanos > 0) {
                    sleep(nanos);
                }
            }
        }
        return loop.until().isPresent() ? Status.FAILED : aggregator.status();
    }

    private Status runRepeat(final ExecutionPlan.Node node, final ConcreteContext context) {
        var aggregator = new StatusAccumulator();
        for (var child : node.children) {
            aggregator.include(run(child, childContext(context, child.descriptor), ExecutionMode.RUN));
        }
        return aggregator.status();
    }

    @SuppressWarnings("removal")
    private Status runUntil(final ExecutionPlan.Node node, final ConcreteContext context) {
        var until = (Until) node.control;
        var aggregator = new StatusAccumulator();
        for (var i = 0; i < node.children.length; i++) {
            var child = node.children[i];
            var status = run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
            aggregator.include(status);
            if (status.isAborted()) {
                skipRemaining(node, i + 1, context, aggregator);
                return Status.ABORTED;
            }
            boolean satisfied;
            if (until.until().isPresent()) {
                try {
                    satisfied = until.until().get().test(context);
                } catch (Throwable t) {
                    UnrecoverableErrors.rethrowIfUnrecoverable(t);
                    skipRemaining(node, i + 1, context, aggregator);
                    return Status.failed("until predicate failed: " + t.getMessage(), t);
                }
            } else {
                satisfied = status.isPassed();
            }
            if (satisfied) {
                skipRemaining(node, i + 1, context, aggregator);
                return Status.PASSED;
            }
        }
        return Status.FAILED;
    }

    private Status runTimeout(final ExecutionPlan.Node node, final ConcreteContext context) {
        var child = node.children[0];
        var millis = TimeUnit.NANOSECONDS.toMillis(node.timeoutNanos);
        var message = "timed out after " + millis + " ms";
        // Completed with the System.nanoTime() at which the child's subtree was cancelled.
        var timedOut = new CompletableFuture<Long>();
        var settled = new CompletableFuture<Void>();
        ScheduledFuture<?> deadline = null;
        if (!closing) {
            try {
                deadline = timers.schedule(
                        () -> {
                            child.descriptor.abort(Status.failed(message), new TimeoutException(message));
                            timedOut.complete(System.nanoTime());
                        },
                        node.timeoutNanos,
                        TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException e) {
                deadline = null;
            }
        }
        var childContext = childContext(context, child.descriptor);
        var isolation = isolationDepth.get();
        try {
            // The child always runs on its own thread so the deadline can preempt it. That thread takes
            // over this thread's execution slot while this thread is parked, so no permit is needed
            // and waiting here can never starve another branch.
            try {
                timedBranches.execute(() -> {
                    isolationDepth.set(isolation);
                    try {
                        run(child, childContext, ExecutionMode.RUN);
                    } finally {
                        isolationDepth.remove();
                        settled.complete(null);
                    }
                });
            } catch (RejectedExecutionException e) {
                // Shutting down: nothing can preempt, so honour the contract on this thread.
                run(child, childContext, ExecutionMode.RUN);
                settled.complete(null);
            }
            awaitTimedChild(child.descriptor, settled, timedOut, message);
            return child.descriptor.status();
        } finally {
            if (deadline != null) {
                deadline.cancel(false);
            }
            Thread.interrupted();
        }
    }

    /**
     * Waits for a timed child to settle.
     *
     * <p>Before the deadline this simply waits. Once the deadline has cancelled the subtree, the
     * cancelled work unwinds and any lifecycle cleanup it still owes runs on the child's thread;
     * this method keeps waiting while cleanup is executing, so cleanup completes before the
     * {@code Timeout} does. If the child has not settled and no cleanup has executed for a
     * continuous {@link #CANCELLATION_GRACE_SECONDS} (for example a leaf that ignores interruption),
     * the remaining subtree is abandoned: every non-terminal descendant is aborted and this method
     * returns, leaving the stuck thread to finish on its own.
     */
    private static void awaitTimedChild(
            final MutableDescriptor child,
            final CompletableFuture<Void> settled,
            final CompletableFuture<Long> timedOut,
            final String message) {
        try {
            CompletableFuture.anyOf(settled, timedOut).get();
            if (settled.isDone()) {
                return;
            }
            var graceNanos = TimeUnit.SECONDS.toNanos(CANCELLATION_GRACE_SECONDS);
            var idleSince = timedOut.get();
            while (true) {
                try {
                    settled.get(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException stillRunning) {
                    var now = System.nanoTime();
                    if (isCleanupExecuting(child)) {
                        idleSince = now;
                    } else if (now - idleSince >= graceNanos) {
                        abandon(child, message);
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns whether lifecycle cleanup is executing in a cancelled subtree. Cancellation makes
     * every cancelled leaf terminal immediately (even one still stuck executing), so the only
     * leaves that are executing and not yet terminal are preserved {@code after} actions.
     */
    private static boolean isCleanupExecuting(final Descriptor descriptor) {
        if (descriptor instanceof MutableDescriptor mutable
                && mutable.isLeafAction()
                && mutable.isExecuting()
                && !mutable.isCompleted()) {
            return true;
        }
        if (descriptor.before().map(Engine::isCleanupExecuting).orElse(false)) {
            return true;
        }
        for (var child : descriptor.children()) {
            if (isCleanupExecuting(child)) {
                return true;
            }
        }
        return descriptor.after().map(Engine::isCleanupExecuting).orElse(false);
    }

    /**
     * Aborts every non-terminal descriptor in an abandoned subtree, including preserved
     * {@code after} actions that can no longer be reached, so the result tree stays terminal.
     */
    private static void abandon(final Descriptor descriptor, final String message) {
        if (descriptor instanceof MutableDescriptor mutable && !mutable.isCompleted()) {
            mutable.abort(Status.aborted("cancelled by ancestor: " + message), new TimeoutException(message));
        }
        descriptor.before().ifPresent(before -> abandon(before, message));
        for (var child : descriptor.children()) {
            abandon(child, message);
        }
        descriptor.after().ifPresent(after -> abandon(after, message));
    }

    private Status runIsolated(final ExecutionPlan.Node node, final ConcreteContext context) {
        var lock = lock(node.lockName);
        var depth = isolationDepth.get();
        lock.lock();
        isolationDepth.set(depth + 1);
        try {
            var child = node.children[0];
            return run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
        } finally {
            isolationDepth.set(depth);
            lock.unlock();
        }
    }

    private Status runConditional(final ExecutionPlan.Node node, final ConcreteContext context) {
        var conditional = (Conditional) node.control;
        boolean condition;
        try {
            condition = conditional.condition().test(context);
        } catch (Throwable t) {
            UnrecoverableErrors.rethrowIfUnrecoverable(t);
            runDescendants(node, context, ExecutionMode.SKIP);
            return Status.failed("condition evaluation failed: " + t.getMessage(), t);
        }
        if (!condition) {
            runDescendants(node, context, ExecutionMode.SKIP);
            return Status.PASSED;
        }
        var child = node.children[0];
        return run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void skipRemaining(
            final ExecutionPlan.Node node,
            final int from,
            final ConcreteContext context,
            final StatusAccumulator aggregator) {
        for (var i = from; i < node.children.length; i++) {
            var child = node.children[i];
            aggregator.include(run(child, childContext(context, child.descriptor), ExecutionMode.SKIP));
        }
    }

    private void runDescendants(
            final ExecutionPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
        if (node.before != null) {
            run(node.before, childContext(context, node.before.descriptor), mode);
        }
        for (var child : node.children) {
            run(child, childContext(context, child.descriptor), mode);
        }
        if (node.after != null) {
            run(node.after, childContext(context, node.after.descriptor), mode);
        }
    }

    private ConcreteContext childContext(final ConcreteContext parent, final MutableDescriptor child) {
        return new ConcreteContext(parent.configuration(), parent.listener(), child, parent.instanceHolder());
    }

    private void terminalize(final MutableDescriptor descriptor, final Status status, final Throwable failure) {
        try {
            if (descriptor.isCompleted()) {
                return;
            }
            if (status != null) {
                descriptor.setStatus(status);
                return;
            }
            forceTerminal(descriptor, failure);
        } catch (IllegalStateException alreadyTerminal) {
            // A concurrent abort won the race; the descriptor is already terminal.
        }
    }

    private static void forceTerminal(final MutableDescriptor descriptor, final Throwable failure) {
        var status = descriptor.status();
        if (status.isPending()) {
            descriptor.setStatus(Status.RUNNING);
            status = descriptor.status();
        }
        if (status.isPending() || status.isRunning()) {
            var cause = failure != null ? failure : new IllegalStateException("execution failed before action work");
            StackTracePruner.prune(cause);
            descriptor.setStatus(
                    Status.failed(cause.getMessage() != null ? cause.getMessage() : "action failed", cause));
        }
    }

    private void recordFailureForFailFast(final MutableDescriptor descriptor) {
        if (!failFast) {
            return;
        }
        var status = descriptor.status();
        if (status.isFailed() || status.isAborted()) {
            failureOccurred = true;
        }
    }

    private boolean shouldSkipForFailFast(final MutableDescriptor descriptor) {
        if (!failFast || !failureOccurred) {
            return false;
        }
        return descriptor.parent().filter(parent -> parent.parent().isEmpty()).isPresent();
    }

    private void recordUnrecoverableFailure(final Throwable throwable) {
        if (UnrecoverableErrors.isUnrecoverable(throwable) && unrecoverableFailure == null) {
            unrecoverableFailure = throwable;
        }
    }

    private static long delayNanos(final Duration delay) {
        try {
            return Math.max(delay.toNanos(), 0L);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void sleep(final long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closing = true;
        timers.shutdownNow();
        timedBranches.shutdownNow();
        for (var frame : liveFrames) {
            frame.abandon();
        }
        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dispatcher.close();
    }
}
