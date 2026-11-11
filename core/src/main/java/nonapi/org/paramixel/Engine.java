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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.ScopeFrame;
import nonapi.org.paramixel.action.StatusAccumulator;
import nonapi.org.paramixel.action.Statuses;
import nonapi.org.paramixel.action.StepAccessors;
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
import org.paramixel.api.action.Conditional;
import org.paramixel.api.action.Loop;
import org.paramixel.api.action.Step;

/**
 * Depth-first execution engine for a compiled {@link CompiledPlan}.
 *
 * <p>Execution is recursive and depth-first: a node reports to the listener, runs its subtree, and then reports
 * completion, all on one thread. A {@code Parallel} node is run by a coordinating thread that admits its
 * children in declaration order, taking one permit from the node's named {@link Pool} per child and starting the
 * child on a worker thread (see {@link #runParallel}). The child's own thread returns the permit when its subtree
 * is terminal. Because admission waits only for a permit, a later sibling never waits for an earlier sibling to
 * finish, and a pool of size one yields exactly serial depth-first order.
 *
 * <p>Blocking on a permit is safe because {@link PoolValidator} rejects, before anything runs, every plan in which
 * a pool could be waited on while an ancestor already holds it, or in which pools and {@code Isolated} locks can
 * be taken in conflicting order.
 *
 * <p>A {@code Timeout} runs its child on a dedicated thread so the deadline can always preempt the child. At the
 * deadline the child's subtree is cancelled, but lifecycle {@code after} actions whose {@code before} was
 * attempted still run, and the {@code Timeout} completes only after that cleanup has finished. If the subtree
 * makes no cleanup progress for a grace period (a leaf ignoring interruption), the {@code Timeout} abandons it and
 * that thread may keep running in the background, still holding the pool permits of its subtree until it exits.
 */
final class Engine implements AutoCloseable {

    private static final String LOOP_SATISFIED = "loop satisfied";

    private static final String THREAD_NAME = "paramixel-engine";
    private static final String TIMED_THREAD_NAME = "paramixel-timed";
    private static final long TERMINATION_TIMEOUT_SECONDS = 5L;
    private static final long CANCELLATION_GRACE_SECONDS = 5L;
    private static final long CANCELLATION_POLL_MILLIS = 25L;

    private final Pools pools;
    private final ExecutorService workers;
    private final ExecutorService timedBranches;
    private final ScheduledExecutorService timers;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile boolean closing;
    private volatile boolean failFast;
    private volatile boolean failureOccurred;
    private volatile Throwable unrecoverableFailure;

    Engine(final Pools pools) {
        this.pools = Objects.requireNonNull(pools, "pools is null");
        this.workers = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable, THREAD_NAME + "-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
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

    /**
     * Returns the number of free permits in a pool.
     *
     * @param pool the pool name; must not be {@code null}
     * @return the free permit count
     */
    int availablePermits(final String pool) {
        return pools.pool(pool).availablePermits();
    }

    void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    Throwable unrecoverableFailure() {
        return unrecoverableFailure;
    }

    /**
     * Returns the pool sizes and peak usage observed by this run.
     *
     * @return the pool statistics; never {@code null}
     */
    List<Pools.PoolStats> poolStats() {
        return pools.stats();
    }

    /**
     * Validates and executes a compiled plan on the calling thread.
     *
     * @param plan the compiled plan; must not be {@code null}
     * @param configuration the run configuration; must not be {@code null}
     * @param listener the run listener; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     * @throws org.paramixel.api.exception.ConfigurationException if the configuration makes the plan invalid
     */
    void execute(final CompiledPlan plan, final Configuration configuration, final Listener listener) {
        Objects.requireNonNull(plan, "plan is null");
        Objects.requireNonNull(configuration, "configuration is null");
        Objects.requireNonNull(listener, "listener is null");
        PlanValidator.validate(plan, pools);
        var context = new ConcreteContext(configuration, listener, plan.root().descriptor, ScopeFrame.root());
        try {
            run(plan.root(), context, ExecutionMode.RUN);
        } catch (Throwable t) {
            recordUnrecoverableFailure(Throwables.unwrap(t));
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

    private Status run(final CompiledPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
        var descriptor = node.descriptor;
        var currentStatus = descriptor.status();
        if (!currentStatus.isPending() && !currentStatus.isRunning()) {
            return currentStatus;
        }
        var thread = Thread.currentThread();
        var originalName = thread.getName();
        var concrete = node.descriptor;
        var leaf = node.kind == CompiledPlan.Kind.TERMINAL;
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
            status = Statuses.fromThrowable(UserCodeException.wrap(failure));
        } finally {
            if (leaf) {
                concrete.setExecutingThread(null);
            }
            Thread.interrupted();
            terminalize(descriptor, status, failure);
            recordFailureForFailFast(descriptor);
            try {
                context.listener().onAfterExecution(descriptor);
            } catch (VirtualMachineError error) {
                // A broken JVM must not be swallowed, even inside listener isolation.
                throw error;
            } catch (Throwable ignored) {
                // Listener failures are isolated by SafeListener in production wiring.
            }
            thread.setName(originalName);
        }
        return descriptor.status();
    }

    private Status execute(final CompiledPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
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
            case TIMEOUT -> runTimeout(node, context);
            case ISOLATED -> runIsolated(node, context);
            case CONDITIONAL -> runConditional(node, context);
        };
    }

    // ── Controls ───────────────────────────────────────────────────────

    private Status runTerminal(final CompiledPlan.Node node, final ConcreteContext context) {
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
            return StepAccessors.consumerOf(step);
        }
        throw new IllegalStateException(
                "not a terminal action: " + action.getClass().getName());
    }

