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

/** A list of {@link AnnotationParameterValue} objects. */
public class AnnotationParameterValueList extends MappableInfoList<AnnotationParameterValue> {
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /** An unmodifiable empty {@link AnnotationParameterValueList}. */
    static final AnnotationParameterValueList EMPTY_LIST = new AnnotationParameterValueList();

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * Return an unmodifiable empty {@link AnnotationParameterValueList}.
     *
     * @return the unmodifiable empty {@link AnnotationParameterValueList}.
     */
    public static AnnotationParameterValueList emptyList() {
        return EMPTY_LIST;
    }

    /**
     * Construct a new modifiable empty list of {@link AnnotationParameterValue} objects.
     */
    public AnnotationParameterValueList() {
        super();
    }

    /**
     * Construct a new modifiable empty list of {@link AnnotationParameterValue} objects, given a size hint.
     *
     * @param sizeHint
     *            the size hint
     */
    public AnnotationParameterValueList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * Construct a new modifiable empty {@link AnnotationParameterValueList}, given an initial list of
     * {@link AnnotationParameterValue} objects.
     *
     * @param annotationParameterValueCollection
     *            the collection of {@link AnnotationParameterValue} objects.
     */
    public AnnotationParameterValueList(final Collection<AnnotationParameterValue> annotationParameterValueCollection) {
        super(annotationParameterValueCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get {@link ClassInfo} objects for any classes referenced in the methods in this list.
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
        for (final AnnotationParameterValue apv : this) {
            apv.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * For primitive array type params, replace Object[] arrays containing boxed types with primitive arrays (need
     * to check the type of each method of the annotation class to determine if it is a primitive array type).
     *
     * @param annotationClassInfo
     *            the annotation class info
     */
    void convertWrapperArraysToPrimitiveArrays(final ClassInfo annotationClassInfo) {
        for (final AnnotationParameterValue apv : this) {
            apv.convertWrapperArraysToPrimitiveArrays(annotationClassInfo);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the annotation parameter value, by calling {@link AnnotationParameterValue#getValue()} on the result of
     * {@link #get(String)}, if non-null.
     *
     * @param parameterName
     *            The name of an annotation parameter.
     * @return The value of the {@link AnnotationParameterValue} object in the list with the given name, by calling
     *         {@link AnnotationParameterValue#getValue()} on that object, or null if not found.
     *
     *         <p>
     *         The annotation parameter value may be one of the following types:
     *         <ul>
     *         <li>String for string constants
     *         <li>String[] for arrays of strings
     *         <li>A boxed type, e.g. Integer or Character, for primitive-typed constants
     *         <li>A 1-dimensional primitive-typed array (i.e. int[], long[], short[], char[], byte[], boolean[],
     *         float[], or double[]), for arrays of primitives
     *         <li>A 1-dimensional {@link Object}[] array for array types (and then the array element type may be
     *         one of the types in this list)
     *         <li>{@link AnnotationEnumValue}, for enum constants (this wraps the enum class and the string name of
     *         the constant)
     *         <li>{@link AnnotationClassRef}, for Class references within annotations (this wraps the name of the
     *         referenced class)
     *         <li>{@link AnnotationInfo}, for nested annotations
     *         </ul>
     */
    public Object getValue(final String parameterName) {
        final AnnotationParameterValue apv = get(parameterName);
        return apv == null ? null : apv.getValue();
    }
}
