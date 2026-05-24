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

package org.paramixel.api.selector;

import java.util.List;

/**
 * A {@link Selector} that combines multiple selectors with logical AND semantics.
 *
 * <p>A candidate matches an {@code AndSelector} when it matches <em>all</em> of the
 * composed selectors. The returned {{@link #selectors()}} list is unmodifiable.
 *
 * @see Selector#and(Selector...)
 * @see Selector#and(List)
 */
public interface AndSelector extends Selector {

    /**
     * Returns the selectors composed by this AND selector.
     *
     * @return the unmodifiable list of composed selectors; never {@code null} and never empty
     */
    List<Selector> selectors();
}
