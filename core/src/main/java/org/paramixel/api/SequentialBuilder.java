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

package org.paramixel.api;

/**
 * Authors a sequential task: direct children run one at a time in resolved order.
 *
 * <p>Only a sequential builder exposes the failure-continuation policy. {@link #dependent()} is the default: after a
 * direct child finishes with any non-{@code PASSED} terminal outcome, later direct children are {@code SKIPPED} with an
 * explicit dependency reason. {@link #independent()} attempts every eligible direct child even after an earlier child
 * failed, skipped, or aborted; run cancellation, timeout, and fatal abort still override continuation.
 *
 * <p>The policy applies to this task's own direct children only. An independent inner group does not make its
 * enclosing dependent sequence independent, and a {@code parallel} builder has no such policy at all.
 *
 * @param <T> the inherited primary instance type; {@link Void} when the task has no instance binding
 */
public final class SequentialBuilder<T> extends TaskBuilder<T, SequentialBuilder<T>> {

    SequentialBuilder(final String name) {
        super(name, ExecutionMode.SEQUENTIAL, null);
    }

    @Override
    protected SequentialBuilder<T> self() {
        return this;
    }

    /**
     * Selects the default dependent policy: a non-passing direct child stops later direct children.
     *
     * @return this builder
     */
    public SequentialBuilder<T> dependent() {
        setDependent(true);
        return this;
    }

    /**
     * Selects the independent policy: every eligible direct child is attempted even after a non-passing sibling.
     *
     * <p>An independent group still aggregates the most severe child status, so a failed sibling that is followed by
     * passing siblings leaves the group {@code FAILED}.
     *
     * @return this builder
     */
    public SequentialBuilder<T> independent() {
        setDependent(false);
        return this;
    }
}
