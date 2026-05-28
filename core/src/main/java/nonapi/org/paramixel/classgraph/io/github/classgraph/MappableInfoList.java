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

/**
 * A list of named objects that can be indexed by name.
 *
 * @param <T>
 *            the element type
 */
public class MappableInfoList<T extends HasName> extends InfoList<T> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    MappableInfoList() {
        super();
    }

    /**
     * Constructor.
     *
     * @param sizeHint
     *            the size hint
     */
    MappableInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Constructor.
     *
     * @param infoCollection
     *            the initial elements
     */
    MappableInfoList(final Collection<T> infoCollection) {
        super(infoCollection);
    }

    /**
     * Get an index for this list, as a map from the name of each list item (obtained by calling {@code getName()}
     * on each list item) to the list item.
     *
     * @return An index for this list, as a map from the name of each list item (obtained by calling
     *         {@code getName()} on each list item) to the list item.
     */
    public Map<String, T> asMap() {
        final Map<String, T> nameToInfoObject = new HashMap<>();
        for (final T i : this) {
            if (i != null) {
                nameToInfoObject.put(i.getName(), i);
            }
        }
        return nameToInfoObject;
    }

    /**
     * Check if this list contains an item with the given name.
     *
     * @param name
     *            The name to search for.
     * @return true if this list contains an item with the given name.
     */
    public boolean containsName(final String name) {
        for (final T i : this) {
            if (i != null && i.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list item with the given name, or null if not found.
     *
     * @param name
     *            The name to search for.
     * @return The list item with the given name, or null if not found.
     */
    @SuppressWarnings("null")
    public T get(final String name) {
        for (final T i : this) {
            if (i != null && i.getName().equals(name)) {
                return i;
            }
        }
        return null;
    }
}
