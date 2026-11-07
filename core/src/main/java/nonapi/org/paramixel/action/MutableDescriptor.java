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

import java.util.concurrent.CompletableFuture;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Internal mutable extension of {@link Descriptor} for framework use during
 * discovery and execution.
 *
 * <p>Public consumers interact with the read-only {@link Descriptor} view.
 * This interface exposes mutation operations needed by the framework to build
 * descriptor trees and manage execution state.
 */
public interface MutableDescriptor extends Descriptor {

    /**
     * Returns the identity of the compilation that assigned this descriptor's order.
     *
     * <p>Ranks are tree-local: they are only comparable between descriptors that report the same
     * tree id. An uncompiled descriptor reports {@link DescriptorOrder#UNCOMPILED}.
     *
     * @return the compilation identity, or {@link DescriptorOrder#UNCOMPILED}
     */
    long orderTreeId();

    /**
     * Returns the lowest order rank within this descriptor's subtree.
     *
     * <p>Together with {@link #orderRank()} this bounds the inclusive interval occupied by this
     * descriptor's subtree, which is what makes ancestry a constant-time test.
     *
     * @return the subtree's lowest rank, or {@link DescriptorOrder#UNCOMPILED}
     */
    long orderSubtreeStart();

    /**
     * Returns this descriptor's order rank, or {@link DescriptorOrder#UNCOMPILED}.
     *
     * <p>Compiled ranks are non-negative and totally ordered across a tree: a descriptor never
     * outranks its own descendants, and an earlier sibling's whole subtree outranks a later
     * sibling's. Descriptors from different trees are not comparable.
     *
     * @return the order rank, or {@link DescriptorOrder#UNCOMPILED}
     */
    long orderRank();

    /**
     * Records this descriptor's compiled order metadata.
     *
     * <p>Called once per descriptor by {@link DescriptorOrder#compile(MutableDescriptor)}. The
     * rank is published last so a reader that observes a rank also observes the accompanying tree
     * id and subtree start.
     *
     * @param treeId the compilation identity
     * @param subtreeStart the lowest rank in this descriptor's subtree
     * @param rank this descriptor's rank
     * @throws IllegalStateException if this descriptor is already compiled
     */
    void setOrder(long treeId, long subtreeStart, long rank);

    /**
     * Sets the before-child descriptor during discovery.
     *
     * @param before the before-child descriptor; must not be {@code null}
     */
    void setBefore(MutableDescriptor before);

    /**
     * Sets the after-child descriptor during discovery.
     *
     * @param after the after-child descriptor; must not be {@code null}
     */
    void setAfter(MutableDescriptor after);

    /**
     * Adds a child action descriptor during discovery.
     *
     * @param child the child descriptor; must not be {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code child} is this descriptor
     */
    void addChild(MutableDescriptor child);

    /**
     * Sets the parent descriptor. Called during discovery to establish
     * the tree relationship.
     *
     * @param parent the parent descriptor; must not be {@code null}
     */
    void setParent(MutableDescriptor parent);

    /**
     * Freezes this descriptor's tree structure and caches immutable child views.
     *
     * <p>After freeze, structural mutations such as adding children or re-parenting
     * are rejected with {@link IllegalStateException}. Implementations should apply
     * freeze recursively to descendants.
     */
    void freeze();

    /**
     * Returns whether this descriptor is frozen.
     *
     * @return {@code true} when the descriptor is frozen
     */
    boolean isFrozen();

    /**
     * Prepares this descriptor for scheduling.
     *
     * @return a future associated with this scheduled descriptor
     * @throws IllegalStateException if already scheduled
     */
    CompletableFuture<Descriptor> markScheduled();

    /**
     * Returns whether this descriptor has already been scheduled.
     *
     * @return {@code true} when scheduled
     */
    boolean isScheduled();

    /**
     * Returns this descriptor's internal execution status.
     *
     * @return the execution status; never {@code null}
     */
    Status status();

    /**
     * Sets this descriptor's local execution status.
     *
     * @param newStatus the new status; must not be {@code null}
     * @throws NullPointerException if {@code newStatus} is {@code null}
     */
    void setStatus(Status newStatus);

    /**
     * Completes the scheduling future.
     */
    void completeFuture();

    /**
     * Completes the scheduling future exceptionally.
     *
     * @param throwable the failure; must not be {@code null}
     */
    void completeFutureExceptionally(Throwable throwable);

