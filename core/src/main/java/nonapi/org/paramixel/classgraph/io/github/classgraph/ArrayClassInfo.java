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

import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * Holds metadata about an array class. This class extends {@link ClassInfo} with additional methods relevant to
 * array classes, in particular {@link #getArrayTypeSignature()}, {@link #getTypeSignatureStr()},
 * {@link #getElementTypeSignature()}, {@link #getElementClassInfo()}, {@link #loadElementClass()}, and
 * {@link #getNumDimensions()}.
 *
 * <p>
 * An {@link ArrayClassInfo} object will not have any methods, fields or annotations.
 * {@link ClassInfo#isArrayClass()} will return true for this subclass of {@link ClassInfo}.
 */
public class ArrayClassInfo extends ClassInfo {
    /** The array type signature. */
    private ArrayTypeSignature arrayTypeSignature;

    /** The element class info. */
    private ClassInfo elementClassInfo;

    /** Default constructor for deserialization. */
    ArrayClassInfo() {
        super();
    }

    /**
     * Constructor.
     *
     * @param arrayTypeSignature
     *            the array type signature
     */
    ArrayClassInfo(final ArrayTypeSignature arrayTypeSignature) {
        super(arrayTypeSignature.getClassName(), /* modifiers = */ 0, /* resource = */ null);
        this.arrayTypeSignature = arrayTypeSignature;
        // Pre-load fields from element type
        getElementClassInfo();
    }

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo#setScanResult(nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the raw type signature string of the array class, e.g. "[[I" for "int[][]".
     *
     * @return The raw type signature string of the array class.
     */
    @Override
    public String getTypeSignatureStr() {
        return arrayTypeSignature.getTypeSignatureStr();
    }

    /**
     * Returns null, because array classes do not have a ClassTypeSignature. Call {@link #getArrayTypeSignature()}
     * instead.
     *
     * @return null (always).
     */
    @Override
    public ClassTypeSignature getTypeSignature() {
        return null;
    }

    /**
     * Get the type signature of the class.
     *
     * @return The class type signature, if available, otherwise returns null.
     */
    public ArrayTypeSignature getArrayTypeSignature() {
        return arrayTypeSignature;
    }

    /**
     * Get the type signature of the array elements.
     *
     * @return The type signature of the array elements.
     */
    public TypeSignature getElementTypeSignature() {
        return arrayTypeSignature.getElementTypeSignature();
    }

    /**
     * Get the number of dimensions of the array.
     *
     * @return The number of dimensions of the array.
     */
    public int getNumDimensions() {
        return arrayTypeSignature.getNumDimensions();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the {@link ClassInfo} instance for the array element type.
     *
     * @return the {@link ClassInfo} instance for the array element type. Returns null if the element type was not
     *         found during the scan. In particular, will return null for arrays that have a primitive element type.
     */
    public ClassInfo getElementClassInfo() {
        if (elementClassInfo == null) {
            final TypeSignature elementTypeSignature = arrayTypeSignature.getElementTypeSignature();
            if (!(elementTypeSignature instanceof BaseTypeSignature)) {
                elementClassInfo = arrayTypeSignature.getElementTypeSignature().getClassInfo();
                if (elementClassInfo != null) {
                    // Copy over relevant fields from array element ClassInfo
                    this.classpathElement = elementClassInfo.classpathElement;
                    this.classfileResource = elementClassInfo.classfileResource;
                    this.classLoader = elementClassInfo.classLoader;
                    this.isScannedClass = elementClassInfo.isScannedClass;
                    this.isExternalClass = elementClassInfo.isExternalClass;
                    this.moduleInfo = elementClassInfo.moduleInfo;
                    this.packageInfo = elementClassInfo.packageInfo;
                }
            }
        }
        return elementClassInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get a {@code Class<?>} reference for the array element type. Causes the ClassLoader to load the element
     * class, if it is not already loaded.
     *
     * @param ignoreExceptions
     *            Whether or not to ignore exceptions.
     * @return a {@code Class<?>} reference for the array element type. Also works for arrays of primitive element
     *         type.
     */
    public Class<?> loadElementClass(final boolean ignoreExceptions) {
        return arrayTypeSignature.loadElementClass(ignoreExceptions);
    }

    /**
     * Get a {@code Class<?>} reference for the array element type. Causes the ClassLoader to load the element
     * class, if it is not already loaded.
     *
     * @return a {@code Class<?>} reference for the array element type. Also works for arrays of primitive element
     *         type.
     */
    public Class<?> loadElementClass() {
        return arrayTypeSignature.loadElementClass();
    }

    /**
     * Obtain a {@code Class<?>} reference for the array class named by this {@link ArrayClassInfo} object. Causes
     * the ClassLoader to load the element class, if it is not already loaded.
     *
     * @param ignoreExceptions
     *            Whether or not to ignore exceptions
     * @return The class reference, or null, if ignoreExceptions is true and there was an exception or error loading
     *         the class.
     * @throws IllegalArgumentException
     *             if ignoreExceptions is false and there were problems loading the class.
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        if (classRef == null) {
            classRef = arrayTypeSignature.loadClass(ignoreExceptions);
        }
        return classRef;
    }

    /**
     * Obtain a {@code Class<?>} reference for the array class named by this {@link ArrayClassInfo} object. Causes
     * the ClassLoader to load the element class, if it is not already loaded.
     *
     * @return The class reference.
     * @throws IllegalArgumentException
     *             if there were problems loading the class.
     */
    @Override
    public Class<?> loadClass() {
        if (classRef == null) {
            classRef = arrayTypeSignature.loadClass();
        }
        return classRef;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get {@link ClassInfo} objects for any classes referenced in the type descriptor or type signature.
     *
     * @param classNameToClassInfo
     *            the map from class name to {@link ClassInfo}.
     * @param refdClassInfo
     *            the referenced class info
     */
    @Override
    protected void findReferencedClassInfo(
            final Map<String, ClassInfo> classNameToClassInfo, final Set<ClassInfo> refdClassInfo, final LogNode log) {
        super.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo#hashCode()
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
