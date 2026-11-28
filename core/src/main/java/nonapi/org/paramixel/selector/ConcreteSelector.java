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

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.selector.Selector;

/**
 * The single implementation of {@link Selector}: an immutable predicate over a discovery candidate.
 *
 * <p>The builder compiles its criteria into one predicate per dimension. A candidate matches when the package
 * dimension accepts its package name, the class dimension accepts its class name, and the tag dimension accepts
 * its tags: a dimension with no includes accepts everything, any include may match and no exclude may match. A
 * tag include requires at least one matching tag, so an untagged class never matches a tag include; a tag exclude
 * rejects only when at least one tag matches.
 *
 * <p>{@link #configured()} is a placeholder that {@link #resolve(Configuration)} replaces with the selector
 * described by the {@code paramixel.match.*} keys.
 */
public final class ConcreteSelector implements Selector {

    /**
     * One discovery candidate: a fully qualified class name, its package, and the tags declared on the factory
     * method.
     *
     * @param packageName the package name; never {@code null}
     * @param className the fully qualified class name; never {@code null}
     * @param tags the declared tag values; never {@code null}
     */
    public record Candidate(String packageName, String className, List<String> tags) {

        /**
         * Creates a candidate.
         *
         * @param packageName the package name; must not be {@code null}
         * @param className the fully qualified class name; must not be {@code null}
         * @param tags the declared tag values; must not be {@code null}
         */
        public Candidate {
            Objects.requireNonNull(packageName, "packageName is null");
            Objects.requireNonNull(className, "className is null");
            Objects.requireNonNull(tags, "tags is null");
        }
    }

    private final Node root;

    private ConcreteSelector(final Node root) {
        this.root = Objects.requireNonNull(root, "root is null");
    }

    /**
     * Returns a selector from compiled criteria.
     *
     * @param packageIncludes package includes; must not be {@code null}
     * @param packageExcludes package excludes; must not be {@code null}
     * @param classIncludes class includes; must not be {@code null}
     * @param classExcludes class excludes; must not be {@code null}
     * @param tagIncludes tag includes; must not be {@code null}
     * @param tagExcludes tag excludes; must not be {@code null}
     * @return the selector; never {@code null}
     */
    public static ConcreteSelector criteria(
            final List<Predicate<String>> packageIncludes,
            final List<Predicate<String>> packageExcludes,
            final List<Predicate<String>> classIncludes,
            final List<Predicate<String>> classExcludes,
            final List<Predicate<String>> tagIncludes,
            final List<Predicate<String>> tagExcludes) {
        return new ConcreteSelector(
                new Criteria(packageIncludes, packageExcludes, classIncludes, classExcludes, tagIncludes, tagExcludes));
    }

    /**
     * Returns a selector that selects everything.
     *
     * @return the all-selecting selector; never {@code null}
     */
    public static ConcreteSelector all() {
        return new ConcreteSelector(new All());
    }

    /**
     * Returns a selector that selects only when both supplied selectors select.
     *
     * @param left the first selector; must not be {@code null}
     * @param right the second selector; must not be {@code null}
     * @return the combined selector; never {@code null}
     * @throws IllegalArgumentException if a selector is not framework-created
     */
    public static ConcreteSelector and(final Selector left, final Selector right) {
        return new ConcreteSelector(new And(require(left).root, require(right).root));
    }

    /**
     * Returns the deferred sentinel that a runner resolves from configuration.
     *
     * @return the configured placeholder selector; never {@code null}
     */
    public static ConcreteSelector configured() {
        return new ConcreteSelector(new Configured());
    }

