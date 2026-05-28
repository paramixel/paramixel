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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A list of named objects.
 *
 * @param <T>
 *            the element type
 */
public class InfoList<T extends HasName> extends PotentiallyUnmodifiableList<T> {
    /** serialVersionUID. */
    static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    InfoList() {
        super();
    }

    /**
     * Constructor.
     *
     * @param sizeHint
     *            the size hint
     */
    InfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Constructor.
     *
     * @param infoCollection
     *            the initial elements.
     */
    InfoList(final Collection<T> infoCollection) {
        super(infoCollection);
    }

    // Keep Scrutinizer happy
    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    // Keep Scrutinizer happy
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the names of all items in this list, by calling {@code getName()} on each item in the list.
     *
     * @return The names of all items in this list, by calling {@code getName()} on each item in the list.
     */
    public List<String> getNames() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> names = new ArrayList<>(this.size());
            for (final T i : this) {
                if (i != null) {
                    names.add(i.getName());
                }
            }
            return names;
        }
    }

    /**
     * Get the String representations of all items in this list, by calling {@code toString()} on each item in the
     * list.
     *
     * @return The String representations of all items in this list, by calling {@code toString()} on each item in
     *         the list.
     */
    public List<String> getAsStrings() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> toStringVals = new ArrayList<>(this.size());
            for (final T i : this) {
                toStringVals.add(i == null ? "null" : i.toString());
            }
            return toStringVals;
        }
    }

    /**
     * Get the String representations of all items in this list, using only <a href=
     * "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">simple
     * names</a> of any named classes, by calling {@code ScanResultObject#toStringWithSimpleNames()} if the object
     * is a subclass of {@code ScanResultObject} (e.g. {@link ClassInfo}, {@link MethodInfo} or {@link FieldInfo}
     * object), otherwise calling {@code toString()}, for each item in the list.
     *
     * @return The String representations of all items in this list, using only the <a href=
     *         "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">
     *         simple names</a> of any named classes.
     */
    public List<String> getAsStringsWithSimpleNames() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> toStringVals = new ArrayList<>(this.size());
            for (final T i : this) {
                toStringVals.add(
                        i == null
                                ? "null"
                                : i instanceof ScanResultObject
                                        ? ((ScanResultObject) i).toStringWithSimpleNames()
                                        : i.toString());
            }
            return toStringVals;
        }
    }
}
