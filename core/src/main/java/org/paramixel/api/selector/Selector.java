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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import nonapi.org.paramixel.ConcreteAllSelector;
import nonapi.org.paramixel.ConcreteAndSelector;
import nonapi.org.paramixel.ConcreteClassRegexSelector;
import nonapi.org.paramixel.ConcreteNotSelector;
import nonapi.org.paramixel.ConcreteOrSelector;
import nonapi.org.paramixel.ConcretePackageRegexSelector;
import nonapi.org.paramixel.ConcreteTagRegexSelector;
import nonapi.org.paramixel.support.Arguments;

/**
 * Describes discovery criteria used by {@link org.paramixel.api.Runner} when locating {@code @Paramixel.Factory} methods.
 *
 * <p>Selectors may constrain discovery by package name, fully qualified class name, tag value, or a
 * composition of these using {@link #and(Selector...)}, {@link #or(Selector...)}, or {@link #not(Selector)}.
 * All selector regular expressions use {@link Pattern#matcher(CharSequence)} {@code .find()} semantics.
 *
 * <p>If an exact match is required, callers should provide an anchored regular expression such as {@code ^smoke$}.
 *
 * <p>Use the static factory methods to create selectors:
 * <ul>
 *   <li>{@link #all()} — matches everything</li>
 *   <li>{@link #packageRegex(String)} — matches package names by regex</li>
 *   <li>{@link #classRegex(String)} — matches class names by regex</li>
 *   <li>{@link #tagRegex(String)} — matches tag values by regex</li>
 *   <li>{@link #packageTreeOf(Class)} — matches a package and all subpackages</li>
 *   <li>{@link #packageOf(Class)} — matches an exact package</li>
 *   <li>{@link #classOf(Class)} — matches an exact class</li>
 *   <li>{@link #and(Selector...)} / {@link #and(List)} — logical AND composition</li>
 *   <li>{@link #or(Selector...)} / {@link #or(List)} — logical OR composition</li>
 *   <li>{@link #not(Selector)} — logical NOT negation</li>
 * </ul>
 *
 * @see RegexSelector
 * @see PackageRegexSelector
 * @see ClassRegexSelector
 * @see TagRegexSelector
 * @see AndSelector
 * @see OrSelector
 * @see NotSelector
 */
public interface Selector {

    /**
     * Tests whether the supplied package name matches this selector's package criterion.
     *
     * @param packageName the package name to test; must not be {@code null}
     * @return {@code true} if the package name matches
     * @throws NullPointerException if {@code packageName} is {@code null}
     */
    boolean matchesPackage(String packageName);

    /**
     * Tests whether the supplied fully qualified class name matches this selector's class criterion.
     *
     * @param className the fully qualified class name to test; must not be {@code null}
     * @return {@code true} if the class name matches
     * @throws NullPointerException if {@code className} is {@code null}
     */
    boolean matchesClass(String className);

    /**
     * Tests whether the supplied tag value matches this selector's tag criterion.
     *
     * @param tag the tag value to test; must not be {@code null}
     * @return {@code true} if the tag matches
     * @throws NullPointerException if {@code tag} is {@code null}
     */
    boolean matchesTag(String tag);

    /**
     * Returns a selector that matches all action factories regardless of package, class name, or tag.
     *
     * <p>The returned selector is a singleton; all three {@code matches*} methods return {@code true}.
     *
     * @return the all-matching selector; never {@code null}
     */
    static Selector all() {
        return ConcreteAllSelector.INSTANCE;
    }

    /**
     * Creates a selector that matches package names using the supplied regular expression.
     *
     * <p>The expression is matched with {@code find()} semantics against candidate package names.
     *
     * @param regex the package-match regular expression; must not be {@code null} or blank
     * @return a new package regex selector
     * @throws NullPointerException if {@code regex} is {@code null}
     * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
     */
    static Selector packageRegex(final String regex) {
        return new ConcretePackageRegexSelector(compileAndValidate(regex, "packageRegex"));
    }