    /**
     * Interrupts the thread executing this descriptor's action, if one is recorded.
     *
     * <p>Called by the scheduler's timeout handler after the grace period expires.
     * If no thread is recorded, this method is a no-op.
     */
    void interruptExecutingThread();

    /**
     * Cancels this descriptor's subtree best-effort.
     *
     * <p>Marks this descriptor terminal with {@code rootStatus} (if not already terminal),
     * completes its scheduled future exceptionally with {@code cause}, interrupts this
     * descriptor's executing thread when it is a leaf action, and recurses into
     * {@link #before()}, {@link #children()}, and {@link #after()}: each descendant is marked
     * {@link Status#ABORTED}, has its scheduled future completed exceptionally, and, when it is
     * a leaf action, has its executing thread interrupted.
     *
     * <p>This is the unblocking mechanism for coordination subtrees (e.g. a {@code Parallel})
     * whose coordinator is parked in the scheduler's managed join: completing the futures makes
     * the join observe {@code isDone()} and exit, and interrupting leaf threads unblocks
     * cooperative (interruptible) user code so its leaf permits are released.
     *
     * <p>For deeply nested synchronous coordination (e.g. Sequential → Parallel → Sequential
     * → Parallel → Step), the abort propagation is bounded: the interrupt targets the leaf's
     * executing thread, the leaf's exception unwinds through the call stack, each coordination
     * node's {@code managedJoin} returns (either from the interrupt or the future completion),
     * and the parent's {@code runChild} returns, allowing the next level to observe its child's
     * terminal state. The propagation time is bounded by the nesting depth and the scheduler's
     * backoff ceiling (1 second per {@code managedJoin} park).
     *
     * <p>Idempotent: a descriptor already in a terminal state is skipped, so concurrent
     * completion and cancellation both converge safely. Safe to call from any thread,
     * including a scheduler worker thread; this method never blocks on a future, it only
     * completes them. A leaf whose body ignores interruption (e.g. a CPU-bound loop) cannot be
     * forcibly stopped and may leak its leaf permit and worker thread until the scheduler is
     * closed.
     *
     * @param rootStatus the terminal status for this descriptor; must not be {@code null}
     * @param cause the cause used to complete futures exceptionally; must not be {@code null}
     */
    void abort(Status rootStatus, Throwable cause);

    /**
     * Returns the descriptor depth from the root.
     *
     * <p>Root descriptors have depth {@code 0}. Direct children of root have depth {@code 1}, and
     * so on.
     *
     * @return non-negative tree depth
     */
    default int depth() {
        var depth = 0;
        var currentParent = parent();
        while (currentParent.isPresent()) {
            depth++;
            currentParent = currentParent.orElseThrow().parent();
        }
        return depth;
    }

    /**
     * Returns whether this descriptor wraps a coordination action.
     *
     * <p>Coordination actions manage child/lifecycle orchestration and are not counted as leaf
     * work for global leaf-parallelism permits.
     *
     * @return {@code true} when the wrapped action is coordination-only
     */
    @SuppressWarnings("removal")
    default boolean isCoordinationAction() {
        return action() instanceof Parallel
                || action() instanceof Sequence
                || action() instanceof Sequential
                || action() instanceof Isolated
                || before().isPresent()
                || after().isPresent()
                || !children().isEmpty();
    }

    /**
     * Returns whether this descriptor wraps a leaf action.
     *
     * @return {@code true} when the wrapped action is leaf work
     */
    default boolean isLeafAction() {
        return action() instanceof Step || action() instanceof Assert || action() instanceof Delay;
    }

    /**
     * Returns the execution node attached to this descriptor, if any.
     *
     * <p>Coordination strategies attach execution nodes to track pending children
     * and schedule continuations. Leaf descriptors never have execution nodes.
     *
     * @return the execution node, or {@code null} if not set
     */
    ExecutionNode executionNode();

    /**
     * Attaches an execution node to this descriptor.
     *
     * @param node the execution node, or {@code null} to clear it
     */
    void setExecutionNode(ExecutionNode node);

    /**
     * Returns the scheduling future for this descriptor.
     *
     * <p>The future is created by {@link #markScheduled()} and completed when
     * the descriptor reaches a terminal status (or fails to start).
     *
     * @return the scheduling future, or {@code null} if not yet scheduled
     */
    CompletableFuture<Descriptor> scheduledFuture();
}
