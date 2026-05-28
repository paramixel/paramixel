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

/** A list of {@link PackageInfo} objects. */
public class PackageInfoList extends MappableInfoList<PackageInfo> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    PackageInfoList() {
        super();
    }

    /**
     * Constructor.
     *
     * @param sizeHint
     *            the size hint
     */
    PackageInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Constructor.
     *
     * @param packageInfoCollection
     *            the package info collection
     */
    PackageInfoList(final Collection<PackageInfo> packageInfoCollection) {
        super(packageInfoCollection);
    }

    /** An unmodifiable {@link PackageInfoList}. */
    static final PackageInfoList EMPTY_LIST = new PackageInfoList() {
        /** serialVersionUID */
        private static final long serialVersionUID = 1L;

        @Override
        public boolean add(final PackageInfo e) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public void add(final int index, final PackageInfo element) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean remove(final Object o) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public PackageInfo remove(final int index) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean addAll(final Collection<? extends PackageInfo> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean addAll(final int index, final Collection<? extends PackageInfo> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean removeAll(final Collection<?> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean retainAll(final Collection<?> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public void clear() {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public PackageInfo set(final int index, final PackageInfo element) {
            throw new IllegalArgumentException("List is immutable");
        }
    };

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Filter an {@link PackageInfoList} using a predicate mapping an {@link PackageInfo} object to a boolean,
     * producing another {@link PackageInfoList} for all items in the list for which the predicate is true.
     */
    @FunctionalInterface
    public interface PackageInfoFilter {
        /**
         * Whether or not to allow an {@link PackageInfo} list item through the filter.
         *
         * @param packageInfo
         *            The {@link PackageInfo} item to filter.
         * @return Whether or not to allow the item through the filter. If true, the item is copied to the output
         *         list; if false, it is excluded.
         */
        boolean accept(PackageInfo packageInfo);
    }

    /**
     * Find the subset of the {@link PackageInfo} objects in this list for which the given filter predicate is true.
     *
     * @param filter
     *            The {@link PackageInfoFilter} to apply.
     * @return The subset of the {@link PackageInfo} objects in this list for which the given filter predicate is
     *         true.
     */
    public PackageInfoList filter(final PackageInfoFilter filter) {
        final PackageInfoList packageInfoFiltered = new PackageInfoList();
        for (final PackageInfo resource : this) {
            if (filter.accept(resource)) {
                packageInfoFiltered.add(resource);
            }
        }
        return packageInfoFiltered;
    }
}
