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

import java.util.List;
import nonapi.org.paramixel.classgraph.io.github.classgraph.Classfile.TypePathNode;

/**
 * A Java type signature. Subclasses are ClassTypeSignature, MethodTypeSignature, and TypeSignature.
 */
public abstract class HierarchicalTypeSignature extends ScanResultObject {
    protected AnnotationInfoList typeAnnotationInfo;

    /** A hierarchical type signature. */
    public HierarchicalTypeSignature() {
        super();
    }

    /**
     * Add a type annotation.
     *
     * @param annotationInfo
     *            the annotation
     */
    protected void addTypeAnnotation(final AnnotationInfo annotationInfo) {
        if (typeAnnotationInfo == null) {
            typeAnnotationInfo = new AnnotationInfoList(1);
        }
        typeAnnotationInfo.add(annotationInfo);
    }

    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                annotationInfo.setScanResult(scanResult);
            }
        }
    }

    /**
     * Get a list of {@link AnnotationInfo} objects for any type annotations on this type, or null if none.
     *
     * @return a list of {@link AnnotationInfo} objects for any type annotations on this type, or null if none.
     */
    public AnnotationInfoList getTypeAnnotationInfo() {
        return typeAnnotationInfo;
    }

    /**
     * Add a type annotation.
     *
     * @param typePath
     *            the type path
     * @param annotationInfo
     *            the annotation
     */
    protected abstract void addTypeAnnotation(List<TypePathNode> typePath, AnnotationInfo annotationInfo);

    /**
     * Render type signature to string.
     *
     * @param useSimpleNames
     *            whether to use simple names for classes.
     * @param annotationsToExclude
     *            toplevel annotations to exclude, to eliminate duplication (toplevel annotations are both
     *            class/field/method annotations and type annotations).
     * @param buf
     *            the {@link StringBuilder} to write to.
     */
    protected abstract void toStringInternal(
            final boolean useSimpleNames, AnnotationInfoList annotationsToExclude, StringBuilder buf);

    /**
     * Render type signature to string.
     *
     * @param useSimpleNames
     *            whether to use simple names for classes.
     * @param buf
     *            the {@link StringBuilder} to write to.
     */
    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        toStringInternal(useSimpleNames, /* annotationsToExclude = */ null, buf);
    }
}
