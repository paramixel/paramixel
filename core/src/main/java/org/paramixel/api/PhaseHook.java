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

import java.util.Objects;

/**
 * The single {@code before} or {@code after} slot of one task node.
 *
 * <p>A node has at most one before slot and at most one after slot, and each slot is exactly one of two things:
 *
 * <ul>
 *   <li>{@link Callback} — an {@link Paramixel.Id} hook resolved against the definition's anchor class, written
 *       {@code .before("setUp")};</li>
 *   <li>{@link Subtree} — a task definition, written {@code .before(Sequential.builder("setup").step(...))}, which runs
 *       as a subtree before the node's own steps.</li>
 * </ul>
 *
 * <p>A subtree slot is part of the owning node's sequential chain for {@link Context} scoping: a value an entry
 * {@code put}s inside a {@link Sequential} before-subtree is visible to the owning node's steps and after slot. The
 * usual rule still applies inside the subtree, so state written by a {@link Parallel} branch never crosses that
 * parallel boundary.
 *
 * <p>Composition replaces repetition. Two setup phases are written
 * {@code .before(Sequential.builder("setup").step(setUpA).step(setUpB))}; two cleanup phases are written
 * {@code .after(Sequential.builder("cleanup").independent().step(tearDownB).step(tearDownA))}. A {@link Sequential}
 * cleanup subtree is {@link SequentialBuilder#dependent() dependent} by default, so a failing cleanup step skips the
 * later ones; call {@link SequentialBuilder#independent()} when every cleanup step must run.
 *
 * @see Task#before()
 * @see Task#after()
 * @see TaskBuilder#before(Task)
 * @see TaskBuilder#after(Task)
 */
public sealed interface PhaseHook permits PhaseHook.Callback, PhaseHook.Subtree {

    /**
     * A {@code before} or {@code after} slot backed by an identified hook method.
     *
     * @param callback the resolved hook; never {@code null}
     */
    record Callback(TaskCallback callback) implements PhaseHook {

        /**
         * Creates a callback slot.
         *
         * @param callback the resolved hook; must not be {@code null}
         * @throws NullPointerException if {@code callback} is {@code null}
         */
        public Callback {
            Objects.requireNonNull(callback, "callback is null");
        }
    }

    /**
     * A {@code before} or {@code after} slot backed by a task subtree.
     *
     * @param task the subtree root; never {@code null}
     */
    record Subtree(Task task) implements PhaseHook {

        /**
         * Creates a subtree slot.
         *
         * @param task the subtree root; must not be {@code null}
         * @throws NullPointerException if {@code task} is {@code null}
         */
        public Subtree {
            Objects.requireNonNull(task, "task is null");
        }
    }
}
