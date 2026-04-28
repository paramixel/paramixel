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

package org.paramixel.gui;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.control.TreeItem;
import org.paramixel.core.Action;

public final class DiscoveryNode {

    public enum Kind {
        ROOT,
        PACKAGE,
        CLASS,
        ACTION
    }

    public enum Status {
        NOT_RUN,
        RUNNING,
        PASSED,
        FAILED,
        SKIPPED;

        public static Status worst(Status left, Status right) {
            return severity(left) >= severity(right) ? left : right;
        }

        private static int severity(Status status) {
            return switch (status) {
                case NOT_RUN -> 0;
                case RUNNING -> 1;
                case PASSED -> 2;
                case SKIPPED -> 3;
                case FAILED -> 4;
            };
        }
    }

    private final String id;
    private final Kind kind;
    private final String displayName;
    private final String qualifiedName;
    private final Class<?> sourceClass;
    private final Action action;

    private volatile Status status = Status.NOT_RUN;
    private volatile Throwable throwable;
    private volatile DiscoveryNode parent;
    private volatile TreeItem<DiscoveryNode> treeItem;

    private final CopyOnWriteArrayList<DiscoveryNode> children = new CopyOnWriteArrayList<>();

    public DiscoveryNode(String id, Kind kind, String displayName, String qualifiedName) {
        this(id, kind, displayName, qualifiedName, null, null);
    }

    public DiscoveryNode(
            String id, Kind kind, String displayName, String qualifiedName, Class<?> sourceClass, Action action) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.qualifiedName = qualifiedName;
        this.sourceClass = sourceClass;
        this.action = action;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public String displayName() {
        return displayName;
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public Class<?> sourceClass() {
        return sourceClass;
    }

    public Action action() {
        return action;
    }

    public Status status() {
        return status;
    }

    public Throwable throwable() {
        return throwable;
    }

    public DiscoveryNode parent() {
        return parent;
    }

    public List<DiscoveryNode> children() {
        return Collections.unmodifiableList(children);
    }

    public TreeItem<DiscoveryNode> treeItem() {
        return treeItem;
    }

    public void setTreeItem(TreeItem<DiscoveryNode> treeItem) {
        this.treeItem = treeItem;
    }

    public void setParent(DiscoveryNode parent) {
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
            parent.recomputeStatus();
        }
    }

    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (parent != null) {
            parent.recomputeStatus();
        }
    }

    public void markRunning() {
        throwable = null;
        setStatus(Status.RUNNING);
    }

    public void markCompleted(Status status, Throwable throwable) {
        this.throwable = throwable;
        this.status = Objects.requireNonNull(status, "status must not be null");
        recomputeStatus();
    }

    public synchronized void recomputeStatus() {
        Status aggregate = status;
        boolean hasActivity = status != Status.NOT_RUN;

        for (DiscoveryNode child : children) {
            Status childStatus = child.status();
            aggregate = Status.worst(aggregate, childStatus);
            if (childStatus != Status.NOT_RUN) {
                hasActivity = true;
            }
        }

        if (!hasActivity && aggregate == Status.PASSED) {
            aggregate = Status.RUNNING;
        } else if (!hasActivity && aggregate == Status.NOT_RUN) {
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
