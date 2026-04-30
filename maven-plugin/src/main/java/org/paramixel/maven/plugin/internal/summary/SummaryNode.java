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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.paramixel.core.support.Arguments;

public class SummaryNode {

    private final String uniqueId;

    private final String displayName;

    private final List<SummaryNode> children = new CopyOnWriteArrayList<>();

    private volatile SummaryNode parent;

    private volatile SummaryStatus status;

    private Throwable throwable;

    private String skipReason;

    private volatile long startTime;

    private volatile long duration;

    public SummaryNode(String uniqueId, String displayName) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId must not be null");
        Arguments.requireNonBlank(uniqueId, "uniqueId must not be blank");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        Arguments.requireNonBlank(displayName, "displayName must not be blank");
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SummaryNode getParent() {
        return parent;
    }

    public List<SummaryNode> getChildren() {
        return children;
    }

    public void addChild(SummaryNode child) {
        Objects.requireNonNull(child, "child must not be null");
        child.parent = this;
        children.add(child);
    }

    public SummaryStatus getStatus() {
        return status;
    }

    public void setStatus(SummaryStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        this.status = status;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        if (skipReason != null) {
            Arguments.requireNonBlank(skipReason, "skipReason must not be blank");
        }
        this.skipReason = skipReason;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        Arguments.requireNonNegative(duration, "duration must be non-negative");
        this.duration = duration;
    }

    public synchronized void recordStart() {
        this.startTime = System.currentTimeMillis();
        this.duration = 0L;
    }

    public synchronized void recordEnd() {
        long endTime = System.currentTimeMillis();
        this.duration = endTime - startTime;
    }

    public boolean hasFailures() {
        if (status == SummaryStatus.FAILED) {
            return true;
        }
        for (SummaryNode child : children) {
            if (child.hasFailures()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "SummaryNode{" + "uniqueId='"
                + uniqueId + '\'' + ", displayName='"
                + displayName + '\'' + ", status="
                + status + ", duration="
                + duration + "ms}";
    }
}
