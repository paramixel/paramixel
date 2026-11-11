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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.listener.Constants;
import nonapi.org.paramixel.listener.Listeners;
import nonapi.org.paramixel.support.Arguments;
import nonapi.org.paramixel.support.FastId;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;

/**
 * The framework's descriptor: an immutable tree node plus the descriptor's own execution state.
 *
 * <p>Structurally mirrors {@link Action}: {@link #before()} and {@link #after()} are separate slots, not included in
 * {@link #children()}. The structure ({@code action}, {@code before}, {@code children}, {@code after}) is final and
 * built bottom-up by the compile pass, so nothing can reshape a tree once it exists. The only mutable state is the
 * execution state of this occurrence: status, start and completion instants, and the thread running its leaf body.
 *
 * <p>{@code parent} is assigned exactly once during compilation, before the tree is published to the engine; the
 * guard in {@link #attach(MutableDescriptor)} enforces the single assignment.
 */
public final class ConcreteDescriptor implements MutableDescriptor {

    private final Action action;
    private final String id;
    private final MutableDescriptor before;
    private final List<Descriptor> children;
    private final MutableDescriptor after;

    private volatile MutableDescriptor parent;
    private volatile Status status = Status.PENDING;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile Thread executingThread;
    private volatile String cachedIdPath;
    private volatile String cachedPath;

    /**
     * Creates a standalone descriptor that is its own root and has no lifecycle slots or children.
     *
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final Action action) {
        this(action, null, List.of(), null);
    }

    /**
     * Creates a parented descriptor with no lifecycle slots or children.
     *
     * @param parent the parent descriptor, or {@code null} for a root
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final MutableDescriptor parent, final Action action) {
        this(action, null, List.of(), null);
        this.parent = parent;
    }

    /**
     * Creates a fully shaped descriptor.
     *
     * <p>The compile pass builds children before their parent, so this constructor does not link the parent
     * relationship; it calls {@link #attach(MutableDescriptor)} on the parent afterwards.
     *
     * @param action the action bound to this descriptor; must not be {@code null}
     * @param before the before slot, or {@code null}
     * @param children the body children in discovery order; must not be {@code null} or contain {@code null}
     * @param after the after slot, or {@code null}
     * @throws NullPointerException if {@code action} or {@code children} is {@code null}, or a child is {@code null}
     */
    public ConcreteDescriptor(
            final Action action,
            final MutableDescriptor before,
            final List<? extends Descriptor> children,
            final MutableDescriptor after) {
        this.action = Objects.requireNonNull(action, "action is null");
        this.before = before;
        this.children = List.copyOf(Objects.requireNonNull(children, "children is null"));
        this.after = after;
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
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    @Override
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
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
        return children;
    }

    @Override
    public Optional<Descriptor> after() {
        return Optional.ofNullable(after);
    }

    @Override
    public void attach(final MutableDescriptor parent) {
        Objects.requireNonNull(parent, "parent is null");
        if (this.parent != null) {
            throw new IllegalStateException("Descriptor already attached to a parent: " + id);
        }
        this.parent = parent;
    }

    /**
     * Links every structural slot to this descriptor, which publishes the subtree.
     *
     * <p>Called once by the compile pass after this descriptor and its slots exist.
     *
     * @throws IllegalStateException if a slot was already attached to another parent
     */
    public void attachSlots() {
        if (before != null) {
            before.attach(this);
        }
        for (var child : children) {
            Arguments.requireInstanceOf(child, MutableDescriptor.class, "child must be a MutableDescriptor")
                    .attach(this);
        }
        if (after != null) {
            after.attach(this);
        }
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
        // Descriptor already in a terminal state: no-op.
    }

