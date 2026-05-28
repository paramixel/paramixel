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

package nonapi.org.paramixel.classgraph.io.github.classgraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** A list of {@link MethodInfo} objects. */
public class MethodInfoList extends InfoList<MethodInfo> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /** An unmodifiable empty {@link MethodInfoList}. */
    static final MethodInfoList EMPTY_LIST = new MethodInfoList();

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * Return an unmodifiable empty {@link MethodInfoList}.
     *
     * @return the unmodifiable empty {@link MethodInfoList}.
     */
    public static MethodInfoList emptyList() {
        return EMPTY_LIST;
    }

    /** Construct a new modifiable empty list of {@link MethodInfo} objects. */
    public MethodInfoList() {
        super();
    }

    /**
     * Construct a new modifiable empty list of {@link MethodInfo} objects, given a size hint.
     *
     * @param sizeHint
     *            the size hint
     */
    public MethodInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Construct a new modifiable empty {@link MethodInfoList}, given an initial collection of {@link MethodInfo}
     * objects.
     *
     * @param methodInfoCollection
     *            the collection of {@link MethodInfo} objects.
     */
    public MethodInfoList(final Collection<MethodInfo> methodInfoCollection) {
        super(methodInfoCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get {@link ClassInfo} objects for any classes referenced in the type descriptor or type signature.
     *
     * @param classNameToClassInfo
     *            the map from class name to {@link ClassInfo}.
     * @param refdClassInfo
     *            the referenced class info
     * @param log
     *            the log
     */
    protected void findReferencedClassInfo(
            final Map<String, ClassInfo> classNameToClassInfo, final Set<ClassInfo> refdClassInfo, final LogNode log) {
        for (final MethodInfo mi : this) {
            mi.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get this {@link MethodInfoList} as a map from method name to a {@link MethodInfoList} of methods with that
     * name.
     *
     * @return This {@link MethodInfoList} as a map from method name to a {@link MethodInfoList} of methods with
     *         that name.
     */
    public Map<String, MethodInfoList> asMap() {
        // Note that MethodInfoList extends InfoList rather than MappableInfoList, because one
        // name can be shared by multiple MethodInfo objects (so asMap() needs to be of type
        // Map<String, MethodInfoList> rather than Map<String, MethodInfo>)
        final Map<String, MethodInfoList> methodNameToMethodInfoList = new HashMap<>();
        for (final MethodInfo methodInfo : this) {
            final String name = methodInfo.getName();
            MethodInfoList methodInfoList = methodNameToMethodInfoList.get(name);
            if (methodInfoList == null) {
                methodInfoList = new MethodInfoList(1);
                methodNameToMethodInfoList.put(name, methodInfoList);
            }
            methodInfoList.add(methodInfo);
        }
        return methodNameToMethodInfoList;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Check whether the list contains a method with the given name.
     *
     * @param methodName
     *            The name of a class.
     * @return true if the list contains a method with the given name.
     */
    public boolean containsName(final String methodName) {
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of all methods matching a given name. (There may be more than one method with a given name,
     * due to overloading, so this returns a {@link MethodInfoList} rather than a single {@link MethodInfo}.)
     *
     * @param methodName
     *            The name of a method.
     * @return A {@link MethodInfoList} of {@link MethodInfo} objects from this list that have the given name (there
     *         may be more than one method with a given name, due to overloading, so this returns a
     *         {@link MethodInfoList} rather than a single {@link MethodInfo}). Returns the empty list if no method
     *         had a matching name.
     */
    public MethodInfoList get(final String methodName) {
        boolean hasMethodWithName = false;
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                hasMethodWithName = true;
                break;
            }
        }
        if (!hasMethodWithName) {
            return EMPTY_LIST;
        } else {
            final MethodInfoList matchingMethods = new MethodInfoList(2);
            for (final MethodInfo mi : this) {
                if (mi.getName().equals(methodName)) {
                    matchingMethods.add(mi);
                }
            }
            return matchingMethods;
        }
    }

    /**
     * Returns a single method with the given name, or null if not found. Throws {@link IllegalArgumentException} if
     * there are two methods with the given name.
     *
     * @param methodName
     *            The name of a method.
     * @return The {@link MethodInfo} object from the list with the given name, if there is exactly one method with
     *         the given name. Returns null if there were no methods with the given name.
     * @throws IllegalArgumentException
     *             if there are two or more methods with the given name.
     */
    public MethodInfo getSingleMethod(final String methodName) {
        int numMethodsWithName = 0;
        MethodInfo lastFoundMethod = null;
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                numMethodsWithName++;
                lastFoundMethod = mi;
            }
        }
        if (numMethodsWithName == 0) {
            return null;
        } else if (numMethodsWithName == 1) {
            return lastFoundMethod;
        } else {
            throw new IllegalArgumentException("There are multiple methods named \"" + methodName + "\" in class "
                    + iterator().next().getClassInfo().getName());
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Filter an {@link MethodInfoList} using a predicate mapping an {@link MethodInfo} object to a boolean,
     * producing another {@link MethodInfoList} for all items in the list for which the predicate is true.
     */
    @FunctionalInterface
    public interface MethodInfoFilter {
        /**
         * Whether or not to allow an {@link MethodInfo} list item through the filter.
         *
         * @param methodInfo
         *            The {@link MethodInfo} item to filter.
         * @return Whether or not to allow the item through the filter. If true, the item is copied to the output
         *         list; if false, it is excluded.
         */
        boolean accept(MethodInfo methodInfo);
    }

    /**
     * Find the subset of the {@link MethodInfo} objects in this list for which the given filter predicate is true.
     *
     * @param filter
     *            The {@link MethodInfoFilter} to apply.
     * @return The subset of the {@link MethodInfo} objects in this list for which the given filter predicate is
     *         true.
     */
    public MethodInfoList filter(final MethodInfoFilter filter) {
        final MethodInfoList methodInfoFiltered = new MethodInfoList();
        for (final MethodInfo resource : this) {
            if (filter.accept(resource)) {
                methodInfoFiltered.add(resource);
            }
        }
        return methodInfoFiltered;
    }
}
