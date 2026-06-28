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

package nonapi.org.paramixel.action;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.ExecutionMode;
import nonapi.org.paramixel.Scheduler;
import org.paramixel.api.Descriptor;

/**
 * Execution state for a descriptor undergoing continuation-based scheduling.
 *
 * <p>Coordination strategies (Parallel, Sequential, Scope, Timeout) attach an
 * {@code ExecutionNode} to their descriptor to track pending children, phase
 * transitions, and status aggregation without blocking a scheduler worker thread.
 * When the last child completes, the node schedules a continuation that finalizes
 * the descriptor and notifies its parent.
 *
 * <p>This class is not thread-safe for mutable fields other than
 * {@link #pendingChildren}. Strategy code is expected to serialize its own
 * state transitions when more than one continuation can be active for the same
 * node (for example, {@code Parallel}). The {@code pendingChildren} counter is
 * atomically mutated by completing child workers.
 */
public final class ExecutionNode {

    /**
     * Lifecycle phase for before-child execution.
     */
    public static final int PHASE_BEFORE = 0;

    /**
     * Lifecycle phase for body-child execution.
     */
    public static final int PHASE_BODY = 1;

    /**
     * Lifecycle phase for after-child execution.
     */
    public static final int PHASE_AFTER = 2;

    /**
     * Lifecycle phase for final aggregation.
     */
    public static final int PHASE_COMPLETE = 3;

    /** The descriptor being executed. */
    public final MutableDescriptor descriptor;
    /** The scheduler executing this node. */
    public final Scheduler scheduler;

    /**
     * Number of children that have not yet reported completion.
     *
     * <p>Atomically decremented by completing children. The continuation is
     * scheduled after every decrement so that strategies can react to child
     * completion.
     */
    public final AtomicInteger pendingChildren = new AtomicInteger(0);

    /**
     * Whether a continuation should be scheduled after every child completion,
     * even while other children remain pending.
     *
     * <p>Only admission-window strategies such as {@code Parallel} need this.
     * Sequential strategies, lifecycle phases, and aggregate-only strategies run
     * their continuation when the pending count reaches zero.
     */
    public boolean continueOnEveryChildCompletion;

    /**
     * Continuation runnable to execute when a child completes.
     */
    public volatile Runnable continuation;

    /**
     * Current execution phase for lifecycle/scope nodes.
     */
    public int phase;

    /**
     * Current child index for sequential/parallel iteration.
     */
    public int childIndex;

    /**
     * Number of currently running direct children (for Parallel admission control).
     */
    public int runningChildren;

    /**
     * Maximum number of concurrent direct children (Parallel cap),
     * or dependent flag (1 = dependent, 0 = independent) for Sequential.
     */
    public int cap;

    /**
     * Frozen snapshot of children, used for iteration during execution.
     */
    public List<Descriptor> children;

    /**
     * Incremental status aggregator.
     */
    public StatusAccumulator aggregator;

    /**
     * Execution mode for child scheduling (used by Sequential dependent tracking).
     */
    public ExecutionMode childMode = ExecutionMode.RUN;

    /**
     * Number of direct child scheduling attempts made for this node.
     *
     * <p>Once a strategy attempts to schedule at least one child, completion is
     * delegated to child-completion continuations. This remains true even if the
     * child completes before the starting strategy returns.
     */
    public int attemptedChildren;

    /**
     * Whether this node's strategy has already published terminal state to
     * {@link #nodeCompletion}. Set atomically by the first continuation or
     * external abort path that completes the node to prevent duplicate completion.
     */
    public final AtomicBoolean finalized = new AtomicBoolean(false);

    /**
     * Completes when this node's strategy has reached terminal state.
     *
     * <p>{@code executeDescriptorWithCompletion} waits on this future via
     * {@code managedJoin} for deferred descriptors so that
     * {@code onAfterExecution} fires on the same thread as
     * {@code onBeforeExecution}, preserving the listener bracket contract.
     *
     * <p>Scheduling future completion, scheduler execution callbacks, and
     * parent notification happen after this future completes, on the original
     * descriptor execution thread.
     */
    public final CompletableFuture<Void> nodeCompletion = new CompletableFuture<>();

    /**
     * Creates a new execution node.
     *
     * @param descriptor the descriptor being executed; must not be {@code null}
     * @param scheduler the scheduler; must not be {@code null}
     */
    public ExecutionNode(final MutableDescriptor descriptor, final Scheduler scheduler) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler is null");
    }

    /**
     * Increments the pending child count.
     */
    public void incrementPendingChildren() {
        pendingChildren.incrementAndGet();
    }

    /**
     * Decrements the pending child count if it is positive.
     *
     * @return the count after decrementing, or {@code -1} if there were no
     *     pending children to decrement
     */
    public int decrementPendingChildren() {
        while (true) {
            var current = pendingChildren.get();
            if (current <= 0) {
                return -1;
            }
            var next = current - 1;
            if (pendingChildren.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * Clears all pending children.
     *
     * @return {@code true} if at least one child was pending before the drain
     */
    public boolean drainPendingChildren() {
        return pendingChildren.getAndSet(0) > 0;
    }

    /**
     * Returns the current pending child count.
     *
     * @return non-negative pending count
     */
    public int pendingChildCount() {
        return pendingChildren.get();
    }

    /**
     * Completes this node from an external terminal transition, such as an abort
     * cascade.
     *
     * <p>This method only releases waiters on {@link #nodeCompletion}. It does
     * not invoke listeners, scheduler callbacks, scheduled futures, or parent
     * notification; those remain owned by the descriptor execution thread.
     *
     * @return {@code true} if this call completed the node
     */
    public boolean completeExternally() {
        if (!finalized.compareAndSet(false, true)) {
            return false;
        }
        nodeCompletion.complete(null);
        return true;
    }

    /**
     * Schedules this node's continuation as a work item on the scheduler's
     * executor queue. This is called when {@code pendingChildren} reaches zero
     * (by the last completing child) or when the node has no children to
     * schedule (by the start strategy).
     */
    public void scheduleContinuation() {
        // Schedule the continuation on the scheduler worker pool.
        // The work will run on a scheduler worker with proper thread naming, etc.
        scheduler.executeContinuation(this);
    }
}
