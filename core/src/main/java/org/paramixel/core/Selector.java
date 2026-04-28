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

/**
 * Describes a classpath selection as a regular expression.
 */
public class Selector {

    private final String regex;

    private Selector(String regex) {
        this.regex = regex;
    }

    /**
     * Returns the regular expression used for matching.
     *
     * @return The selector regular expression.
     */
    public String regex() {
        return this.regex;
    }

    /**
     * Creates a selector that matches a package and its subpackages.
     *
     * @param packageName The package name to match; must not be null.
     * @return A selector for {@code packageName}.
     */
    public static Selector byPackageName(String packageName) {
        return new Selector("^" + packageName.replace(".", "\\.") + "(\\..*)?$");
    }

    /**
     * Creates a selector that matches the package of a class and its
     * subpackages.
     *
     * @param clazz The class whose package should be matched; must not be null.
     * @return A selector for the class package.
     */
    public static Selector byPackageName(Class<?> clazz) {
        return new Selector("^" + clazz.getPackageName().replace(".", "\\.") + "(\\..*)?$");
    }

    /**
     * Creates a selector that matches one fully qualified class name.
     *
     * @param className The class name to match; must not be null.
     * @return A selector for {@code className}.
     */
    public static Selector byClassName(String className) {
        return new Selector("^" + className.replace(".", "\\.") + "$");
    }

    /**
     * Creates a selector that matches one class.
     *
     * @param clazz The class to match; must not be null.
     * @return A selector for {@code clazz}.
     */
    public static Selector byClassName(Class<?> clazz) {
        return new Selector("^" + clazz.getName().replace(".", "\\.") + "$");
    }
}
