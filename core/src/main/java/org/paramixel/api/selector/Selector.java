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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.paramixel.api.Configuration;

/**
 * Describes which discovered {@code @Paramixel.Factory} methods a run selects.
 *
 * <p>A selector is built with {@link #builder()}, which offers one code-anchored and one name-based form per
 * dimension, each with an {@code exclude…} twin:
 *
 * <table>
 *   <caption>Selector builder methods</caption>
 *   <tr><th>Dimension</th><th>Code-anchored</th><th>Name-based</th></tr>
 *   <tr><td>Package and its subpackages</td><td>{@code packageTreeOf(Class)}</td><td>{@code packagePattern(Pattern)}</td></tr>
 *   <tr><td>Class</td><td>{@code classOf(Class)}</td><td>{@code classPattern(Pattern)}</td></tr>
 *   <tr><td>Tag</td><td>none; a tag is not a type</td><td>{@code tagPattern(Pattern)}</td></tr>
 * </table>
 *
 * <p>Dimensions are ANDed: a dimension with no includes is unconstrained, and within a dimension any include may
 * match while no exclude may match. An empty builder selects everything. Patterns use {@link
 * java.util.regex.Matcher#matches()} (full-match) semantics; an unescaped {@code .} is a wildcard, so use
 * {@link Pattern#quote(String)} for a literal and prefer {@code packageTreeOf(Class)} for exact code.
 *
 * <p>A tag include matches a class only when at least one of its tags matches, so an untagged class is never
 * selected by a tag include. A tag exclude rejects a class only when at least one of its tags matches, so
 * untagged classes are kept. "Everything not claimed by another lane" is therefore
 * {@code excludeTagPattern(Pattern.compile("smoke|slow"))}.
 *
 * <p>Selectors compose with {@link #and(Selector)}. {@link #configured()} is the selector described by the
 * {@code paramixel.match.package.regex}, {@code paramixel.match.class.regex} and {@code paramixel.match.tag.regex}
 * configuration keys; it is a deferred sentinel that a {@link org.paramixel.api.Runner} resolves with its own
 * configuration, so it is the one selector that is only meaningful inside a runner. Use {@link
 * #fromConfiguration(Configuration)} for the resolved form.
 *
 * <p>This interface is <strong>not for implementation by users</strong>; create selectors with {@link #builder()}.
 */
public interface Selector {

    /**
     * Returns a builder for a selector.
     *
     * @return a new, empty builder that selects everything until a criterion is added
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the selector described by the {@code paramixel.match.*} configuration keys.
     *
     * <p>This is a deferred sentinel: a runner replaces it with {@link #fromConfiguration(Configuration)}
     * before discovery, which lets a program call {@code run(Selector.configured())} without holding the
     * configuration. Every other selector is self-contained.
     *
     * @return the configured selector; never {@code null}
     */
    static Selector configured() {
        return ConcreteSelector.configured();
    }

    /**
     * Returns the selector described by the supplied configuration.
     *
     * @param configuration the configuration to read; must not be {@code null}
     * @return the resolved selector; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     * @throws org.paramixel.api.exception.ConfigurationException if a key holds a blank or invalid pattern
     */
    static Selector fromConfiguration(final Configuration configuration) {
        return ConcreteSelector.fromConfiguration(configuration);
    }

    /**
     * Returns a selector that selects a class only when this selector and the supplied selector both do.
     *
     * @param other the selector to combine with; must not be {@code null}
     * @return the combined selector; never {@code null}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default Selector and(final Selector other) {
        Objects.requireNonNull(other, "other is null");
        return ConcreteSelector.and(this, other);
    }

    /**
     * Fluent builder for {@link Selector} instances.
     *
     * <p>Builders are mutable and not thread-safe; {@link #build()} returns an immutable snapshot.
     */
    final class Builder {

        private final List<Predicate<String>> packageIncludes = new ArrayList<>();
        private final List<Predicate<String>> packageExcludes = new ArrayList<>();
        private final List<Predicate<String>> classIncludes = new ArrayList<>();
        private final List<Predicate<String>> classExcludes = new ArrayList<>();
        private final List<Predicate<String>> tagIncludes = new ArrayList<>();
        private final List<Predicate<String>> tagExcludes = new ArrayList<>();

        private Builder() {
            // Intentionally empty
        }

        /**
         * Adds an include for a package and all of its subpackages.
         *
         * @param type a class whose package is selected; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public Builder packageTreeOf(final Class<?> type) {
            packageIncludes.add(packageTree(Objects.requireNonNull(type, "type is null")));
            return this;
        }

        /**
         * Adds an exclude for a package and all of its subpackages.
         *
         * @param type a class whose package is excluded; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public Builder excludePackageTreeOf(final Class<?> type) {
            packageExcludes.add(packageTree(Objects.requireNonNull(type, "type is null")));
            return this;
        }

        /**
         * Adds an include for package names matching the supplied pattern.
         *
         * @param pattern the package-name pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder packagePattern(final Pattern pattern) {
            packageIncludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Adds an exclude for package names matching the supplied pattern.
         *
         * @param pattern the package-name pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder excludePackagePattern(final Pattern pattern) {
            packageExcludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Adds an include for one class.
         *
         * @param type the class to select; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public Builder classOf(final Class<?> type) {
            var name = Objects.requireNonNull(type, "type is null").getName();
            classIncludes.add(candidate -> candidate.equals(name));
            return this;
        }

        /**
         * Adds an exclude for one class.
         *
         * @param type the class to exclude; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public Builder excludeClassOf(final Class<?> type) {
            var name = Objects.requireNonNull(type, "type is null").getName();
            classExcludes.add(candidate -> candidate.equals(name));
            return this;
        }

        /**
         * Adds an include for fully qualified class names matching the supplied pattern.
         *
         * @param pattern the class-name pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder classPattern(final Pattern pattern) {
            classIncludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Adds an exclude for fully qualified class names matching the supplied pattern.
         *
         * @param pattern the class-name pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder excludeClassPattern(final Pattern pattern) {
            classExcludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Adds an include for tag values matching the supplied pattern.
         *
         * @param pattern the tag pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder tagPattern(final Pattern pattern) {
            tagIncludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Adds an exclude for tag values matching the supplied pattern.
         *
         * @param pattern the tag pattern, matched with {@code matches()} semantics; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code pattern} is {@code null}
         */
        public Builder excludeTagPattern(final Pattern pattern) {
            tagExcludes.add(patternMatcher(Objects.requireNonNull(pattern, "pattern is null")));
            return this;
        }

        /**
         * Builds an immutable selector from this builder's current criteria.
         *
         * @return the selector; never {@code null}
         */
        public Selector build() {
            return ConcreteSelector.criteria(
                    packageIncludes, packageExcludes, classIncludes, classExcludes, tagIncludes, tagExcludes);
        }

        private static Predicate<String> packageTree(final Class<?> type) {
            var prefix = type.getPackageName();
            if (prefix.isEmpty()) {
                return candidate -> true;
            }
            return candidate -> candidate.equals(prefix) || candidate.startsWith(prefix + ".");
        }

        private static Predicate<String> patternMatcher(final Pattern pattern) {
            return candidate -> pattern.matcher(candidate).matches();
        }
    }
}
