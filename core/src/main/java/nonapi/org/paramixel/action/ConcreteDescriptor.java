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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.FastId;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;

/**
 * Mutable descriptor for all action occurrences in a descriptor tree.
 *
 * <p>Structurally mirrors {@link Action}: {@link #before()} and {@link #after()} are
 * separate slots, not included in {@link #children()}. Tree structure, occurrence identity,
 * and execution state are held directly.
 */
public final class ConcreteDescriptor implements MutableDescriptor {

    private final Action action;
    private final String id;
    private volatile Status status = Status.PENDING;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile MutableDescriptor before;
    private final List<MutableDescriptor> children = new ArrayList<>();
    private volatile MutableDescriptor after;
    private volatile List<Descriptor> childrenView;
    private volatile boolean frozen;
    private volatile SchedulerPriorityKey schedulerPriorityKey = SchedulerPriorityKey.root();
    private volatile Thread executingThread;
    private volatile CompletableFuture<Descriptor> scheduledFuture;
    private volatile String cachedIdPath;
    volatile MutableDescriptor parent;
    volatile ExecutionNode executionNode;

    /**
     * Creates a root descriptor.
     *
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final Action action) {
        this(null, action);
    }

    /**
     * Creates a descriptor with an optional parent.
     *
     * @param parent the parent descriptor, or {@code null} for root
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final MutableDescriptor parent, final Action action) {
        this.parent = parent;
        this.action = Objects.requireNonNull(action, "action is null");
        this.id = FastId.generateId();
    }

    @Override
    public Action action() {
        return action;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public boolean isPassed() {
        return status.isPassed();
    }

    @Override
    public boolean isFailed() {
        return status.isFailed();
    }

    @Override
    public boolean isSkipped() {
        return status.isSkipped();
    }

    @Override
    public boolean isAborted() {
        return status.isAborted();
    }

    @Override
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    @Override
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    @Override
    public Optional<String> message() {
        return status.message();
    }

    @Override
    public Optional<Throwable> throwable() {
        return status.throwable();
    }

    @Override
    public boolean isCompleted() {
        return !status.isPending() && !status.isRunning();
    }

    @Override
    public Optional<Descriptor> parent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Optional<Descriptor> before() {
        return Optional.ofNullable(before);
    }

    @Override
    public List<Descriptor> children() {
        var view = childrenView;
        if (view != null) {
            return view;
        }
        synchronized (this) {
            if (childrenView == null) {
                childrenView = List.copyOf(children);
            }
            return childrenView;
        }
    }

    @Override
    public Optional<Descriptor> after() {
        return Optional.ofNullable(after);
    }

    @Override
    public synchronized void setBefore(final MutableDescriptor before) {
        Objects.requireNonNull(before, "before is null");
        ensureNotFrozen();
        before.setParent(this);
        before.setSchedulerPriorityKey(schedulerPriorityKey.child(0));
        this.before = before;
    }

    @Override
    public synchronized void setAfter(final MutableDescriptor after) {
        Objects.requireNonNull(after, "after is null");
        ensureNotFrozen();
        after.setParent(this);
        var offset = this.before != null ? 1 + children.size() : children.size();
        after.setSchedulerPriorityKey(schedulerPriorityKey.child(offset));
        this.after = after;
    }

    @Override
    public synchronized void addChild(final MutableDescriptor child) {
        Objects.requireNonNull(child, "child is null");
        if (child == this) {
            throw new IllegalArgumentException("child is this descriptor");
        }
        ensureNotFrozen();
        child.setParent(this);
        var offset = this.before != null ? 1 : 0;
        child.setSchedulerPriorityKey(schedulerPriorityKey.child(offset + children.size()));
        children.add(child);
        childrenView = null;
    }

    @Override
    public void setParent(final MutableDescriptor parent) {
        ensureNotFrozen();
        this.parent = Objects.requireNonNull(parent, "parent is null");
    }

    @Override
    public SchedulerPriorityKey schedulerPriorityKey() {
        return schedulerPriorityKey;
    }

    @Override
    public synchronized void setSchedulerPriorityKey(final SchedulerPriorityKey schedulerPriorityKey) {
        ensureNotFrozen();
        this.schedulerPriorityKey = Objects.requireNonNull(schedulerPriorityKey, "schedulerPriorityKey is null");
        var offset = 0;
        if (before != null) {
            before.setSchedulerPriorityKey(this.schedulerPriorityKey.child(offset));
            offset = 1;
        }
        for (var i = 0; i < children.size(); i++) {
            children.get(i).setSchedulerPriorityKey(this.schedulerPriorityKey.child(offset + i));
        }
        if (after != null) {
            after.setSchedulerPriorityKey(this.schedulerPriorityKey.child(offset + children.size()));
        }
    }

    @Override
    public void freeze() {
        MutableDescriptor beforeSnapshot;
        List<Descriptor> childrenSnapshot;
        MutableDescriptor afterSnapshot;
        synchronized (this) {
            beforeSnapshot = before;
            if (childrenView != null) {
                childrenSnapshot = childrenView;
            } else {
                childrenView = List.copyOf(children);
                childrenSnapshot = childrenView;
            }
            frozen = true;
            afterSnapshot = after;
        }

        if (beforeSnapshot != null) {
            beforeSnapshot.freeze();
        }
        for (var child : childrenSnapshot) {
            Arguments.requireInstanceOf(child, MutableDescriptor.class, "child must be a MutableDescriptor")
                    .freeze();
        }
        if (afterSnapshot != null) {
            afterSnapshot.freeze();
        }
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Descriptor is frozen: " + id);
        }
    }

    @Override
    public synchronized CompletableFuture<Descriptor> markScheduled() {
        if (scheduledFuture != null) {
            throw new IllegalStateException("Descriptor already scheduled: " + id);
        }
        scheduledFuture = new CompletableFuture<>();
        return scheduledFuture;
    }

    @Override
    public synchronized boolean isScheduled() {
        return scheduledFuture != null;
    }

    @Override
    public synchronized void setStatus(final Status newStatus) {
        Objects.requireNonNull(newStatus, "status is null");
        if (newStatus.isPending()) {
            throw new IllegalArgumentException("Cannot set PENDING status");
        }
        if (status.isPending()) {
            if (!newStatus.isRunning()) {
                throw new IllegalStateException("Descriptor must transition from PENDING to RUNNING");
            }
            status = Status.RUNNING;
            startedAt = Instant.now();
            completedAt = null;
            return;
        }
        if (status.isRunning()) {
            if (newStatus.isPending() || newStatus.isRunning()) {
                throw new IllegalStateException("Descriptor must transition from RUNNING to terminal status");
            }
            status = newStatus;
            completedAt = Instant.now();
        }
        // Descriptor already in a terminal state — no-op.
    }

    @Override
    public synchronized void completeFuture() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.complete(this);
        }
    }

    @Override
    public synchronized void completeFutureExceptionally(final Throwable throwable) {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.completeExceptionally(throwable);
        }
    }

    /**
     * Records the thread executing this descriptor's action.
     *
     * @param thread the executing thread; may be {@code null} to clear the record
     */
    public synchronized void setExecutingThread(final Thread thread) {
        this.executingThread = thread;
    }