    @Override
    public synchronized void replaceStatus(final Status newStatus) {
        Objects.requireNonNull(newStatus, "status is null");
        if (!status.isPending() && !status.isRunning() && newStatus.kind().isTerminal()) {
            status = newStatus;
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
     * Returns the human-readable display path from the run root to this descriptor, cached after first
     * computation.
     *
     * <p>The parent chain is fixed once the tree is compiled, so the path is a stable value.
     *
     * @return the display-name path; empty for the run root
     */
    @Override
    public String path() {
        var path = cachedPath;
        if (path != null) {
            return path;
        }
        var names = new ArrayDeque<String>();
        for (var d = this; d != null; d = d.parent instanceof ConcreteDescriptor cd ? cd : null) {
            var name = Listeners.sanitizeMessage(d.action.displayName());
            if (!Constants.ROOT_NAME.equals(name)) {
                names.addFirst(name);
            }
        }
        cachedPath = String.join(" / ", names);
        return cachedPath;
    }

    /**
     * Returns the id path from root to this descriptor, cached after first computation.
     *
     * <p>The parent chain is fixed once the tree is compiled, so the id path is a stable value.
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
    public synchronized boolean tryStart() {
        if (status.isPending()) {
            setStatus(Status.RUNNING);
            return true;
        }
        return status.isRunning();
    }

    @Override
    public boolean isExecuting() {
        return executingThread != null;
    }

    @Override
    public boolean abort(final Status rootStatus, final Throwable cause) {
        Objects.requireNonNull(rootStatus, "rootStatus is null");
        Objects.requireNonNull(cause, "cause is null");
        var transition = beginAbort(rootStatus, cause);
        if (transition == AbortTransition.ALREADY_TERMINAL) {
            return true; // already terminal — idempotent; it necessarily started earlier
        }
        // Recurse without holding this node's monitor: the structural slots are final, and releasing
        // the monitor avoids holding a lock across the recursive walk.
        var message = cause.getMessage() != null ? cause.getMessage() : "cancelled";
        var abortedDescendant = Status.aborted("cancelled by ancestor: " + message);
        // The before slot is aborted first: its own atomic transition reports whether it had been
        // attempted, which decides whether the after slot still owes cleanup.
        var beforeAttempted = before != null && before.abort(abortedDescendant, cause);
        for (var child : children) {
            Arguments.requireInstanceOf(child, MutableDescriptor.class, "child must be a MutableDescriptor")
                    .abort(abortedDescendant, cause);
        }
        var cleanupOwed = before != null ? beforeAttempted : transition == AbortTransition.STARTED;
        if (after != null && !cleanupOwed) {
            after.abort(abortedDescendant, cause);
        }
        return transition == AbortTransition.STARTED;
    }

    /**
     * Outcome of this node's own abort transition.
     */
    private enum AbortTransition {
        /** The descriptor was already terminal; nothing changed. */
        ALREADY_TERMINAL,
        /** The descriptor was pending and has now been made terminal without ever starting. */
        NOT_STARTED,
        /** The descriptor was running and has now been made terminal. */
        STARTED
    }

    /**
     * Performs this node's own terminal transition and leaf interrupt.
     *
     * <p>Returns {@link AbortTransition#ALREADY_TERMINAL} when this descriptor is already terminal,
     * making {@link #abort} idempotent. Uses {@link #setStatus} to preserve the documented status
     * state machine and {@code startedAt}/{@code completedAt} bookkeeping. Runs under this
     * descriptor's monitor, so it is atomic with respect to {@link #tryStart()}.
     *
     * <p>Only leaf descriptors run user code on their executing thread; coordination nodes are
     * unblocked by their children completing, so interrupting only leaves avoids stranding an
     * interrupt flag on a coordinating stack. For deeply nested coordination
     * (e.g. Sequential → Parallel → Sequential → Parallel → Step), the interrupt reaches every
     * executing leaf in the subtree, each leaf returns, and each coordinating frame observes its
     * children's terminal state as the recursion unwinds. Propagation is therefore bounded by the
     * nesting depth plus the time an uncooperative leaf takes to return.
     *
     * @param rootStatus the terminal status for this descriptor; must not be {@code null}
     * @param cause the cause of the cancellation; must not be {@code null}
     * @return the transition that occurred
     */
    private synchronized AbortTransition beginAbort(final Status rootStatus, final Throwable cause) {
        if (isCompleted()) {
            return AbortTransition.ALREADY_TERMINAL;
        }
        var transition = status.isRunning() ? AbortTransition.STARTED : AbortTransition.NOT_STARTED;
        if (status.isPending()) {
            // State machine requires PENDING -> RUNNING -> terminal.
            setStatus(Status.RUNNING);
        }
        setStatus(rootStatus);
        if (isLeafAction()) {
            interruptExecutingThread();
        }
        return transition;
    }
}