    /**
     * Creates a selector that matches fully qualified class names using the supplied regular expression.
     *
     * <p>The expression is matched with {@code find()} semantics against candidate class names.
     *
     * @param regex the class-match regular expression; must not be {@code null} or blank
     * @return a new class regex selector
     * @throws NullPointerException if {@code regex} is {@code null}
     * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
     */
    static Selector classRegex(final String regex) {
        return new ConcreteClassRegexSelector(compileAndValidate(regex, "classRegex"));
    }

    /**
     * Creates a selector that matches tag values using the supplied regular expression.
     *
     * <p>The expression is matched with {@code find()} semantics against each declared
     * {@link org.paramixel.api.Paramixel.Tag} value.
     *
     * @param regex the tag-match regular expression; must not be {@code null} or blank
     * @return a new tag regex selector
     * @throws NullPointerException if {@code regex} is {@code null}
     * @throws IllegalArgumentException if {@code regex} is blank or not a valid regular expression
     */
    static Selector tagRegex(final String regex) {
        return new ConcreteTagRegexSelector(compileAndValidate(regex, "tagRegex"));
    }

    /**
     * Creates a selector that matches the supplied class's package and all
     * subpackages below it.
     *
     * <p>This is a convenience factory equivalent to
     * {@code Selector.packageRegex("^" + Pattern.quote(clazz.getPackageName()) + "(\\..*)?$")}.
     *
     * @param clazz the class whose package should be matched; must not be {@code null}
     * @return a new selector matching the class's package and all subpackages below it
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    static Selector packageTreeOf(final Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        var regex = "^" + Pattern.quote(clazz.getPackageName()) + "(\\..*)?$";
        return new ConcretePackageRegexSelector(Pattern.compile(regex));
    }

    /**
     * Creates a selector that matches only classes in the supplied class's
     * exact package, excluding subpackages.
     *
     * <p>This is a convenience factory equivalent to
     * {@code Selector.packageRegex("^" + Pattern.quote(clazz.getPackageName()) + "$")}.
     *
     * @param clazz the class whose package should be matched exactly; must not be {@code null}
     * @return a new selector matching the class's exact package
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    static Selector packageOf(final Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        var regex = "^" + Pattern.quote(clazz.getPackageName()) + "$";
        return new ConcretePackageRegexSelector(Pattern.compile(regex));
    }

    /**
     * Creates a selector that matches the exact fully qualified class name of the
     * supplied class.
     *
     * <p>This is a convenience factory equivalent to
     * {@code Selector.classRegex("^" + Pattern.quote(clazz.getName()) + "$")}.
     *
     * @param clazz the class to match; must not be {@code null}
     * @return a new selector matching the class's fully qualified name
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    static Selector classOf(final Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz is null");
        var regex = "^" + Pattern.quote(clazz.getName()) + "$";
        return new ConcreteClassRegexSelector(Pattern.compile(regex));
    }

    /**
     * Creates a selector that matches when all of the supplied selectors match (logical AND).
     *
     * <p>At least two selectors must be provided. Nested AND selectors are flattened:
     * {@code and(and(a, b), c)} produces the same matches as {@code and(a, b, c)}.
     * Null elements are rejected.
     *
     * @param selectors the selectors to compose; must not be {@code null}, must contain at least
     *     two elements, and must not contain {@code null} elements
     * @return a new AND selector
     * @throws NullPointerException if {@code selectors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if fewer than two selectors are provided
     */
    static Selector and(final Selector... selectors) {
        Objects.requireNonNull(selectors, "selectors is null");
        Arguments.requireNoNullElements(selectors, "Selector.and() selectors contains null element");
        return andFromList(List.of(selectors), "Selector.and()");
    }