    /**
     * Returns the selector described by the {@code paramixel.match.*} configuration keys.
     *
     * @param configuration the configuration to read; must not be {@code null}
     * @return the resolved selector; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     * @throws ConfigurationException if a key holds a blank or invalid pattern
     */
    public static ConcreteSelector fromConfiguration(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration is null");
        var packageIncludes = patternInclude(configuration, Configuration.MATCH_PACKAGE_REGEX);
        var classIncludes = patternInclude(configuration, Configuration.MATCH_CLASS_REGEX);
        var tagIncludes = patternInclude(configuration, Configuration.MATCH_TAG_REGEX);
        return new ConcreteSelector(
                new Criteria(packageIncludes, List.of(), classIncludes, List.of(), tagIncludes, List.of()));
    }

    /**
     * Returns the framework selector behind the supplied selector.
     *
     * @param selector the selector; must not be {@code null}
     * @return the concrete selector; never {@code null}
     * @throws IllegalArgumentException if {@code selector} was not created by the framework
     */
    public static ConcreteSelector require(final Selector selector) {
        Objects.requireNonNull(selector, "selector is null");
        if (selector instanceof ConcreteSelector concrete) {
            return concrete;
        }
        throw new IllegalArgumentException("selector must be created with Selector.builder(), Selector.configured() or"
                + " Selector.fromConfiguration(Configuration)");
    }

    /**
     * Returns whether this selector is the unresolved {@link #configured()} sentinel.
     *
     * @return {@code true} when this selector needs configuration to be resolved
     */
    public boolean isConfiguredPlaceholder() {
        return root instanceof Configured;
    }

    /**
     * Returns whether this selector contains the {@link #configured()} sentinel anywhere in its composition.
     *
     * @return {@code true} when a configuration is required to evaluate this selector
     */
    public boolean usesConfiguration() {
        return root.usesConfiguration();
    }

    /**
     * Resolves every {@link #configured()} sentinel with the supplied configuration.
     *
     * @param configuration the configuration to read; must not be {@code null}
     * @return a selector with no placeholder; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public ConcreteSelector resolve(final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration is null");
        return usesConfiguration() ? new ConcreteSelector(root.resolve(configuration)) : this;
    }

    /**
     * Tests a discovery candidate.
     *
     * @param packageName the package name; must not be {@code null}
     * @param className the fully qualified class name; must not be {@code null}
     * @param tags the declared tag values; must not be {@code null}
     * @return {@code true} when the candidate is selected
     * @throws IllegalStateException if the selector still contains a {@link #configured()} sentinel
     */
    public boolean test(final String packageName, final String className, final List<String> tags) {
        return root.test(new Candidate(packageName, className, tags));
    }

    /**
     * Tests whether the package and class dimensions accept a candidate, ignoring tags.
     *
     * <p>A selector without tag criteria decides the candidate completely; one with tag criteria needs
     * {@link #test(String, String, List)} because it must see the factory method's tags.
     *
     * @param packageName the package name; must not be {@code null}
     * @param className the fully qualified class name; must not be {@code null}
     * @return {@code false} only when the candidate cannot be selected
     */
    public boolean acceptsClass(final String packageName, final String className) {
        return root.acceptsClass(packageName, className);
    }

    private static List<Predicate<String>> patternInclude(final Configuration configuration, final String key) {
        var value = configuration.getString(key);
        if (value.isEmpty()) {
            return List.of();
        }
        var pattern = value.orElseThrow();
        if (pattern.isBlank()) {
            throw new ConfigurationException(
                    "Invalid value for '" + key + "': the pattern is blank; remove the key or supply a pattern");
        }
        try {
            var compiled = Pattern.compile(pattern.strip());
            return List.of(candidate -> compiled.matcher(candidate).matches());
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException(
                    "Invalid value for '" + key + "': '" + pattern + "' is not a valid regular expression: "
                            + e.getDescription(),
                    e);
        }
    }

    private interface Node {

        boolean test(Candidate candidate);

        boolean acceptsClass(String packageName, String className);

        boolean usesConfiguration();

        Node resolve(Configuration configuration);
    }

    private static final class All implements Node {

        @Override
        public boolean test(final Candidate candidate) {
            return true;
        }

