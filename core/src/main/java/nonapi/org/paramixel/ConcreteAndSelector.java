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

package nonapi.org.paramixel;

import java.util.List;
import java.util.Objects;
import org.paramixel.api.selector.AndSelector;
import org.paramixel.api.selector.Selector;

/**
 * Concrete implementation of {@link AndSelector}.
 *
 * <p>A candidate matches when it matches <em>all</em> of the composed selectors.
 */
public final class ConcreteAndSelector implements AndSelector {

    private final List<Selector> selectors;

    /**
     * Creates an AND selector composing the supplied selectors.
     *
     * @param selectors the unmodifiable list of composed selectors; must not be {@code null}
     */
    public ConcreteAndSelector(final List<Selector> selectors) {
        this.selectors = Objects.requireNonNull(selectors, "selectors is null");
    }

    @Override
    public List<Selector> selectors() {
        return selectors;
    }

    @Override
    public boolean matchesPackage(final String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");
        for (Selector selector : selectors) {
            if (!selector.matchesPackage(packageName)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matchesClass(final String className) {
        Objects.requireNonNull(className, "className is null");
        for (Selector selector : selectors) {
            if (!selector.matchesClass(className)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matchesTag(final String tag) {
        Objects.requireNonNull(tag, "tag is null");
        for (Selector selector : selectors) {
            if (!selector.matchesTag(tag)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ConcreteAndSelector other)) return false;
        return selectors.equals(other.selectors);
    }

    @Override
    public int hashCode() {
        return selectors.hashCode();
    }

    @Override
    public String toString() {
        return "AndSelector" + selectors;
    }
}
