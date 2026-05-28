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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nonapi.org.paramixel.classgraph.io.github.classgraph.Classfile.TypePathNode;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.ParseException;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.Parser;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * A type signature for a reference type or base type. Subclasses are {@link ReferenceTypeSignature} (whose own
 * subclasses are {@link ClassRefTypeSignature}, {@link TypeVariableSignature}, and {@link ArrayTypeSignature}), and
 * {@link BaseTypeSignature}.
 */
public abstract class TypeSignature extends HierarchicalTypeSignature {
    /** Constructor. */
    protected TypeSignature() {
        // Empty
    }

    /**
     * Get the names of any classes referenced in the type signature.
     *
     * @param refdClassNames
     *            the referenced class names.
     */
    protected void findReferencedClassNames(final Set<String> refdClassNames) {
        final String className = getClassName();
        if (className != null && !className.isEmpty()) {
            refdClassNames.add(getClassName());
        }
    }

    /**
     * Get {@link ClassInfo} objects for any classes referenced in the type signature.
     *
     * @param classNameToClassInfo
     *            the map from class name to {@link ClassInfo}.
     * @param refdClassInfo
     *            the referenced class info.
     */
    @Override
    protected final void findReferencedClassInfo(
            final Map<String, ClassInfo> classNameToClassInfo, final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final Set<String> refdClassNames = new HashSet<>();
        findReferencedClassNames(refdClassNames);
        for (final String refdClassName : refdClassNames) {
            final ClassInfo classInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
            classInfo.scanResult = scanResult;
            refdClassInfo.add(classInfo);
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
     * Compare base types, ignoring generic type parameters.
     *
     * @param other
     *            the other {@link TypeSignature} to compare to.
     * @return True if the two {@link TypeSignature} objects are equal, ignoring type parameters.
     */
    public abstract boolean equalsIgnoringTypeParams(final TypeSignature other);

    /**
     * Parse a type signature.
     *
     * @param parser
     *            The parser
     * @param definingClass
     *            The class containing the type descriptor.
     * @return The parsed type descriptor or type signature.
     * @throws ParseException
     *             If the type signature could not be parsed.
     */
    static TypeSignature parse(final Parser parser, final String definingClass) throws ParseException {
        final ReferenceTypeSignature referenceTypeSignature =
                ReferenceTypeSignature.parseReferenceTypeSignature(parser, definingClass);
        if (referenceTypeSignature != null) {
            return referenceTypeSignature;
        }
        final BaseTypeSignature baseTypeSignature = BaseTypeSignature.parse(parser);
        if (baseTypeSignature != null) {
            return baseTypeSignature;
        }
        return null;
    }

    /**
     * Parse a type signature.
     *
     * @param typeDescriptor
     *            The type descriptor or type signature to parse.
     * @param definingClass
     *            The class containing the type descriptor.
     * @return The parsed type descriptor or type signature.
     * @throws ParseException
     *             If the type signature could not be parsed.
     */
    static TypeSignature parse(final String typeDescriptor, final String definingClass) throws ParseException {
        final Parser parser = new Parser(typeDescriptor);
        TypeSignature typeSignature;
        typeSignature = parse(parser, definingClass);
        if (typeSignature == null) {
            throw new ParseException(parser, "Could not parse type signature");
        }
        if (parser.hasMore()) {
            throw new ParseException(parser, "Extra characters at end of type descriptor");
        }
        return typeSignature;
    }

    /**
     * Add a type annotation to this type.
     *
     * @param typePath
     *            The type path.
     * @param annotationInfo
     *            The annotation to add.
     */
    @Override
    protected abstract void addTypeAnnotation(List<TypePathNode> typePath, AnnotationInfo annotationInfo);
}
