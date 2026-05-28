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
import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** A list of {@link FieldInfo} objects. */
public class FieldInfoList extends MappableInfoList<FieldInfo> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /** An unmodifiable empty {@link FieldInfoList}. */
    static final FieldInfoList EMPTY_LIST = new FieldInfoList();

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * Return an unmodifiable empty {@link FieldInfoList}.
     *
     * @return the unmodifiable empty {@link FieldInfoList}.
     */
    public static FieldInfoList emptyList() {
        return EMPTY_LIST;
    }

    /**
     * Construct a new modifiable empty list of {@link FieldInfo} objects.
     */
    public FieldInfoList() {
        super();
    }

    /**
     * Construct a new modifiable empty list of {@link FieldInfo} objects, given a size hint.
     *
     * @param sizeHint
     *            the size hint
     */
    public FieldInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Construct a new modifiable empty {@link FieldInfoList}, given an initial list of {@link FieldInfo} objects.
     *
     * @param fieldInfoCollection
     *            the collection of {@link FieldInfo} objects.
     */
    public FieldInfoList(final Collection<FieldInfo> fieldInfoCollection) {
        super(fieldInfoCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get {@link ClassInfo} objects for any classes referenced in the list.
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
        for (final FieldInfo fi : this) {
            fi.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Filter an {@link FieldInfoList} using a predicate mapping an {@link FieldInfo} object to a boolean, producing
     * another {@link FieldInfoList} for all items in the list for which the predicate is true.
     */
    @FunctionalInterface
    public interface FieldInfoFilter {
        /**
         * Whether or not to allow an {@link FieldInfo} list item through the filter.
         *
         * @param fieldInfo
         *            The {@link FieldInfo} item to filter.
         * @return Whether or not to allow the item through the filter. If true, the item is copied to the output
         *         list; if false, it is excluded.
         */
        boolean accept(FieldInfo fieldInfo);
    }

    /**
     * Find the subset of the {@link FieldInfo} objects in this list for which the given filter predicate is true.
     *
     * @param filter
     *            The {@link FieldInfoFilter} to apply.
     * @return The subset of the {@link FieldInfo} objects in this list for which the given filter predicate is
     *         true.
     */
    public FieldInfoList filter(final FieldInfoFilter filter) {
        final FieldInfoList fieldInfoFiltered = new FieldInfoList();
        for (final FieldInfo resource : this) {
            if (filter.accept(resource)) {
                fieldInfoFiltered.add(resource);
            }
        }
        return fieldInfoFiltered;
    }
}
