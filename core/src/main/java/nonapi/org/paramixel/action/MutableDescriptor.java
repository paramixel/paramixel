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

import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Internal extension of {@link Descriptor} for framework use during compilation and execution.
 *
 * <p>Public consumers interact with the read-only {@link Descriptor} view. This interface adds only what the
 * framework needs: a single {@link #attach(MutableDescriptor)} during compilation and the execution-state
 * transitions of one descriptor occurrence. The tree itself is immutable.
 */
public interface MutableDescriptor extends Descriptor {

    /**
     * Attaches this descriptor to its parent.
     *
     * <p>The compile pass builds a tree bottom-up, so a child is created before its parent and attached exactly
     * once, before the tree is published to the engine.
     *
     * @param parent the parent descriptor; must not be {@code null}
     * @throws NullPointerException if {@code parent} is {@code null}
     * @throws IllegalStateException if this descriptor is already attached
     */
    void attach(MutableDescriptor parent);

    /**
     * Sets this descriptor's local execution status.
     *
     * @param newStatus the new status; must not be {@code null}
     * @throws NullPointerException if {@code newStatus} is {@code null}
     */
    void setStatus(Status newStatus);

    /**
     * Replaces a terminal status with a more descriptive one.
     *
     * <p>Used by the engine when a skipped subtree should carry the reason it did not run, for example
     * {@code "loop satisfied"} or {@code "condition not met"}. Only a terminal status can be replaced, and the
     * replacement must be terminal too.
     *
     * @param newStatus the replacement status; must not be {@code null}
     * @throws NullPointerException if {@code newStatus} is {@code null}
     * @throws IllegalStateException if the descriptor is not terminal or {@code newStatus} is not terminal
     */
    void replaceStatus(Status newStatus);

    /**
     * Interrupts the thread executing this descriptor's action, if one is recorded.
     *
     * <p>Called by the scheduler's timeout handler after the grace period expires.
     * If no thread is recorded, this method is a no-op.
     */
    void interruptExecutingThread();

    /**
     * Atomically starts this descriptor.
     *
     * <p>Transitions {@link Status#PENDING} to {@link Status#RUNNING}. The engine executes an
     * action only when this method returns {@code true}, so a descriptor aborted before it
     * started never runs its action. The transition is atomic with respect to
     * {@link #abort(Status, Throwable)}.
     *
     * @return {@code true} if the descriptor is now (or was already) {@link Status#RUNNING};
     *     {@code false} if it is already terminal
     */
    boolean tryStart();

    /**
     * Returns whether a thread is currently recorded as executing this descriptor's action.
     *
     * @return {@code true} while a leaf action's thread is recorded
     */
    boolean isExecuting();

    /**
     * Cancels this descriptor's subtree best-effort.
     *
     * <p>Marks this descriptor terminal with {@code rootStatus} (if not already terminal), interrupts this
     * descriptor's executing thread when it is a leaf action, and recurses into {@link #before()} and
     * {@link #children()}: each descendant is marked {@link Status#ABORTED} and, when it is a leaf action, has its
     * executing thread interrupted.
     *
     * <p>Lifecycle cleanup is preserved: {@link #after()} is aborted only when its lifecycle never
     * attempted its {@link #before()} (or, without a {@code before}, never started). Otherwise the
     * {@code after} action is left untouched and not interrupted, so it still runs once the
     * cancelled work has unwound — matching the documented guarantee that {@code after} always
     * runs once {@code before} has been attempted.
     *
     * <p>This is the unblocking mechanism for coordination subtrees (e.g. a {@code Parallel}) whose coordinating
     * frame is waiting on children: terminal state lets each waiting frame observe completion, and interrupting leaf
     * threads unblocks cooperative (interruptible) user code so its executing frame returns and releases its
     * permit.
     *
     * <p>For deeply nested coordination (e.g. Sequential → Parallel → Sequential → Parallel →
     * Step), propagation is bounded by the nesting depth: the interrupt reaches each executing
     * leaf, each leaf returns, and each coordinating frame observes its children's terminal state
     * as the recursion unwinds.
     *
     * <p>Idempotent: a descriptor already in a terminal state is skipped, so concurrent completion and cancellation
     * both converge safely. Safe to call from any thread, including a worker thread; this method never blocks. A leaf
     * whose body ignores interruption (e.g. a CPU-bound loop) cannot be forcibly stopped and keeps its concurrency
     * budget and worker thread until it returns or the engine is closed.
     *
     * @param rootStatus the terminal status for this descriptor; must not be {@code null}
     * @param cause the cause of the cancellation; must not be {@code null}
     * @return {@code true} if this descriptor had already started (was running or terminal) when
     *     the abort reached it; {@code false} if the abort prevented it from ever starting
     */
    boolean abort(Status rootStatus, Throwable cause);

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
    default boolean isCoordinationAction() {
        return action() instanceof Parallel
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
        return action() instanceof Step;
    }
}
