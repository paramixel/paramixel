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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import nonapi.org.paramixel.ConcreteMetadata;
import nonapi.org.paramixel.support.FastId;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Metadata;
import org.paramixel.api.action.Mode;

/**
 * Mutable descriptor for all action occurrences in a descriptor tree.
 *
 * <p>Structurally mirrors {@link Action}: {@link #before()} and {@link #after()} are
 * separate slots, not included in {@link #children()}. Tree structure and identity are
 * held directly. Execution state is delegated to the enclosed {@link ConcreteMetadata}
 * instance.
 */
public final class ConcreteDescriptor implements MutableDescriptor {

    private final Action<?> action;
    private final ConcreteMetadata metadata;
    private volatile MutableDescriptor before;
    private final List<MutableDescriptor> children = new ArrayList<>();
    private volatile MutableDescriptor after;
    private volatile List<Descriptor> frozenChildren;
    private volatile SchedulerPriorityKey schedulerPriorityKey = SchedulerPriorityKey.root();
    private CompletableFuture<Descriptor> scheduledFuture;
    volatile MutableDescriptor parent;

    /**
     * Creates a root descriptor.
     *
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final Action<?> action) {
        this(null, action);
    }

    /**
     * Creates a descriptor with an optional parent.
     *
     * @param parent the parent descriptor, or {@code null} for root
     * @param action the action bound to the descriptor; must not be {@code null}
     */
    public ConcreteDescriptor(final MutableDescriptor parent, final Action<?> action) {
        this.parent = parent;
        this.action = Objects.requireNonNull(action, "action is null");
        var id = FastId.generateId();
        this.metadata =
                new ConcreteMetadata(id, action.name(), action.getClass().getName(), action.kind());
    }

    @Override
    public Action<?> action() {
        return action;
    }

    @Override
    public Metadata metadata() {
        return metadata;
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
        var cachedChildren = frozenChildren;
        if (cachedChildren != null) {
            return cachedChildren;
        }
        synchronized (this) {
            cachedChildren = frozenChildren;
            if (cachedChildren != null) {
                return cachedChildren;
            }
            return List.copyOf(children);
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
            if (frozenChildren != null) {
                childrenSnapshot = frozenChildren;
            } else {
                frozenChildren = List.copyOf(children);
                childrenSnapshot = frozenChildren;
            }
            afterSnapshot = after;
        }

        if (beforeSnapshot != null) {
            beforeSnapshot.freeze();
        }
        for (var child : childrenSnapshot) {
            ((MutableDescriptor) child).freeze();
        }
        if (afterSnapshot != null) {
            afterSnapshot.freeze();
        }
    }

    @Override
    public boolean isFrozen() {
        return frozenChildren != null;
    }

    private void ensureNotFrozen() {
        if (frozenChildren != null) {
            throw new IllegalStateException("Descriptor is frozen: " + metadata.id());
        }
    }

    @Override
    public synchronized CompletableFuture<Descriptor> markScheduled(final Mode requestedMode) {
        Objects.requireNonNull(requestedMode, "requestedMode is null");
        if (scheduledFuture != null) {
            throw new IllegalStateException("Descriptor already scheduled: " + metadata.id());
        }
        metadata.setMode(requestedMode);
        scheduledFuture = new CompletableFuture<>();
        return scheduledFuture;
    }

    @Override
    public synchronized boolean isScheduled() {
        return scheduledFuture != null;
    }

    @Override
    public synchronized void setStatus(final Status newStatus) {
        metadata.setStatus(Objects.requireNonNull(newStatus, "status is null"));
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
}
