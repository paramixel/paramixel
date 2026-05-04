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

package org.paramixel.core;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.paramixel.core.support.Arguments;

/**
 * Describes discovery criteria used by {@link Resolver} when locating {@code @Paramixel.ActionFactory} methods.
 *
 * <p>A selector may constrain discovery by package name, fully qualified class name, tag value, or a combination of
 * one location criterion plus one tag criterion. All selector regular expressions use {@link Pattern#matcher}
 * {@code .find()} semantics.
 *
 * <p>If an exact match is required, callers should provide an anchored regular expression such as {@code ^smoke$}.
 */
public final class Selector {

    private final LocationMode locationMode;
    private final Pattern locationPattern;
    private final Pattern tagPattern;

    private Selector(LocationMode locationMode, Pattern locationPattern, Pattern tagPattern) {
        this.locationMode = locationMode;
        this.locationPattern = locationPattern;
        this.tagPattern = tagPattern;
    }

    /**
     * Creates a new builder for composing selector criteria.
     *
     * @return a new selector builder
     */
    public static Builder builder() {
        return new Builder();
    }

    boolean matchesLocation(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        if (locationPattern == null) {
            return true;
        }
        String candidate =
                switch (locationMode) {
                    case PACKAGE -> clazz.getPackageName();
                    case CLASS -> clazz.getName();
                };
        return locationPattern.matcher(candidate).find();
    }

    Pattern getTagPattern() {
        return tagPattern;
    }

    private enum LocationMode {
        PACKAGE,
        CLASS
    }

    /**
     * Fluent builder for {@link Selector} instances.
     *
     * <p>Only one location criterion may be configured at a time. Package, class, and tag match expressions all use
     * regular-expression {@code find()} semantics.
     */
    public static final class Builder {

        private String packageRegex;
        private String classRegex;
        private String tagRegex;

        private Builder() {}

        /**
         * Sets the package-match expression used during discovery.
         *
         * <p>The supplied value is interpreted as a Java regular expression and matched with {@code find()} semantics
         * against candidate package names.
         *
         * @param regex the package-match regular expression
         * @return this builder
         * @throws NullPointerException if {@code regex} is {@code null}
         * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
         */
        public Builder packageMatch(String regex) {
            this.packageRegex = normalizeRegex(regex, "packageMatch");
            return this;
        }

        /**
         * Sets a package filter matching the supplied class package and all of its subpackages.
         *
         * <p>This is a convenience method equivalent to using an anchored package regular expression such as
         * {@code ^com\.example(\..*)?$}.
         *
         * @param clazz the class whose package should be matched
         * @return this builder
         * @throws NullPointerException if {@code clazz} is {@code null}
         */
        public Builder packageOf(Class<?> clazz) {
            Objects.requireNonNull(clazz, "clazz must not be null");
            this.packageRegex = "^" + Pattern.quote(clazz.getPackageName()) + "(\\..*)?$";
            return this;
        }

        /**
         * Sets the fully qualified class-name match expression used during discovery.
         *
         * <p>The supplied value is interpreted as a Java regular expression and matched with {@code find()} semantics
         * against candidate fully qualified class names.
         *
         * @param regex the class-match regular expression
         * @return this builder
         * @throws NullPointerException if {@code regex} is {@code null}
         * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
         */
        public Builder classMatch(String regex) {
            this.classRegex = normalizeRegex(regex, "classMatch");
            return this;
        }

        /**
         * Sets a class filter matching the supplied class by its exact fully qualified class name.
         *
         * <p>This is a convenience method equivalent to using an anchored class regular expression such as
         * {@code ^com\.example\.MyTest$}.
         *
         * @param clazz the class to match
         * @return this builder
         * @throws NullPointerException if {@code clazz} is {@code null}
         */
        public Builder classOf(Class<?> clazz) {
            Objects.requireNonNull(clazz, "clazz must not be null");
            this.classRegex = "^" + Pattern.quote(clazz.getName()) + "$";
            return this;
        }

        /**
         * Sets the tag-match expression used during discovery.
         *
         * <p>The supplied value is interpreted as a Java regular expression and matched with {@code find()} semantics
         * against each declared {@link Paramixel.Tag} value.
         *
         * @param regex the tag-match regular expression
         * @return this builder
         * @throws NullPointerException if {@code regex} is {@code null}
         * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
         */
        public Builder tagMatch(String regex) {
            this.tagRegex = normalizeRegex(regex, "tagMatch");
            return this;
        }

        /**
         * Builds an immutable selector from the configured criteria.
         *
         * @return a new selector
         * @throws IllegalStateException if both package and class match criteria are configured
         */
        public Selector build() {
            if (packageRegex != null && classRegex != null) {
                throw new IllegalStateException(
                        "Selector may define only one location match: packageMatch or classMatch");
            }
            LocationMode locationMode = null;
            Pattern locationPattern = null;
            if (packageRegex != null) {
                locationMode = LocationMode.PACKAGE;
                locationPattern = compilePattern(packageRegex, "packageMatch");
            } else if (classRegex != null) {
                locationMode = LocationMode.CLASS;
                locationPattern = compilePattern(classRegex, "classMatch");
            }
            Pattern tagPattern = tagRegex != null ? compilePattern(tagRegex, "tagMatch") : null;
            return new Selector(locationMode, locationPattern, tagPattern);
        }

        private static String normalizeRegex(String regex, String methodName) {
            Objects.requireNonNull(regex, methodName + " regex must not be null");
            Arguments.requireNonBlank(regex, methodName + " regex must not be blank");
            compilePattern(regex, methodName);
            return regex;
        }

        private static Pattern compilePattern(String regex, String methodName) {
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex for " + methodName + ": '" + regex + "'", e);
            }
        }
    }
}
