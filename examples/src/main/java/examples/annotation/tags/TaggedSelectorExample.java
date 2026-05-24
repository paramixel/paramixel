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

package examples.annotation.tags;

import org.paramixel.api.selector.Selector;

/**
 * Demonstrates building a {@link Selector} that filters action factories by tag using
 * a regular expression match.
 */
public final class TaggedSelectorExample {

    private TaggedSelectorExample() {
        // Intentionally empty
    }

    /**
     * Creates a selector that selects action factories in this package tagged
     * with exactly {@code "smoke"}.
     *
     * @return a tag-filtered selector
     */
    public static Selector smokeSelector() {
        return Selector.and(Selector.packageTreeOf(TaggedSelectorExample.class), Selector.tagRegex("^smoke$"));
    }
}
