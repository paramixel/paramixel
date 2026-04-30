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

package org.paramixel.core.discovery;

/**
 * Describes a classpath selection as a regular expression.
 */
public final class Selector {

    private final String regex;

    private Selector(String regex) {
        this.regex = regex;
    }

    /**
     * Returns the regular expression represented by this selector.
     *
     * @return the selector regular expression
     */
    public String getRegex() {
        return regex;
    }

    /**
     * Creates a selector that matches a package and its subpackages.
     *
     * @param packageName the package name to match
     * @return a selector for the package
     */
    public static Selector byPackageName(String packageName) {
        return new Selector("^" + packageName.replace(".", "\\.") + "(\\..*)?$");
    }

    /**
     * Creates a selector that matches the package of the supplied class and its subpackages.
     *
     * @param clazz the class whose package should be matched
     * @return a selector for the class package
     */
    public static Selector byPackageName(Class<?> clazz) {
        return new Selector("^" + clazz.getPackageName().replace(".", "\\.") + "(\\..*)?$");
    }

    /**
     * Creates a selector that matches a fully qualified class name.
     *
     * @param className the fully qualified class name to match
     * @return a selector for the class name
     */
    public static Selector byClassName(String className) {
        return new Selector("^" + className.replace(".", "\\.") + "$");
    }

    /**
     * Creates a selector that matches the fully qualified name of the supplied class.
     *
     * @param clazz the class whose fully qualified name should be matched
     * @return a selector for the class name
     */
    public static Selector byClassName(Class<?> clazz) {
        return new Selector("^" + clazz.getName().replace(".", "\\.") + "$");
    }
}
