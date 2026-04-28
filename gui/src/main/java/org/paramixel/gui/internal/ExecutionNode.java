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

package org.paramixel.gui.internal;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.control.TreeItem;

/**
 * A node in the execution tree displayed in the GUI.
 */
public final class ExecutionNode {

    /**
     * The kind of node.
     */
    public enum Kind {
        /** The root execution node. */
        EXECUTION,
        /** A branch node with children. */
        BRANCH,
        /** A leaf node. */
        LEAF
    }

    /**
     * The status of a node.
     */
    public enum Status {
        /** Waiting to run. */
        WAITING,
        /** Currently running. */
        RUNNING,
        /** Completed successfully. */
        SUCCESSFUL,
        /** Skipped. */
        SKIPPED,
        /** Failed. */
        FAILED;

        /**
         * Returns the worst of two statuses.
         *
         * @param left The first status.
         * @param right The second status.
         * @return The status with higher severity.
         */
        public static Status worst(Status left, Status right) {
            return severity(left) >= severity(right) ? left : right;
        }

        private static int severity(Status status) {
            return switch (status) {
                case WAITING -> 0;
                case RUNNING -> 1;
                case SUCCESSFUL -> 2;
                case SKIPPED -> 3;
                case FAILED -> 4;
            };
        }
    }

    private final String id;
    private final Kind kind;
    private final String displayName;

    private volatile Status status = Status.WAITING;
    private volatile boolean completed;
    private volatile Throwable throwable;
    private volatile String skipReason;
    private volatile Duration timing;
    private volatile ExecutionNode parent;
    private volatile TreeItem<ExecutionNode> treeItem;

    private final CopyOnWriteArrayList<ExecutionNode> children = new CopyOnWriteArrayList<>();

    /**
     * Creates an execution node.
     *
     * @param id The unique ID.
     * @param kind The kind of node.
     * @param displayName The display name.
     */
    public ExecutionNode(String id, Kind kind, String displayName) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
    }

    /**
     * Returns the unique ID.
     *
     * @return The ID.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the kind of node.
     *
     * @return The kind.
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the display name.
     *
     * @return The display name.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the status.
     *
     * @return The status.
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the failure throwable, if any.
     *
     * @return The throwable, or null.
     */
    public Throwable throwable() {
        return throwable;
    }

    /**
     * Returns the skip reason, if any.
     *
     * @return The skip reason, or null.
     */
    public String skipReason() {
        return skipReason;
    }

    /**
     * Returns the timing.
     *
     * @return The timing, or null.
     */
    public Duration timing() {
        return timing;
    }

    /**
     * Returns the parent node.
     *
     * @return The parent node, or null.
     */
    public ExecutionNode parent() {
        return parent;
    }

    /**
     * Returns the children of this node.
     *
     * @return An unmodifiable list of children.
     */
    public List<ExecutionNode> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the tree item associated with this node.
     *
     * @return The tree item, or null.
     */
    public TreeItem<ExecutionNode> treeItem() {
        return treeItem;
    }

    /**
     * Sets the tree item associated with this node.
     *
     * @param treeItem The tree item.
     */
    public void setTreeItem(TreeItem<ExecutionNode> treeItem) {
        this.treeItem = treeItem;
    }

    /**
     * Returns whether this node is completed.
     *
     * @return True if completed.
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Sets the parent node.
     *
     * @param parent The parent node.
     */
    public void setParent(ExecutionNode parent) {
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
            parent.recomputeStatus();
        }
    }

    /**
     * Sets the status.
     *
     * @param status The status.
     */
    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (parent != null) {
            parent.recomputeStatus();
        }
    }

    /**
     * Marks the node as running.
     */
    public void markRunning() {
        completed = false;
        throwable = null;
        skipReason = null;
        setStatus(Status.RUNNING);
    }

    /**
     * Marks the node as completed.
     *
     * @param status The final status.
     * @param throwable The failure, if any.
     * @param skipReason The skip reason, if any.
     * @param timing The timing, if any.
     */
    public void markCompleted(Status status, Throwable throwable, String skipReason, Duration timing) {
        this.completed = true;
        this.throwable = throwable;
        this.skipReason = skipReason;
        this.timing = timing;
        this.status = Objects.requireNonNull(status, "status must not be null");
        recomputeStatus();
    }

    /**
     * Recomputes the status based on children.
     */
    public synchronized void recomputeStatus() {
        Status aggregate = status;
        boolean hasActivity = status != Status.WAITING;

        for (ExecutionNode child : children) {
            Status childStatus = child.status();
            aggregate = Status.worst(aggregate, childStatus);
            if (childStatus != Status.WAITING) {
                hasActivity = true;
            }
        }

        if (!completed && hasActivity && aggregate == Status.SUCCESSFUL) {
            aggregate = Status.RUNNING;
        } else if (!completed && hasActivity && aggregate == Status.WAITING) {
            aggregate = Status.RUNNING;
        }

        this.status = aggregate;
        if (parent != null) {
            parent.recomputeStatus();
        }
    }

    @Override
    public String toString() {
        return kind + "{" + "displayName='" + displayName + '\'' + ", status=" + status + '}';
    }
}