    private Status runSequential(final CompiledPlan.Node node, final ConcreteContext context) {
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

    /**
     * Runs the children of a {@code Parallel} node, bounded by its pool.
     *
     * <p>The coordinating thread admits the children strictly in declaration order: it takes one pool permit per
     * child (blocking, interruptibly, in FIFO order), starts the child on a worker thread, and goes on to the next
     * child without waiting for the previous one. The child's own thread returns the permit when the child's
     * subtree is terminal. A later sibling therefore waits only for a permit, never for an earlier sibling.
     *
     * <p>If the coordinator is interrupted or the engine is closing, admission stops: every child that was not
     * started is reported {@code ABORTED} (without taking a permit) and the in-flight children are left to be
     * interrupted by {@link #close()}.
     */
    private Status runParallel(final CompiledPlan.Node node, final ConcreteContext context) {
        var children = node.children;
        if (children.length == 0) {
            return Status.PASSED;
        }
        var pool = pools.pool(node.poolName);
        var aggregator = new StatusAccumulator();
        var pending = new CountDownLatch(children.length);
        var stopped = false;
        for (var child : children) {
            if (!stopped && !closing) {
                try {
                    pool.acquire();
                } catch (InterruptedException e) {
                    stopped = true;
                }
            } else {
                stopped = true;
            }
            if (stopped || closing) {
                stopped = true;
                include(aggregator, run(child, childContext(context, child.descriptor), ExecutionMode.ABORT));
                pending.countDown();
                continue;
            }
            try {
                workers.execute(() -> {
                    try {
                        include(aggregator, run(child, childContext(context, child.descriptor), ExecutionMode.RUN));
                    } catch (Throwable t) {
                        recordUnrecoverableFailure(t);
                        include(aggregator, Status.ABORTED);
                    } finally {
                        pool.release();
                        pending.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                pool.release();
                stopped = true;
                include(aggregator, run(child, childContext(context, child.descriptor), ExecutionMode.ABORT));
                pending.countDown();
            }
        }
        var interrupted = stopped && !closing && Thread.currentThread().isInterrupted();
        try {
            pending.await();
        } catch (InterruptedException e) {
            interrupted = true;
            include(aggregator, Status.ABORTED);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (aggregator) {
            return aggregator.status();
        }
    }

    private static void include(final StatusAccumulator aggregator, final Status status) {
        synchronized (aggregator) {
            aggregator.include(status);
        }
    }

    private Status runLifecycle(final CompiledPlan.Node node, final ConcreteContext context) {
        var scoped = context.withFrame(context.frame().child());
        var aggregator = new StatusAccumulator();
        if (node.before != null) {
            aggregator.include(run(node.before, childContext(scoped, node.before.descriptor), ExecutionMode.RUN));
        }
        var bodyMode = node.before == null || node.before.descriptor.status().isPassed()
                ? ExecutionMode.RUN
                : ExecutionMode.SKIP;
        for (var child : node.children) {
            aggregator.include(run(child, childContext(scoped, child.descriptor), bodyMode));
        }
        if (node.after != null) {
            aggregator.include(run(node.after, childContext(scoped, node.after.descriptor), ExecutionMode.RUN));
        }
        return aggregator.status();
    }

    private Status runLoop(final CompiledPlan.Node node, final ConcreteContext context) {
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
                    satisfied = loop.until().get().test(context, status);
                } catch (Throwable t) {
                    UnrecoverableErrors.rethrowIfUnrecoverable(t);
                    skipRemaining(node, i + 1, context, aggregator);
                    return Status.failed("until predicate failed: " + t.getMessage(), t);
                }
                if (satisfied) {
                    skipRemaining(node, i + 1, context, aggregator, LOOP_SATISFIED);
                    return Status.PASSED;
                }
            }
            if (loop.delay().isPresent() && i + 1 < node.children.length) {
                var nanos = delayNanos(loop.delay().get().delayFor(i + 1));
                if (nanos > 0) {
                    sleep(nanos);
                }
            }
        }
        return loop.until().isPresent() ? Status.FAILED : aggregator.status();
    }

    private Status runTimeout(final CompiledPlan.Node node, final ConcreteContext context) {
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
        try {
            // The child always runs on its own thread so the deadline can preempt it. That thread takes
            // over this thread's execution slot while this thread is parked, so no permit is needed
            // and waiting here can never starve another branch.
            try {
                timedBranches.execute(() -> {
                    try {
                        run(child, childContext, ExecutionMode.RUN);
                    } finally {
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

    private Status runIsolated(final CompiledPlan.Node node, final ConcreteContext context) {
        var child = node.children[0];
        if (node.passThrough) {
            // An ancestor already holds this lock name, so its exclusion covers this node.
            return run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
        }
        var lock = lock(node.lockName);
        lock.lock();
        try {
            return run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
        } finally {
            lock.unlock();
        }
    }

    private Status runConditional(final CompiledPlan.Node node, final ConcreteContext context) {
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
            var reason = "condition not met: " + conditional.displayName();
            runDescendants(node, context, ExecutionMode.SKIP);
            markSkipped(node, reason);
            return Status.PASSED;
        }
        var child = node.children[0];
        return run(child, childContext(context, child.descriptor), ExecutionMode.RUN);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private void skipRemaining(
            final CompiledPlan.Node node,
            final int from,
            final ConcreteContext context,
            final StatusAccumulator aggregator) {
        skipRemaining(node, from, context, aggregator, null);
    }

    /**
     * Runs the remaining children in skip mode.
     *
     * <p>When {@code reason} is present, every remaining child is reported {@code SKIPPED} with that message, which
     * lets a report distinguish "this never ran because the loop was satisfied" from a real skip.
     */
    private void skipRemaining(
            final CompiledPlan.Node node,
            final int from,
            final ConcreteContext context,
            final StatusAccumulator aggregator,
            final String reason) {
        for (var i = from; i < node.children.length; i++) {
            var child = node.children[i];
            var status = run(child, childContext(context, child.descriptor), ExecutionMode.SKIP);
            if (reason != null && status.isSkipped()) {
                child.descriptor.replaceStatus(Status.skipped(reason));
            }
            aggregator.include(child.descriptor.status());
        }
    }

    /**
     * Replaces every skipped descendant status with a message that states why the subtree did not run.
     */
    private static void markSkipped(final CompiledPlan.Node node, final String reason) {
        if (node.before != null) {
            markSkipped(node.before, reason);
        }
        for (var child : node.children) {
            markSkipped(child, reason);
        }
        if (node.after != null) {
            markSkipped(node.after, reason);
        }
        if (node.descriptor.status().isSkipped()) {
            node.descriptor.replaceStatus(Status.skipped(reason));
        }
    }

    private void runDescendants(final CompiledPlan.Node node, final ConcreteContext context, final ExecutionMode mode) {
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
        return new ConcreteContext(parent.configuration(), parent.listener(), child, parent.frame());
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
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
