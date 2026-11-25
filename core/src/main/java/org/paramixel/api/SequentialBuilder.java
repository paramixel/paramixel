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
 * Authors a {@link Sequential} task: direct steps run one at a time in resolved order.
 *
 * <p>Only a sequential builder exposes the failure-continuation policy. {@link #dependent()} is the default: after a
 * direct child finishes with any non-{@code PASSED} terminal outcome, later direct children are {@code SKIPPED} with an
 * explicit dependency reason. {@link #independent()} attempts every eligible direct child even after an earlier child
 * failed, skipped, or aborted; run cancellation, timeout, and fatal abort still override continuation.
 *
 * <p>The policy applies to this task's own direct children only. An independent inner group does not make its
 * enclosing dependent sequence independent, and a {@link Parallel} builder has no such policy at all.
 *
 * <p>A sequential subtree is also the natural way to compose several setup or cleanup phases, because a node has at
 * most one {@code before} slot and one {@code after} slot:
 *
 * <pre>{@code
 * Sequential.builder(MyTest.class, "scenario")
 *         .before(Sequential.builder("setup").step(ID_START_DB).step(ID_START_APP))
 *         .step(ID_CHECK)
 *         .after(Sequential.builder("cleanup").independent().step(ID_STOP_APP).step(ID_STOP_DB));
 * }</pre>
 */
public final class SequentialBuilder extends TaskBuilder<SequentialBuilder> {

    SequentialBuilder(final String name) {
        super(name, ExecutionMode.SEQUENTIAL, null);
    }

    @Override
    protected SequentialBuilder self() {
        return this;
    }

    @Override
    Sequential newTask(final TaskState state) {
        return new Sequential(state);
    }

    /**
     * Builds the immutable sequential task.
     *
     * @return the immutable task; never {@code null}
     * @throws org.paramixel.api.exception.CompilationException if an identifier is unknown, ambiguous, malformed, or has
     *     no anchor, or if one builder is attached more than once in this tree
     */
    @Override
    public Sequential build() {
        return (Sequential) super.build();
    }

    /**
     * Selects the default dependent policy: a non-passing direct child stops later direct children.
     *
     * @return this builder
     */
    public SequentialBuilder dependent() {
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
    public SequentialBuilder independent() {
        setDependent(false);
        return this;
    }
}
