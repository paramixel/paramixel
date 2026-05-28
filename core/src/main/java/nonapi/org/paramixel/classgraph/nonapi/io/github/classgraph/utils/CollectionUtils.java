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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Collection utilities.
 */
public final class CollectionUtils {
    /** Class can't be constructed. */
    private CollectionUtils() {
        // Empty
    }

    /**
     * Sort a collection if it is not empty (to prevent {@link ConcurrentModificationException} if an immutable
     * empty list that has been returned more than once is being sorted in one thread and iterated through in
     * another thread -- #334).
     *
     * @param <T>
     *            the element type
     * @param list
     *            the list
     */
    public static <T extends Comparable<? super T>> void sortIfNotEmpty(final List<T> list) {
        if (list.size() > 1) {
            Collections.sort(list);
        }
    }

    /**
     * Sort a collection if it is not empty (to prevent {@link ConcurrentModificationException} if an immutable
     * empty list that has been returned more than once is being sorted in one thread and iterated through in
     * another thread -- #334).
     *
     * @param <T>
     *            the element type
     * @param list
     *            the list
     * @param comparator
     *            the comparator
     */
    public static <T> void sortIfNotEmpty(final List<T> list, final Comparator<? super T> comparator) {
        if (list.size() > 1) {
            Collections.sort(list, comparator);
        }
    }

    /**
     * Copy and sort a collection.
     *
     * @param elts
     *            the collection to copy and sort
     * @return a sorted copy of the collection
     */
    public static <T extends Comparable<T>> List<T> sortCopy(final Collection<T> elts) {
        final List<T> sortedCopy = new ArrayList<>(elts);
        if (sortedCopy.size() > 1) {
            Collections.sort(sortedCopy);
        }
        return sortedCopy;
    }
}
