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
import java.util.regex.Pattern;
import org.paramixel.api.selector.ClassRegexSelector;

/**
 * Concrete implementation of {@link ClassRegexSelector}.
 *
 * <p>Matches fully qualified class names using {@link Pattern#matcher(CharSequence)} {@code .find()} semantics.
 * Package name and tag matching always return {@code true}.
 */
public final class ConcreteClassRegexSelector implements ClassRegexSelector {

    private final Pattern pattern;

    /**
     * Creates a class regex selector with the supplied compiled pattern.
     *
     * @param pattern the compiled pattern; must not be {@code null}
     */
    public ConcreteClassRegexSelector(final Pattern pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern is null");
    }

    @Override
    public Pattern pattern() {
        return pattern;
    }

    @Override
    public boolean matchesClass(final String className) {
        Objects.requireNonNull(className, "className is null");
        return pattern.matcher(className).find();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ConcreteClassRegexSelector other)) return false;
        return pattern.pattern().equals(other.pattern.pattern()) && pattern.flags() == other.pattern.flags();
    }

    @Override
    public int hashCode() {
        return 31 * pattern.pattern().hashCode() + pattern.flags();
    }

    @Override
    public String toString() {
        return "ClassRegexSelector{pattern='" + pattern.pattern() + "'}";
    }
}
