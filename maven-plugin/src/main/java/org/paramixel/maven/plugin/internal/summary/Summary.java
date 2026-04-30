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

package org.paramixel.maven.plugin.internal.summary;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.paramixel.core.support.Arguments;

public class Summary {

    private SummaryNode root;

    private long startTime;

    private long duration;

    private final ConcurrentHashMap<String, SummaryNode> summaryNodeMap = new ConcurrentHashMap<>();

    private static final Set<SummaryStatus> SKIPPED_STATUSES = EnumSet.of(SummaryStatus.SKIPPED, SummaryStatus.ABORTED);

    public Summary() {
        // Intentionally empty
    }

    public SummaryNode getTree() {
        return root;
    }

    public void setRoot(SummaryNode root) {
        this.root = root;
        if (root != null) {
            summaryNodeMap.put(root.getUniqueId(), root);
        }
    }

    public SummaryNode findNode(String uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId must not be null");
        Arguments.requireNonBlank(uniqueId, "uniqueId must not be blank");
        return summaryNodeMap.get(uniqueId);
    }

    public SummaryNode createNode(String uniqueId, String displayName, String parentUniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId must not be null");
        Arguments.requireNonBlank(uniqueId, "uniqueId must not be blank");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Arguments.requireNonBlank(displayName, "displayName must not be blank");
        if (parentUniqueId != null) {
            Arguments.requireNonBlank(parentUniqueId, "parentUniqueId must not be blank");
        }
        SummaryNode node = new SummaryNode(uniqueId, displayName);
        summaryNodeMap.put(uniqueId, node);

        if (parentUniqueId == null) {
            setRoot(node);
        } else {
            SummaryNode parent = summaryNodeMap.get(parentUniqueId);
            if (parent != null) {
                parent.addChild(node);
            }
        }

        return node;
    }

    public boolean hasFailures() {
        if (root == null) {
            return false;
        }
        return root.hasFailures();
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        Arguments.requireNonNegative(duration, "duration must be non-negative");
        this.duration = duration;
    }

    public void recordStart() {
        this.startTime = System.currentTimeMillis();
    }

    public void recordEnd() {
        long endTime = System.currentTimeMillis();
        this.duration = endTime - startTime;
    }

    public int getTestClassCount() {
        if (root == null) {
            return 0;
        }
        return root.getChildren().size();
    }

    public int getTestClassPassed() {
        return countByStatus(root, SummaryStatus.SUCCESSFUL, 1);
    }

    public int getTestClassFailed() {
        return countByStatus(root, SummaryStatus.FAILED, 1);
    }

    public int getTestClassSkipped() {
        return countByStatuses(root, SKIPPED_STATUSES, 1);
    }

    public int getTestArgumentPassed() {
        return countByStatus(root, SummaryStatus.SUCCESSFUL, 2);
    }

    public int getTestArgumentFailed() {
        return countByStatus(root, SummaryStatus.FAILED, 2);
    }

    public int getTestArgumentSkipped() {
        return countByStatuses(root, SKIPPED_STATUSES, 2);
    }

    public int getTestMethodPassed() {
        return countByStatus(root, SummaryStatus.SUCCESSFUL, 3);
    }

    public int getTestMethodFailed() {
        return countByStatus(root, SummaryStatus.FAILED, 3);
    }

    public int getTestMethodSkipped() {
        return countByStatuses(root, SKIPPED_STATUSES, 3);
    }

    private int countByStatuses(SummaryNode node, Set<SummaryStatus> statuses, int depth) {
        if (node == null) {
            return 0;
        }
        if (depth == 0) {
            return statuses.contains(node.getStatus()) ? 1 : 0;
        }
        int count = 0;
        for (SummaryNode child : node.getChildren()) {
            count += countByStatuses(child, statuses, depth - 1);
        }
        return count;
    }

    private int countByStatus(SummaryNode node, SummaryStatus status, int depth) {
        if (node == null) {
            return 0;
        }
        if (depth == 0) {
            return node.getStatus() == status ? 1 : 0;
        }
        int count = 0;
        for (SummaryNode child : node.getChildren()) {
            count += countByStatus(child, status, depth - 1);
        }
        return count;
    }
}
