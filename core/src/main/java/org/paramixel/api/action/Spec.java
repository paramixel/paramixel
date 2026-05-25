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

package org.paramixel.api.action;

/**
 * Resolves an immutable {@link Action} from accumulated configuration, or returns a pre-built
 * action.
 *
 * <p>Implementations fall into two categories:
 *
 * <ul>
 *   <li><b>Accumulating specs</b> — single-use; calling {@link #resolve()} or any mutating
 *       method after {@code resolve()} has been called throws {@link IllegalStateException}. When an
 *       accumulating spec is passed to a composing method such as
 *       {@link Lifecycle.Spec#child(Spec) child},
 *       {@link Lifecycle.Spec#before(Spec) before}, or
 *       {@link Lifecycle.Spec#after(Spec) after}, the receiving method calls
 *       {@link #resolve()} internally, consuming the spec.
 *   <li><b>Pre-built actions</b> — {@link Action} provides a default {@code resolve()} that is
 *       idempotent and returns {@code this}. Any {@link Action} may be passed where a
 *       {@code Spec} is expected.
 * </ul>
 *
 * @param <T> the type consumed by the action resolved by this spec
 * @see Lifecycle.Spec
 * @see Instance.Spec
 * @see Sequential.Spec
 * @see Parallel.Spec
 * @see Static.Spec
 * @see Timeout.Spec
 * @see Repeat.Spec
 */
public interface Spec<T> {

    /**
     * Resolves an immutable action from the accumulated configuration on this spec, or returns
     * {@code this} if this spec is already a pre-built action.
     *
     * <p>Accumulating spec instances may produce exactly one action; subsequent calls throw
     * {@link IllegalStateException}. Pre-built action instances return {@code this} idempotently.
     *
     * @return the resolved action; never {@code null}
     * @throws IllegalStateException if this accumulating spec has already been resolved or contains
     *     invalid configuration
     */
    Action<T> resolve();
}
