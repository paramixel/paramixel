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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * A superclass of objects accessible from a {@link ScanResult} that are associated with a {@link ClassInfo} object.
 */
abstract class ScanResultObject {
    /** The scan result. */
    protected transient ScanResult scanResult;

    /** The associated {@link ClassInfo} object. */
    private transient ClassInfo classInfo;

    /** The class ref, once the class is loaded. */
    protected transient Class<?> classRef;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Set ScanResult backreferences in info objects after scan has completed.
     *
     * @param scanResult
     *            the scan result
     */
    void setScanResult(final ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    /**
     * Get {@link ClassInfo} objects for any classes referenced by this object.
     *
     * @param log
     *            the log
     * @return the referenced class info.
     */
    final Set<ClassInfo> findReferencedClassInfo(final LogNode log) {
        final Set<ClassInfo> refdClassInfo = new LinkedHashSet<>();
        if (scanResult != null) {
            findReferencedClassInfo(scanResult.classNameToClassInfo, refdClassInfo, log);
        }
        return refdClassInfo;
    }

    /**
     * Get {@link ClassInfo} objects for any classes referenced by this object.
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
        final ClassInfo ci = getClassInfo();
        if (ci != null) {
            refdClassInfo.add(ci);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * The name of the class (used by {@link #getClassInfo()} to fetch the {@link ClassInfo} object for the class).
     *
     * @return The class name.
     */
    protected abstract String getClassName();

    /**
     * Get the {@link ClassInfo} object for the referenced class, or null if the referenced class was not
     * encountered during scanning (i.e. no ClassInfo object was created for the class during scanning). N.B. even
     * if this method returns null, {@link #loadClass()} may be able to load the referenced class by name.
     *
     * @return The {@link ClassInfo} object for the referenced class.
     */
    ClassInfo getClassInfo() {
        if (classInfo == null) {
            if (scanResult == null) {
                return null;
            }
            final String className = getClassName();
            if (className == null) {
                throw new IllegalArgumentException("Class name is not set");
            }
            classInfo = scanResult.getClassInfo(className);
        }
        return classInfo;
    }

    /**
     * Get the class name by calling getClassInfo().getName(), or as a fallback, by calling getClassName().
     *
     * @return the class name
     */
    private String getClassInfoNameOrClassName() {
        String className;
        ClassInfo ci = null;
        try {
            ci = getClassInfo();
        } catch (final IllegalArgumentException e) {
            // Just ignore wrong access to array classInfo
        }
        if (ci == null) {
            ci = classInfo;
        }
        if (ci != null) {
            // Get class name from getClassInfo().getName()
            className = ci.getName();
        } else {
            // Get class name from getClassName()
            className = getClassName();
        }
        if (className == null) {
            throw new IllegalArgumentException("Class name is not set");
        }
        return className;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Load the class named returned by {@link #getClassInfo()}, or if that returns null, the class named by
     * {@link #getClassName()}. Returns a {@code Class<?>} reference for the class, cast to the requested superclass
     * or interface type.
     *
     * @param <T>
     *            the superclass or interface type
     * @param superclassOrInterfaceType
     *            The type to cast the resulting class reference to.
     * @param ignoreExceptions
     *            If true, ignore classloading exceptions and return null on failure.
     * @return The {@code Class<?>} reference for the referenced class, or null if the class could not be loaded (or
     *         casting failed) and ignoreExceptions is true.
     * @throws IllegalArgumentException
     *             if the class could not be loaded or cast, and ignoreExceptions was false.
     */
    <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType, final boolean ignoreExceptions) {
        synchronized (this) {
            // If class is not already loaded, try loading class
            if (classRef == null) {
                final String className = getClassInfoNameOrClassName();
                try {
                    classRef = scanResult != null
                            ? scanResult.loadClass(className, superclassOrInterfaceType, ignoreExceptions)
                            // Fallback, if scanResult is not set
                            : Class.forName(className);
                    if (classRef == null && !ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className);
                    }
                } catch (final Throwable t) {
                    if (!ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className, t);
                    }
                }
            }
            @SuppressWarnings("unchecked")
            final Class<T> classT = (Class<T>) classRef;
            return classT;
        }
    }

    /**
     * Load the class named returned by {@link #getClassInfo()}, or if that returns null, the class named by
     * {@link #getClassName()}. Returns a {@code Class<?>} reference for the class, cast to the requested superclass
     * or interface type.
     *
     * @param <T>
     *            the superclass or interface type
     * @param superclassOrInterfaceType
     *            The type to cast the resulting class reference to.
     * @return The {@code Class<?>} reference for the referenced class, or null if the class could not be loaded (or
     *         casting failed) and ignoreExceptions is true.
     * @throws IllegalArgumentException
     *             if the class could not be loaded or cast, and ignoreExceptions was false.
     */
    <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType) {
        return loadClass(superclassOrInterfaceType, /* ignoreExceptions = */ false);
    }

    /**
     * Load the class named returned by {@link #getClassInfo()}, or if that returns null, the class named by
     * {@link #getClassName()}. Returns a {@code Class<?>} reference for the class.
     *
     * @param ignoreExceptions
     *            If true, ignore classloading exceptions and return null on failure.
     * @return The {@code Class<?>} reference for the referenced class, or null if the class could not be loaded and
     *         ignoreExceptions is true.
     * @throws IllegalArgumentException
     *             if the class could not be loaded and ignoreExceptions was false.
     */
    Class<?> loadClass(final boolean ignoreExceptions) {
        if (classRef == null) {
            final String className = getClassInfoNameOrClassName();
            if (scanResult != null) {
                classRef = scanResult.loadClass(className, ignoreExceptions);
            } else {
                // Fallback, if scanResult is not set
                try {
                    classRef = Class.forName(className);
                } catch (final Throwable t) {
                    if (!ignoreExceptions) {
                        throw new IllegalArgumentException("Could not load class " + className, t);
                    }
                }
            }
        }
        return classRef;
    }

    /**
     * Load the class named returned by {@link #getClassInfo()}, or if that returns null, the class named by
     * {@link #getClassName()}. Returns a {@code Class<?>} reference for the class.
     *
     * @return The {@code Class<?>} reference for the referenced class.
     * @throws IllegalArgumentException
     *             if the class could not be loaded.
     */
    Class<?> loadClass() {
        return loadClass(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Render to string.
     *
     * @param useSimpleNames
     *            if true, use just the simple name of each class.
     * @param buf
     *            the buf
     */
    protected abstract void toString(final boolean useSimpleNames, StringBuilder buf);

    /**
     * Render to string, with simple names for classes if useSimpleNames is true.
     *
     * @param useSimpleNames
     *            if true, use just the simple name of each class.
     * @return the string representation.
     */
    String toString(final boolean useSimpleNames) {
        final StringBuilder buf = new StringBuilder();
        toString(useSimpleNames, buf);
        return buf.toString();
    }

    /**
     * Render to string, using only <a href=
     * "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">simple
     * names</a> for classes.
     *
     * @return the string representation, using simple names for classes.
     */
    public String toStringWithSimpleNames() {
        final StringBuilder buf = new StringBuilder();
        toString(/* useSimpleNames = */ true, buf);
        return buf.toString();
    }

    /**
     * Render to string.
     *
     * @return the string representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        toString(/* useSimpleNames = */ false, buf);
        return buf.toString();
    }
}