        @Override
        public boolean acceptsClass(final String packageName, final String className) {
            return true;
        }

        @Override
        public boolean usesConfiguration() {
            return false;
        }

        @Override
        public Node resolve(final Configuration configuration) {
            return this;
        }
    }

    private static final class Configured implements Node {

        @Override
        public boolean test(final Candidate candidate) {
            throw new IllegalStateException("Selector.configured() must be resolved by a Runner before use");
        }

        @Override
        public boolean acceptsClass(final String packageName, final String className) {
            throw new IllegalStateException("Selector.configured() must be resolved by a Runner before use");
        }

        @Override
        public boolean usesConfiguration() {
            return true;
        }

        @Override
        public Node resolve(final Configuration configuration) {
            return new Criteria(
                    patternInclude(configuration, Configuration.MATCH_PACKAGE_REGEX),
                    List.of(),
                    patternInclude(configuration, Configuration.MATCH_CLASS_REGEX),
                    List.of(),
                    patternInclude(configuration, Configuration.MATCH_TAG_REGEX),
                    List.of());
        }
    }

    private static final class And implements Node {

        private final Node left;
        private final Node right;

        And(final Node left, final Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean test(final Candidate candidate) {
            return left.test(candidate) && right.test(candidate);
        }

        @Override
        public boolean acceptsClass(final String packageName, final String className) {
            return left.acceptsClass(packageName, className) && right.acceptsClass(packageName, className);
        }

        @Override
        public boolean usesConfiguration() {
            return left.usesConfiguration() || right.usesConfiguration();
        }

        @Override
        public Node resolve(final Configuration configuration) {
            return new And(left.resolve(configuration), right.resolve(configuration));
        }
    }

    private static final class Criteria implements Node {

        private final List<Predicate<String>> packageIncludes;
        private final List<Predicate<String>> packageExcludes;
        private final List<Predicate<String>> classIncludes;
        private final List<Predicate<String>> classExcludes;
        private final List<Predicate<String>> tagIncludes;
        private final List<Predicate<String>> tagExcludes;

        Criteria(
                final List<Predicate<String>> packageIncludes,
                final List<Predicate<String>> packageExcludes,
                final List<Predicate<String>> classIncludes,
                final List<Predicate<String>> classExcludes,
                final List<Predicate<String>> tagIncludes,
                final List<Predicate<String>> tagExcludes) {
            this.packageIncludes = List.copyOf(packageIncludes);
            this.packageExcludes = List.copyOf(packageExcludes);
            this.classIncludes = List.copyOf(classIncludes);
            this.classExcludes = List.copyOf(classExcludes);
            this.tagIncludes = List.copyOf(tagIncludes);
            this.tagExcludes = List.copyOf(tagExcludes);
        }

        @Override
        public boolean test(final Candidate candidate) {
            return acceptsClass(candidate.packageName(), candidate.className()) && acceptsTags(candidate.tags());
        }

        @Override
        public boolean acceptsClass(final String packageName, final String className) {
            return dimension(packageIncludes, packageExcludes, packageName)
                    && dimension(classIncludes, classExcludes, className);
        }

        private boolean acceptsTags(final List<String> tags) {
            if (!tagIncludes.isEmpty() && tags.stream().noneMatch(tag -> any(tagIncludes, tag))) {
                return false;
            }
            return tags.stream().noneMatch(tag -> any(tagExcludes, tag));
        }

        private static boolean dimension(
                final List<Predicate<String>> includes, final List<Predicate<String>> excludes, final String value) {
            if (!includes.isEmpty() && !any(includes, value)) {
                return false;
            }
            return !any(excludes, value);
        }

        private static boolean any(final List<Predicate<String>> predicates, final String value) {
            for (var predicate : predicates) {
                if (predicate.test(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean usesConfiguration() {
            return false;
        }

        @Override
        public Node resolve(final Configuration configuration) {
            return this;
        }
    }
}
