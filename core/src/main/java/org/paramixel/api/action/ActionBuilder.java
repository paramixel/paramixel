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
 * Anything that can produce an immutable {@link Action}: either a mutable, Paramixel-owned builder or
 * an already built {@link Action}, which builds itself.
 *
 * <p>Every action slot (a child, a body, a before or an after) accepts an {@code ActionBuilder}, so a
 * builder and a built action can be passed interchangeably. Builders are not thread-safe unless documented
 * otherwise. Calling {@link #build()} produces an immutable snapshot from the builder's current
 * configuration; builder inputs are built when they are accepted and are not retained for deferred building.
 */
public sealed interface ActionBuilder
        permits Action,
                Conditional.Builder,
                Instance.Builder,
                Isolated.Builder,
                Loop.Builder,
                Parallel.Builder,
                Scope.Builder,
                Sequential.Builder,
                Timeout.Builder {

    /**
     * Builds an immutable action snapshot from this builder's current configuration.
     *
     * <p>For an {@link Action} this returns the action itself.
     *
     * @return an action; never {@code null}
     */
    Action build();
}
