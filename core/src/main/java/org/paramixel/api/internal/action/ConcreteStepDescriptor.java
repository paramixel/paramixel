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

package org.paramixel.api.internal.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Descriptor;
import org.paramixel.api.action.Metadata;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.ConcreteMetadata;
import org.paramixel.api.internal.support.FastId;
import org.paramixel.spi.action.Mode;

/**
 * Mutable descriptor for {@link Step} action occurrences.
 *
 * <p>Tree structure and identity are held directly. Execution state is delegated to
 * the enclosed {@link ConcreteMetadata} instance.
 */
public final class ConcreteStepDescriptor implements MutableDescriptor {

    private final Step<?> action;
    private final ConcreteMetadata metadata;
    private final List<MutableDescriptor> children = new ArrayList<>();
    private volatile List<Descriptor> frozenChildren;
    volatile MutableDescriptor parent;
    private volatile SchedulerPriorityKey schedulerPriorityKey = SchedulerPriorityKey.root();
    private CompletableFuture<Descriptor> scheduledFuture;

    /**
     * Creates a descriptor for a {@link Step} action.
     *
     * @param parent the parent descriptor, or {@code null} for root
     * @param action the step action bound to the descriptor; must not be {@code null}
     */
    public ConcreteStepDescriptor(final MutableDescriptor parent, final Step<?> action) {
        this.parent = parent;
        this.action = Objects.requireNonNull(action, "action must not be null");
        var id = FastId.generateId();
        this.metadata =
                new ConcreteMetadata(id, action.name(), action.getClass().getName(), action.kind());
    }

    @Override
    public Action<?> action() {
        return action;
    }

    /**
     * Returns the step action bound to this descriptor.
     *
     * @return the step action; never {@code null}
     */
    public Step<?> step() {
        return action;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public synchronized Optional<Descriptor> parent() {
        return Optional.ofNullable(parent);
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
    public synchronized void addChild(final MutableDescriptor child) {
        Objects.requireNonNull(child, "child must not be null");
        if (child == this) {
            throw new IllegalArgumentException("child must not be this descriptor");
        }
        ensureNotFrozen();
        child.setParent(this);
        child.setSchedulerPriorityKey(schedulerPriorityKey.child(children.size()));
        children.add(child);
    }

    @Override
    public void setParent(final MutableDescriptor parent) {
        ensureNotFrozen();
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
    }

    @Override
    public SchedulerPriorityKey schedulerPriorityKey() {
        return schedulerPriorityKey;
    }

    @Override
    public synchronized void setSchedulerPriorityKey(final SchedulerPriorityKey schedulerPriorityKey) {
        ensureNotFrozen();
        this.schedulerPriorityKey =
                Objects.requireNonNull(schedulerPriorityKey, "schedulerPriorityKey must not be null");
        for (var i = 0; i < children.size(); i++) {
            children.get(i).setSchedulerPriorityKey(this.schedulerPriorityKey.child(i));
        }
    }

    @Override
    public void freeze() {
        List<Descriptor> snapshot;
        synchronized (this) {
            if (frozenChildren != null) {
                snapshot = frozenChildren;
            } else {
                frozenChildren = List.copyOf(children);
                snapshot = frozenChildren;
            }
        }

        for (var child : snapshot) {
            ((MutableDescriptor) child).freeze();
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
        Objects.requireNonNull(requestedMode, "requestedMode must not be null");
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
        metadata.setStatus(Objects.requireNonNull(newStatus, "status must not be null"));
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
