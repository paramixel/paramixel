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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nonapi.org.paramixel.classgraph.io.github.classgraph.Classfile.TypePathNode;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.ParseException;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.Parser;

/** A type argument. */
public final class TypeArgument extends HierarchicalTypeSignature {
    /** A type wildcard. */
    public enum Wildcard {
        /** No wildcard. */
        NONE,

        /** The '?' wildcard. */
        ANY,

        /** extends. */
        EXTENDS,

        /** super. */
        SUPER
    }

    /** A wildcard type. */
    private final Wildcard wildcard;

    /** Type signature (will be null if wildcard == ANY). */
    private final ReferenceTypeSignature typeSignature;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param wildcard
     *            The wildcard type
     * @param typeSignature
     *            The type signature
     */
    private TypeArgument(final Wildcard wildcard, final ReferenceTypeSignature typeSignature) {
        super();
        this.wildcard = wildcard;
        this.typeSignature = typeSignature;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the type wildcard, which is one of {NONE, ANY, EXTENDS, SUPER}.
     *
     * @return The type wildcard.
     */
    public Wildcard getWildcard() {
        return wildcard;
    }

    /**
     * Get the type signature associated with the wildcard (or null, if the wildcard is ANY).
     *
     * @return The type signature.
     */
    public ReferenceTypeSignature getTypeSignature() {
        return typeSignature;
    }

    @Override
    protected void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        if (typePath.size() == 0 && wildcard != Wildcard.NONE) {
            // Annotation before wildcard
            addTypeAnnotation(annotationInfo);
        } else if (typePath.size() > 0 && typePath.get(0).typePathKind == 2) {
            // Annotation is on the bound of a wildcard type argument of a parameterized type.
            // TypeSignature can be null in a corrupt classfile (#758).
            if (typeSignature != null) {
                typeSignature.addTypeAnnotation(typePath.subList(1, typePath.size()), annotationInfo);
            }
        } else {
            // Annotation is on a type argument of a parameterized type.
            // TypeSignature can be null in a corrupt classfile (#758).
            if (typeSignature != null) {
                typeSignature.addTypeAnnotation(typePath, annotationInfo);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Parse a type argument.
     *
     * @param parser
     *            The parser.
     * @param definingClassName
     *            The name of the defining class (for resolving type variables).
     * @return The parsed method type signature.
     * @throws ParseException
     *             If method type signature could not be parsed.
     */
    private static TypeArgument parse(final Parser parser, final String definingClassName) throws ParseException {
        final char peek = parser.peek();
        if (peek == '*') {
            parser.expect('*');
            return new TypeArgument(Wildcard.ANY, null);
        } else if (peek == '+') {
            parser.expect('+');
            final ReferenceTypeSignature typeSignature =
                    ReferenceTypeSignature.parseReferenceTypeSignature(parser, definingClassName);
            if (typeSignature == null) {
                throw new ParseException(parser, "Missing '+' type bound");
            }
            return new TypeArgument(Wildcard.EXTENDS, typeSignature);
        } else if (peek == '-') {
            parser.expect('-');
            final ReferenceTypeSignature typeSignature =
                    ReferenceTypeSignature.parseReferenceTypeSignature(parser, definingClassName);
            if (typeSignature == null) {
                throw new ParseException(parser, "Missing '-' type bound");
            }
            return new TypeArgument(Wildcard.SUPER, typeSignature);
        } else {
            final ReferenceTypeSignature typeSignature =
                    ReferenceTypeSignature.parseReferenceTypeSignature(parser, definingClassName);
            if (typeSignature == null) {
                throw new ParseException(parser, "Missing type bound");
            }
            return new TypeArgument(Wildcard.NONE, typeSignature);
        }
    }

    /**
     * Parse a list of type arguments.
     *
     * @param parser
     *            The parser.
     * @param definingClassName
     *            The name of the defining class (for resolving type variables).
     * @return The list of type arguments.
     * @throws ParseException
     *             If type signature could not be parsed.
     */
    static List<TypeArgument> parseList(final Parser parser, final String definingClassName) throws ParseException {
        if (parser.peek() == '<') {
            parser.expect('<');
            final List<TypeArgument> typeArguments = new ArrayList<>(2);
            while (parser.peek() != '>') {
                if (!parser.hasMore()) {
                    throw new ParseException(parser, "Missing '>'");
                }
                typeArguments.add(parse(parser, definingClassName));
            }
            parser.expect('>');
            return typeArguments;
        } else {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        // getClassInfo() is not valid for this type, so getClassName() does not need to be implemented
        throw new IllegalArgumentException("getClassName() cannot be called here");
    }

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() cannot be called here");
    }

    /* (non-Javadoc)
     * @see nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResultObject#setScanResult(nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
    }

    /**
     * Get the names of any classes referenced in the type signature.
     *
     * @param refdClassNames
     *            the referenced class names.
     */
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        if (typeSignature != null) {
            typeSignature.findReferencedClassNames(refdClassNames);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return (typeSignature != null ? typeSignature.hashCode() : 0) + 7 * wildcard.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof TypeArgument)) {
            return false;
        }
        final TypeArgument other = (TypeArgument) obj;
        return Objects.equals(this.typeAnnotationInfo, other.typeAnnotationInfo)
                && (Objects.equals(this.typeSignature, other.typeSignature) && other.wildcard.equals(this.wildcard));
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void toStringInternal(
            final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude, final StringBuilder buf) {
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                if (annotationsToExclude == null || !annotationsToExclude.contains(annotationInfo)) {
                    annotationInfo.toString(useSimpleNames, buf);
                    buf.append(' ');
                }
            }
        }
        switch (wildcard) {
            case ANY:
                buf.append('?');
                break;
            case EXTENDS:
                final String typeSigStr = typeSignature.toString(useSimpleNames);
                buf.append(typeSigStr.equals("java.lang.Object") ? "?" : "? extends " + typeSigStr);
                break;
            case SUPER:
                buf.append("? super ");
                typeSignature.toString(useSimpleNames, buf);
                break;
            case NONE:
            default:
                typeSignature.toString(useSimpleNames, buf);
                break;
        }
    }
}
