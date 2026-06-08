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
 * A mutable, Paramixel-owned action builder that produces an immutable {@link Action} snapshot.
 *
 * <p>Builders are not thread-safe unless documented otherwise. Calling {@link #build()} produces
 * an immutable snapshot from the builder's current configuration. Builder inputs are not retained
 * for deferred building.
 */
public sealed interface Builder
        permits Conditional.Builder,
                Instance.Builder,
                Isolated.Builder,
                Parallel.Builder,
                Repeat.Builder,
                Scope.Builder,
                Sequence.Builder,
                Static.Builder,
                Timeout.Builder,
                Until.Builder {

    /**
     * Builds an immutable action snapshot from this builder's current configuration.
     *
     * @return a new action; never {@code null}
     */
    Action build();
}