    @Override
    public synchronized void interruptExecutingThread() {
        var thread = executingThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Returns the id path from root to this descriptor, cached after first computation.
     *
     * <p>The parent chain is immutable post-freeze, so the id path is a stable value.
     * Uses a volatile field with benign races: two threads may compute the same string,
     * but {@link String} is immutable and the computation is pure.
     *
     * @return the id path; never {@code null}
     */
    public String idPath() {
        var path = cachedIdPath;
        if (path != null) {
            return path;
        }
        var ids = new ArrayDeque<String>();
        for (var d = this; d != null; d = d.parent instanceof ConcreteDescriptor cd ? cd : null) {
            ids.addFirst(d.id());
        }
        cachedIdPath = String.join("-", ids);
        return cachedIdPath;
    }

    @Override
    public ExecutionNode executionNode() {
        return executionNode;
    }

    @Override
    public void setExecutionNode(final ExecutionNode node) {
        this.executionNode = node;
    }

    @Override
    public CompletableFuture<Descriptor> scheduledFuture() {
        return scheduledFuture;
    }

    @Override
    public void abort(final Status rootStatus, final Throwable cause) {
        Objects.requireNonNull(rootStatus, "rootStatus is null");
        Objects.requireNonNull(cause, "cause is null");
        if (!beginAbort(rootStatus, cause)) {
            return; // already terminal — idempotent
        }
        // Recurse without holding this node's monitor: children()/before()/after() are immutable
        // post-freeze, and releasing avoids holding a lock across the recursive walk.
        var message = cause.getMessage() != null ? cause.getMessage() : "cancelled";
        var abortedDescendant = Status.aborted("cancelled by ancestor: " + message);
        if (before != null) {
            before.abort(abortedDescendant, cause);
        }
        for (var child : children()) {
            Arguments.requireInstanceOf(child, MutableDescriptor.class, "child must be a MutableDescriptor")
                    .abort(abortedDescendant, cause);
        }
        if (after != null) {
            after.abort(abortedDescendant, cause);
        }
    }

    /**
     * Performs this node's own terminal transition and leaf interrupt.
     *
     * <p>Returns {@code false} when this descriptor is already terminal, making {@link #abort}
     * idempotent. Uses {@link #setStatus} to preserve the documented status state machine and
     * {@code startedAt}/{@code completedAt} bookkeeping.
     *
     * <p>Only leaf descriptors run user code on their executing thread; coordination nodes are
     * unblocked solely by future completion, so interrupting only leaves avoids stranding an
     * interrupt flag on a coordinator parked in the scheduler's managed join. For deeply nested
     * synchronous coordination (e.g. Sequential → Parallel → Sequential → Parallel → Step),
     * the interrupt propagates through the chain: each coordination node's {@code managedJoin}
     * observes the future completion (or the interrupt waking it from park), unwinds through
     * the call stack, and the parent's {@code runChild} returns, allowing the next level to
     * observe its child's terminal state. This propagation is bounded by the depth of the
     * nesting and the scheduler's backoff ceiling (1 second per {@code managedJoin} park).
     *
     * @param rootStatus the terminal status for this descriptor; must not be {@code null}
     * @param cause the cause used to complete the scheduling future exceptionally; must not be
     *     {@code null}
     * @return {@code true} if this node transitioned (or was running and now completes)
     */
    private synchronized boolean beginAbort(final Status rootStatus, final Throwable cause) {
        if (isCompleted()) {
            return false;
        }
        if (status.isPending()) {
            // State machine requires PENDING -> RUNNING -> terminal.
            setStatus(Status.RUNNING);
        }
        setStatus(rootStatus);
        completeFutureExceptionally(cause);
        var node = executionNode;
        if (node != null) {
            executionNode = null;
            node.completeExternally();
        }
        // Only leaf descriptors run user code on their executing thread; coordination nodes are
        // unblocked solely by future completion, so interrupting only leaves avoids stranding an
        // interrupt flag on a coordinator parked in the scheduler's managed join.
        if (isLeafAction()) {
            interruptExecutingThread();
        }
        return true;
    }
}
