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

package nonapi.org.paramixel.selector;

import java.util.Objects;
import org.paramixel.api.selector.NotSelector;
import org.paramixel.api.selector.Selector;

/**
 * Concrete implementation of {@link NotSelector}.
 *
 * <p>A candidate matches when it does <em>not</em> match the negated selector.
 * All three {@code matches*} methods are strictly negated.
 */
public final class ConcreteNotSelector implements NotSelector {

    private final Selector selector;

    /**
     * Creates a NOT selector negating the supplied selector.
     *
     * @param selector the selector to negate; must not be {@code null}
     */
    public ConcreteNotSelector(final Selector selector) {
        this.selector = Objects.requireNonNull(selector, "selector is null");
    }

    @Override
    public Selector selector() {
        return selector;
    }

    @Override
    public boolean matchesPackage(final String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");
        return !selector.matchesPackage(packageName);
    }

    @Override
    public boolean matchesClass(final String className) {
        Objects.requireNonNull(className, "className is null");
        return !selector.matchesClass(className);
    }

    @Override
    public boolean matchesTag(final String tag) {
        Objects.requireNonNull(tag, "tag is null");
        return !selector.matchesTag(tag);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ConcreteNotSelector other)) return false;
        return selector.equals(other.selector);
    }

    @Override
    public int hashCode() {
        return selector.hashCode();
    }

    @Override
    public String toString() {
        return "NotSelector(" + selector + ")";
    }
}
