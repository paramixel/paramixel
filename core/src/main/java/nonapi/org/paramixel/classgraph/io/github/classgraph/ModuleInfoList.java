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

/** A list of {@link ModuleInfo} objects. */
public class ModuleInfoList extends MappableInfoList<ModuleInfo> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    ModuleInfoList() {
        super();
    }

    /**
     * Constructor.
     *
     * @param sizeHint
     *            the size hint
     */
    ModuleInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Constructor.
     *
     * @param moduleInfoCollection
     *            the module info collection
     */
    ModuleInfoList(final Collection<ModuleInfo> moduleInfoCollection) {
        super(moduleInfoCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Filter an {@link ModuleInfoList} using a predicate mapping an {@link ModuleInfo} object to a boolean,
     * producing another {@link ModuleInfoList} for all items in the list for which the predicate is true.
     */
    @FunctionalInterface
    public interface ModuleInfoFilter {
        /**
         * Whether or not to allow an {@link ModuleInfo} list item through the filter.
         *
         * @param moduleInfo
         *            The {@link ModuleInfo} item to filter.
         * @return Whether or not to allow the item through the filter. If true, the item is copied to the output
         *         list; if false, it is excluded.
         */
        boolean accept(ModuleInfo moduleInfo);
    }

    /**
     * Find the subset of the {@link ModuleInfo} objects in this list for which the given filter predicate is true.
     *
     * @param filter
     *            The {@link ModuleInfoFilter} to apply.
     * @return The subset of the {@link ModuleInfo} objects in this list for which the given filter predicate is
     *         true.
     */
    public ModuleInfoList filter(final ModuleInfoFilter filter) {
        final ModuleInfoList moduleInfoFiltered = new ModuleInfoList();
        for (final ModuleInfo resource : this) {
            if (filter.accept(resource)) {
                moduleInfoFiltered.add(resource);
            }
        }
        return moduleInfoFiltered;
    }
}
