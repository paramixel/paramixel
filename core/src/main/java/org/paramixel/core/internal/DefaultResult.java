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
import org.paramixel.core.Action;
import org.paramixel.core.Result;

/**
 * The default implementation of {@link Result}.
 */
public final class DefaultResult implements Result {

    private final Action action;
    private final Status status;
    private final Duration timing;
    private final Throwable failure;
    private final List<Result> children;
    private volatile Result parent;

    private DefaultResult(Action action, Status status, Duration timing, Throwable failure, List<Result> children) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.timing = Objects.requireNonNull(timing, "timing must not be null");
        this.failure = failure;
        this.children = Collections.unmodifiableList(Objects.requireNonNull(children, "children must not be null"));
        for (Result child : children) {
            if (child instanceof DefaultResult dr) {
                dr.setParent(this);
            }
        }
    }

    /**
     * Creates a result.
     *
     * @param action The action.
     * @param status The status.
     * @param timing The timing.
     * @param failure The failure, if any.
     * @param children The child results.
     * @return A new DefaultResult.
     */
    public static DefaultResult of(
            Action action, Status status, Duration timing, Throwable failure, List<Result> children) {
        return new DefaultResult(action, status, timing, failure, children);
    }

    /**
     * Creates a passing result.
     *
     * @param action The action.
     * @param timing The timing.
     * @return A new DefaultResult.
     */
    public static DefaultResult pass(Action action, Duration timing) {
        return of(action, Status.PASS, timing, null, List.of());
    }

    /**
     * Creates a passing result with children.
     *
     * @param action The action.
     * @param timing The timing.
     * @param children The child results.
     * @return A new DefaultResult.
     */
    public static DefaultResult pass(Action action, Duration timing, List<Result> children) {
        return of(action, Status.PASS, timing, null, children);
    }

    /**
     * Creates a failing result.
     *
     * @param action The action.
     * @param timing The timing.
     * @param failure The failure.
     * @return A new DefaultResult.
     */
    public static DefaultResult fail(Action action, Duration timing, Throwable failure) {
        return of(action, Status.FAIL, timing, failure, List.of());
    }

    /**
     * Creates a failing result with children.
     *
     * @param action The action.
     * @param timing The timing.
     * @param failure The failure.
     * @param children The child results.
     * @return A new DefaultResult.
     */
    public static DefaultResult fail(Action action, Duration timing, Throwable failure, List<Result> children) {
        return of(action, Status.FAIL, timing, failure, children);
    }

    /**
     * Creates a skipped result.
     *
     * @param action The action.
     * @param timing The timing.
     * @return A new DefaultResult.
     */
    public static DefaultResult skip(Action action, Duration timing) {
        return of(action, Status.SKIP, timing, null, List.of());
    }

    /**
     * Creates a skipped result with a reason.
     *
     * @param action The action.
     * @param timing The timing.
     * @param skipReason The reason for skipping.
     * @return A new DefaultResult.
     */
    public static DefaultResult skip(Action action, Duration timing, Throwable skipReason) {
        return of(action, Status.SKIP, timing, skipReason, List.of());
    }

    /**
     * Creates a skipped result with children.
     *
     * @param action The action.
     * @param timing The timing.
     * @param children The child results.
     * @return A new DefaultResult.
     */
    public static DefaultResult skip(Action action, Duration timing, List<Result> children) {
        return of(action, Status.SKIP, timing, null, children);
    }

    @Override
    public Action action() {
        return action;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public Duration timing() {
        return timing;
    }

    @Override
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    @Override
    public Optional<Result> parent() {
        return Optional.ofNullable(parent);
    }

    void setParent(Result parent) {
        this.parent = parent;
    }

    @Override
    public List<Result> children() {
        return children;
    }

    @Override
    public String toString() {
        return status + " | " + timing.toMillis() + " ms";
    }
}
