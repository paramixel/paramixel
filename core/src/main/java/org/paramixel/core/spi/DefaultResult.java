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

package org.paramixel.core.spi;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Status;

/**
 * Default mutable {@link Result} implementation used while a run is in progress.
 *
 * <p>This SPI type stores the executed {@link Action}, aggregate child results, final {@link Status}, and elapsed
 * timing data. It is primarily intended for Paramixel internals and advanced integrations that need to construct
 * result trees manually.
 */
public final class DefaultResult implements Result {

    private final Action action;
    private volatile Status status;
    private volatile Duration elapsedTime;
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
        this.status = DefaultStatus.STAGED;
        this.elapsedTime = Duration.ZERO;
    }

    /**
     * Creates a result with explicit status and elapsed time values.
     *
     * @param action the action represented by this result
     * @param status the initial status
     * @param elapsedTime the initial elapsed time
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultResult(Action action, Status status, Duration elapsedTime) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.elapsedTime = Objects.requireNonNull(elapsedTime, "elapsedTime must not be null");
    }

    /**
     * Updates the status recorded for this result.
     *
     * @param status the new status
     * @throws NullPointerException if {@code status} is {@code null}
     */
    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Updates the elapsed time recorded for this result.
     *
     * @param elapsedTime the elapsed time to record
     * @throws NullPointerException if {@code elapsedTime} is {@code null}
     */
    public void setElapsedTime(Duration elapsedTime) {
        this.elapsedTime = Objects.requireNonNull(elapsedTime, "elapsedTime must not be null");
    }

    /**
     * Sets the parent result for this result.
     *
     * @param parent the parent result
     * @throws NullPointerException if {@code parent} is {@code null}
     */
    public void setParent(Result parent) {
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
    }

    /**
     * Adds a child result and assigns this result as its parent when possible.
     *
     * @param child the child result to add
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
        return status;
    }

    @Override
    public Duration getElapsedTime() {
        return elapsedTime;
    }

    @Override
    public Duration getCumulativeElapsedTime() {
        if (children.isEmpty()) {
            return elapsedTime;
        }
        return children.stream().map(Result::getCumulativeElapsedTime).reduce(Duration.ZERO, Duration::plus);
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
        return status + " | " + elapsedTime.toMillis() + " ms";
    }
}
