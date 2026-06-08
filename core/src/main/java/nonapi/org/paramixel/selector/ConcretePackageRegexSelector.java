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
import org.paramixel.api.selector.PackageRegexSelector;

/**
 * Concrete implementation of {@link PackageRegexSelector}.
 *
 * <p>Matches package names using {@link Pattern#matcher(CharSequence)} {@code .find()} semantics.
 * Class name and tag matching always return {@code true}.
 */
public final class ConcretePackageRegexSelector implements PackageRegexSelector {

    private final Pattern pattern;

    /**
     * Creates a package regex selector with the supplied compiled pattern.
     *
     * @param pattern the compiled pattern; must not be {@code null}
     */
    public ConcretePackageRegexSelector(final Pattern pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern is null");
    }

    @Override
    public Pattern pattern() {
        return pattern;
    }

    @Override
    public boolean matchesPackage(final String packageName) {
        Objects.requireNonNull(packageName, "packageName is null");
        return pattern.matcher(packageName).find();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConcretePackageRegexSelector other)) {
            return false;
        }
        return pattern.pattern().equals(other.pattern.pattern()) && pattern.flags() == other.pattern.flags();
    }

    @Override
    public int hashCode() {
        return 31 * pattern.pattern().hashCode() + pattern.flags();
    }

    @Override
    public String toString() {
        return "PackageRegexSelector{pattern='" + pattern.pattern() + "'}";
    }
}
