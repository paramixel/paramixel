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

/**
 * A {@link RegexSelector} that filters discovery candidates by package name.
 *
 * <p>{@link #matchesPackage(String)} applies the regex pattern using {@code find()} semantics.
 * {@link #matchesClass(String)} and {@link #matchesTag(String)} return {@code true} because
 * a package-only selector does not constrain class names or tags.
 *
 * @see Selector#packageRegex(String)
 * @see Selector#packageTreeOf(Class)
 * @see Selector#packageOf(Class)
 */
public interface PackageRegexSelector extends RegexSelector {

    @Override
    boolean matchesPackage(String packageName);

    @Override
    default boolean matchesClass(String className) {
        return true;
    }

    @Override
    default boolean matchesTag(String tag) {
        return true;
    }
}
