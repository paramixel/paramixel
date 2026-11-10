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

import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.StatusAccumulator;
import org.paramixel.api.Status;

/**
 * Per-activation state of one {@code Parallel} node.
 *
 * <p>The children are consumed through a shared cursor by <em>lanes</em>: the coordinating thread is
 * always lane one, and each additional lane runs on a worker thread that holds a permit. Because
 * every lane simply claims the next unstarted child, children start in declaration order and a
 * child that blocks never prevents its later siblings from being started by another lane.
 * At most {@code cap} lanes exist at any time.
 *
 * <p>All state is guarded by the frame's monitor.
 */
final class ParallelFrame {

    private final ExecutionPlan.Node node;
    private final ConcreteContext context;
    private final int cap;
    private final StatusAccumulator aggregator = new StatusAccumulator();
    private int next;
    private int lanes = 1;
    private boolean abandoned;

    ParallelFrame(final ExecutionPlan.Node node, final ConcreteContext context, final int cap) {
        this.node = node;
        this.context = context;
        this.cap = cap;
    }

    ConcreteContext context() {
        return context;
    }

    /**
     * Claims the next unstarted child.
     *
     * @return the child, or {@code null} if every child has been claimed
     */
    synchronized ExecutionPlan.Node claim() {
        return next < node.children.length ? node.children[next++] : null;
    }

    /**
     * Reserves a lane for a new worker if the frame still has unstarted children and is below its cap.
     *
     * @return {@code true} if a lane was reserved; the caller must later call {@link #leave()}
     */
    synchronized boolean tryJoin() {
        if (next >= node.children.length || lanes >= cap) {
            return false;
        }
        lanes++;
        return true;
    }

    /**
     * Releases a lane reserved by {@link #tryJoin()}.
     */
    synchronized void leave() {
        lanes--;
        notifyAll();
    }

    synchronized void include(final Status status) {
        aggregator.include(status);
    }

    synchronized Status status() {
        return aggregator.status();
    }

    /**
     * Blocks the coordinating lane until every worker lane has left, the frame is abandoned, or the
     * thread is interrupted.
     *
     * @return {@code true} if the wait was interrupted
     */
    synchronized boolean awaitWorkers() {
        try {
            while (lanes > 1 && !abandoned) {
                wait();
            }
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }

    /**
     * Wakes the coordinating lane without waiting for workers that will never run, used on shutdown.
     */
    synchronized void abandon() {
        abandoned = true;
        notifyAll();
    }
}