    /**
     * Creates a selector that matches when all of the supplied selectors match (logical AND).
     *
     * <p>At least two selectors must be provided. Nested AND selectors are flattened.
     * Null elements are rejected.
     *
     * @param selectors the selectors to compose; must not be {@code null}, must contain at least
     *     two elements, and must not contain {@code null} elements
     * @return a new AND selector
     * @throws NullPointerException if {@code selectors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if fewer than two selectors are provided
     */
    static Selector and(final List<Selector> selectors) {
        Objects.requireNonNull(selectors, "selectors is null");
        Arguments.requireNoNullElements(selectors, "Selector.and() selectors contains null element");
        return andFromList(selectors, "Selector.and()");
    }

    /**
     * Creates a selector that matches when any of the supplied selectors match (logical OR).
     *
     * <p>At least two selectors must be provided. Nested OR selectors are flattened:
     * {@code or(or(a, b), c)} produces the same matches as {@code or(a, b, c)}.
     * Null elements are rejected.
     *
     * @param selectors the selectors to compose; must not be {@code null}, must contain at least
     *     two elements, and must not contain {@code null} elements
     * @return a new OR selector
     * @throws NullPointerException if {@code selectors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if fewer than two selectors are provided
     */
    static Selector or(final Selector... selectors) {
        Objects.requireNonNull(selectors, "selectors is null");
        Arguments.requireNoNullElements(selectors, "Selector.or() selectors contains null element");
        return orFromList(List.of(selectors), "Selector.or()");
    }

    /**
     * Creates a selector that matches when any of the supplied selectors match (logical OR).
     *
     * <p>At least two selectors must be provided. Nested OR selectors are flattened.
     * Null elements are rejected.
     *
     * @param selectors the selectors to compose; must not be {@code null}, must contain at least
     *     two elements, and must not contain {@code null} elements
     * @return a new OR selector
     * @throws NullPointerException if {@code selectors} is {@code null} or contains {@code null} elements
     * @throws IllegalArgumentException if fewer than two selectors are provided
     */
    static Selector or(final List<Selector> selectors) {
        Objects.requireNonNull(selectors, "selectors is null");
        Arguments.requireNoNullElements(selectors, "Selector.or() selectors contains null element");
        return orFromList(selectors, "Selector.or()");
    }

    /**
     * Creates a selector that matches when the supplied selector does not match (logical NOT).
     *
     * <p>All three {@code matches*} methods are strictly negated. No double-negation
     * simplification or De Morgan transformation is applied.
     *
     * @param selector the selector to negate; must not be {@code null}
     * @return a new NOT selector
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    static Selector not(final Selector selector) {
        Objects.requireNonNull(selector, "Selector.not() selector is null");
        return new ConcreteNotSelector(selector);
    }

    private static Selector andFromList(final List<Selector> selectors, final String methodName) {
        Arguments.requireTrue(
                selectors.size() >= 2,
                () -> methodName + " requires at least 2 selectors but only " + selectors.size() + " were provided");
        var flattened = new ArrayList<Selector>();
        for (Selector selector : selectors) {
            if (selector instanceof AndSelector andSelector) {
                flattened.addAll(andSelector.selectors());
            } else {
                flattened.add(selector);
            }
        }
        return new ConcreteAndSelector(List.copyOf(flattened));
    }

    private static Selector orFromList(final List<Selector> selectors, final String methodName) {
        Arguments.requireTrue(
                selectors.size() >= 2,
                () -> methodName + " requires at least 2 selectors but only " + selectors.size() + " were provided");
        var flattened = new ArrayList<Selector>();
        for (Selector selector : selectors) {
            if (selector instanceof OrSelector orSelector) {
                flattened.addAll(orSelector.selectors());
            } else {
                flattened.add(selector);
            }
        }
        return new ConcreteOrSelector(List.copyOf(flattened));
    }

    private static Pattern compileAndValidate(final String regex, final String methodName) {
        Objects.requireNonNull(regex, methodName + " regex is null");
        Arguments.requireNonBlank(regex, methodName + " regex is blank");
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex for " + methodName + ": '" + regex + "'", e);
        }
    }
}
