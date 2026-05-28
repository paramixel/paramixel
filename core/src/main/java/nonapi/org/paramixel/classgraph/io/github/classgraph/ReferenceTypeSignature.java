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

import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.ParseException;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types.Parser;

/**
 * A type signature for a reference type. Subclasses are {@link ClassRefOrTypeVariableSignature}
 * ({@link ClassRefTypeSignature} or {@link TypeVariableSignature}), and {@link ArrayTypeSignature}.
 */
public abstract class ReferenceTypeSignature extends TypeSignature {
    /** Constructor. */
    protected ReferenceTypeSignature() {
        super();
    }

    /**
     * Parse a reference type signature.
     *
     * @param parser
     *            The parser
     * @param definingClassName
     *            The class containing the type descriptor.
     * @return The parsed type reference type signature.
     * @throws ParseException
     *             If the type signature could not be parsed.
     */
    static ReferenceTypeSignature parseReferenceTypeSignature(final Parser parser, final String definingClassName)
            throws ParseException {
        final ClassRefTypeSignature classTypeSignature = ClassRefTypeSignature.parse(parser, definingClassName);
        if (classTypeSignature != null) {
            return classTypeSignature;
        }
        final TypeVariableSignature typeVariableSignature = TypeVariableSignature.parse(parser, definingClassName);
        if (typeVariableSignature != null) {
            return typeVariableSignature;
        }
        final ArrayTypeSignature arrayTypeSignature = ArrayTypeSignature.parse(parser, definingClassName);
        if (arrayTypeSignature != null) {
            return arrayTypeSignature;
        }
        return null;
    }

    /**
     * Parse a class bound.
     *
     * @param parser
     *            The parser.
     * @param definingClassName
     *            The class containing the type descriptor.
     * @return The parsed class bound.
     * @throws ParseException
     *             If the type signature could not be parsed.
     */
    static ReferenceTypeSignature parseClassBound(final Parser parser, final String definingClassName)
            throws ParseException {
        parser.expect(':');
        // May return null if there is no signature after ':' (class bound signature may be empty)
        return parseReferenceTypeSignature(parser, definingClassName);
    }
}
