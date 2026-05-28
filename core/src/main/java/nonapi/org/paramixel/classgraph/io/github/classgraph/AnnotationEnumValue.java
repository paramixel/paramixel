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

import java.lang.reflect.Field;

/**
 * Class for wrapping an enum constant value (split into class name and constant name), as used as an annotation
 * parameter value.
 */
public class AnnotationEnumValue extends ScanResultObject implements Comparable<AnnotationEnumValue> {
    /** The class name. */
    private String className;

    /** The value name. */
    private String valueName;

    /** Default constructor for deserialization. */
    AnnotationEnumValue() {
        super();
    }

    /**
     * Constructor.
     *
     * @param className
     *            The enum class name.
     * @param constValueName
     *            The enum const value name.
     */
    AnnotationEnumValue(final String className, final String constValueName) {
        super();
        this.className = className;
        this.valueName = constValueName;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the class name.
     *
     * @return The name of the enum class.
     */
    @Override
    public String getClassName() {
        return className;
    }

    /**
     * Get the value name.
     *
     * @return The name of the enum const value.
     */
    public String getValueName() {
        return valueName;
    }

    /**
     * Get the name.
     *
     * @return The fully-qualified name of the enum constant value, i.e. ({@link #getClassName()} +
     *         {#getValueName()}).
     */
    public String getName() {
        return className + "." + valueName;
    }

    /**
     * Loads the enum class, instantiates the enum constants for the class, and returns the enum constant value
     * represented by this {@link AnnotationEnumValue}.
     *
     * @param ignoreExceptions
     *            If true, ignore classloading exceptions and return null on failure.
     * @return The enum constant value represented by this {@link AnnotationEnumValue}
     * @throws IllegalArgumentException
     *             if the class could not be loaded and ignoreExceptions was false, or if the enum constant is
     *             invalid.
     */
    public Object loadClassAndReturnEnumValue(final boolean ignoreExceptions) throws IllegalArgumentException {
        final Class<?> classRef = super.loadClass(ignoreExceptions);
        if (classRef == null) {
            if (ignoreExceptions) {
                return null;
            } else {
                throw new IllegalArgumentException("Enum class " + className + " could not be loaded");
            }
        }
        if (!classRef.isEnum()) {
            throw new IllegalArgumentException("Class " + className + " is not an enum");
        }
        Field field;
        try {
            field = classRef.getDeclaredField(valueName);
        } catch (final ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException("Could not find enum constant " + this, e);
        }
        if (!field.isEnumConstant()) {
            throw new IllegalArgumentException("Field " + this + " is not an enum constant");
        }
        try {
            return field.get(null);
        } catch (final ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException("Field " + this + " is not accessible", e);
        }
    }

    /**
     * Loads the enum class, instantiates the enum constants for the class, and returns the enum constant value
     * represented by this {@link AnnotationEnumValue}.
     *
     * @return The enum constant value represented by this {@link AnnotationEnumValue}
     * @throws IllegalArgumentException
     *             if the class could not be loaded, or the enum constant is invalid.
     */
    public Object loadClassAndReturnEnumValue() throws IllegalArgumentException {
        return loadClassAndReturnEnumValue(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final AnnotationEnumValue o) {
        final int diff = className.compareTo(o.className);
        return diff == 0 ? valueName.compareTo(o.valueName) : diff;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AnnotationEnumValue)) {
            return false;
        }
        return compareTo((AnnotationEnumValue) obj) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return className.hashCode() * 11 + valueName.hashCode();
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        buf.append(useSimpleNames ? ClassInfo.getSimpleName(className) : className);
        buf.append('.');
        buf.append(valueName);
    }
}
