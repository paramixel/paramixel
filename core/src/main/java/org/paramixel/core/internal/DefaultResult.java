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

package org.paramixel.core.internal;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Status;

/**
 * Default mutable {@link Result} implementation used while a run is in progress.
 *
 * <p>This mutable result implementation stores the run {@link Action}, aggregate child results, final {@link Status}, and run duration
 * data. It is primarily intended for Paramixel internals and advanced integrations that need to construct result trees
 * manually.
 */
public final class DefaultResult implements Result {

    private record State(Status status, Duration runDuration) {
        State {
            Objects.requireNonNull(status);
            Objects.requireNonNull(runDuration);
        }
    }

    private final Action action;
    private final AtomicReference<State> state = new AtomicReference<>(new State(DefaultStatus.STAGED, Duration.ZERO));
    private volatile Result parent;
    private final List<Result> children = new CopyOnWriteArrayList<>();

    /**
     * Creates a staged result for the supplied action.
     *
     * @param action the action represented by this result
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public DefaultResult(Action action) {
        this.action = Objects.requireNonNull(action, "action must not be null");
    }

    /**
     * Creates a result with explicit status and run duration values.
     *
     * @param action the action represented by this result
     * @param status the initial run status for this result
     * @param runDuration the initial wall-clock duration for this result
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultResult(Action action, Status status, Duration runDuration) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.state.set(new State(
                Objects.requireNonNull(status, "status must not be null"),
                Objects.requireNonNull(runDuration, "runDuration must not be null")));
    }

    /**
     * Atomically updates both the status and run duration recorded for this result.
     *
     * <p>This method provides atomicity guarantees that separate {@link #setStatus(Status)} and
     * {@link #setRunDuration(Duration)} calls cannot: a concurrent reader calling {@link #getStatus()} and
     * {@link #getRunDuration()} will never observe a new status paired with a stale duration.
     *
     * @param status the final run status to record
     * @param runDuration the wall-clock duration to record atomically with the status
     * @throws NullPointerException if any argument is {@code null}
     */
    public void complete(Status status, Duration runDuration) {
        this.state.set(new State(
                Objects.requireNonNull(status, "status must not be null"),
                Objects.requireNonNull(runDuration, "runDuration must not be null")));
    }

    /**
     * Updates the status recorded for this result, preserving the current run duration.
     *
     * @param status the updated run status
     * @throws NullPointerException if {@code status} is {@code null}
     */
    public void setStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        state.updateAndGet(s -> new State(status, s.runDuration()));
    }

    /**
     * Updates the run duration recorded for this result, preserving the current status.
     *
     * @param runDuration the updated wall-clock duration
     * @throws NullPointerException if {@code runDuration} is {@code null}
     */
    public void setRunDuration(Duration runDuration) {
        Objects.requireNonNull(runDuration, "runDuration must not be null");
        state.updateAndGet(s -> new State(s.status(), runDuration));
    }

    /**
     * Sets the parent result for this result.
     *
     * @param parent the enclosing result for a nested action
     * @throws NullPointerException if {@code parent} is {@code null}
     */
    public void setParent(Result parent) {
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
    }

    /**
     * Adds a child result and assigns this result as its parent when possible.
     *
     * @param child the result produced by a nested action run
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public void addChild(Result child) {
        Objects.requireNonNull(child, "child must not be null");
        children.add(child);
        if (child instanceof DefaultResult dr) {
            dr.setParent(this);
        }
    }

    @Override
    public Status getStatus() {
        return state.get().status();
    }

    @Override
    public Duration getRunDuration() {
        return state.get().runDuration();
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public Optional<Result> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public List<Result> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public String toString() {
        State snapshot = state.get();
        return snapshot.status() + " | " + snapshot.runDuration().toMillis() + " ms";
    }
}
