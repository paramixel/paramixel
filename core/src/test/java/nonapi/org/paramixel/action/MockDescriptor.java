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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.support.FastId;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;

/**
 * Test-only descriptor that keeps the old imperative construction style for tests that only need a tree to render.
 *
 * <p>Production descriptors are immutable and built bottom-up by the compile pass; this stub exists so listener and
 * report tests can assemble arbitrary shapes without depending on the compile rules. It is deliberately simple: it
 * records statuses and structure and implements no engine semantics.
 */
public final class MockDescriptor implements MutableDescriptor {

    private final Action action;
    private final String id = FastId.generateId();
    private MutableDescriptor parent;
    private MutableDescriptor before;
    private final List<Descriptor> children = new ArrayList<>();
    private MutableDescriptor after;
    private volatile Status status = Status.PENDING;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile boolean executing;

    /**
     * Creates a mock descriptor.
     *
     * @param action the action to bind; must not be {@code null}
     */
    public MockDescriptor(final Action action) {
        this.action = Objects.requireNonNull(action, "action is null");
    }

    /**
     * Creates a mock descriptor with a parent.
     *
     * @param parent the parent descriptor, or {@code null}
     * @param action the action to bind; must not be {@code null}
     */
    public MockDescriptor(final MutableDescriptor parent, final Action action) {
        this(action);
        this.parent = parent;
    }

    /**
     * Sets the before slot.
     *
     * @param before the before descriptor; must not be {@code null}
     * @return this descriptor
     */
    public MockDescriptor before(final MutableDescriptor before) {
        this.before = Objects.requireNonNull(before, "before is null");
        before.attach(this);
        return this;
    }

    /**
     * Adds a body child.
     *
     * @param child the child descriptor; must not be {@code null}
     * @return this descriptor
     */
    public MockDescriptor child(final MutableDescriptor child) {
        children.add(Objects.requireNonNull(child, "child is null"));
        child.attach(this);
        return this;
    }

    /**
     * Sets the after slot.
     *
     * @param after the after descriptor; must not be {@code null}
     * @return this descriptor
     */
    public MockDescriptor after(final MutableDescriptor after) {
        this.after = Objects.requireNonNull(after, "after is null");
        after.attach(this);
        return this;
    }

    @Override
    public void attach(final MutableDescriptor parent) {
        this.parent = Objects.requireNonNull(parent, "parent is null");
    }

    @Override
    public void setStatus(final Status newStatus) {
        Objects.requireNonNull(newStatus, "status is null");
        if (newStatus.isPending()) {
            throw new IllegalArgumentException("Cannot set PENDING status");
        }
        if (status.isPending() && newStatus.isRunning()) {
            status = Status.RUNNING;
            startedAt = Instant.now();
            return;
        }
        if (status.isRunning() && newStatus.kind().isTerminal()) {
            status = newStatus;
            completedAt = Instant.now();
        }
    }

    @Override
    public void replaceStatus(final Status newStatus) {
        if (status.kind().isTerminal() && newStatus.kind().isTerminal()) {
            status = newStatus;
        }
    }

    @Override
    public void interruptExecutingThread() {
        // No thread tracking in the stub.
    }

    @Override
    public boolean tryStart() {
        if (status.isPending()) {
            setStatus(Status.RUNNING);
            return true;
        }
        return status.isRunning();
    }

    @Override
    public boolean isExecuting() {
        return executing;
    }

    /**
     * Records whether this descriptor is executing.
     *
     * @param executing whether this descriptor is executing
     */
    public void setExecuting(final boolean executing) {
        this.executing = executing;
    }

    @Override
    public boolean abort(final Status rootStatus, final Throwable cause) {
        var started = status.isRunning();
        if (status.isPending()) {
            setStatus(Status.RUNNING);
        }
        if (!status.kind().isTerminal()) {
            setStatus(rootStatus);
        }
        return started;
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
    public String path() {
        var names = new java.util.ArrayDeque<String>();
        for (Descriptor d = this; d != null; d = d.parent().orElse(null)) {
            names.addFirst(d.action().displayName());
        }
        return String.join(" / ", names);
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
        return List.copyOf(children);
    }

    @Override
    public Optional<Descriptor> after() {
        return Optional.ofNullable(after);
    }
}
