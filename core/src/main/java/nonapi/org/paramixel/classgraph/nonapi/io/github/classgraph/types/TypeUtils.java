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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.types;

import java.lang.reflect.Modifier;

/**
 * Utilities for parsing Java type descriptors and type signatures.
 *
 * @author lukehutch
 */
public final class TypeUtils {

    /**
     * Constructor.
     */
    private TypeUtils() {
        // Cannot be constructed
    }

    /**
     * Parse a Java identifier, replacing '/' with '.'. Appends the identifier to the token buffer in the parser.
     *
     * @param parser
     *            The parser.
     * @param stopAtDollarSign
     *            If true, stop parsing when the first '$' is hit.
     * @param stopAtDot
     *            If true, stop parsing when the first '.' is hit.
     * @return true if at least one identifier character was parsed.
     */
    public static boolean getIdentifierToken(
            final Parser parser, final boolean stopAtDollarSign, final boolean stopAtDot) {
        boolean consumedChar = false;
        while (parser.hasMore()) {
            final char c = parser.peek();
            if (c == '/') {
                parser.appendToToken('.');
                parser.next();
                consumedChar = true;
            } else if (c != ';'
                    && c != '['
                    && c != '<'
                    && c != '>'
                    && c != ':'
                    && (!stopAtDollarSign || c != '$')
                    && (!stopAtDot || c != '.')) {
                parser.appendToToken(c);
                parser.next();
                consumedChar = true;
            } else {
                break;
            }
        }
        return consumedChar;
    }

    /** The origin of the modifier bits. */
    public enum ModifierType {
        /** The modifier bits apply to a class. */
        CLASS,
        /** The modifier bits apply to a method. */
        METHOD,
        /** The modifier bits apply to a field. */
        FIELD
    }

    /**
     * Append a space if necessary (if not at the beginning of the buffer, and the last character is not already a
     * space), then append a modifier keyword.
     *
     * @param buf
     *            the buf
     * @param modifierKeyword
     *            the modifier keyword
     */
    private static void appendModifierKeyword(final StringBuilder buf, final String modifierKeyword) {
        if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' ') {
            buf.append(' ');
        }
        buf.append(modifierKeyword);
    }

    /**
     * Convert modifiers into a string representation, e.g. "public static final".
     *
     * @param modifiers
     *            The field or method modifiers.
     * @param modifierType
     *            The {@link ModifierType} these modifiers apply to.
     * @param isDefault
     *            for methods, true if this is a default method (else ignored).
     * @param buf
     *            The buffer to write the result into.
     */
    public static void modifiersToString(
            final int modifiers, final ModifierType modifierType, final boolean isDefault, final StringBuilder buf) {
        if ((modifiers & Modifier.PUBLIC) != 0) {
            appendModifierKeyword(buf, "public");
        } else if ((modifiers & Modifier.PRIVATE) != 0) {
            appendModifierKeyword(buf, "private");
        } else if ((modifiers & Modifier.PROTECTED) != 0) {
            appendModifierKeyword(buf, "protected");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & Modifier.ABSTRACT) != 0) {
            appendModifierKeyword(buf, "abstract");
        }
        if ((modifiers & Modifier.STATIC) != 0) {
            appendModifierKeyword(buf, "static");
        }
        if (modifierType == ModifierType.FIELD) {
            if ((modifiers & Modifier.VOLATILE) != 0) {
                // "bridge" and "volatile" overlap in bit 0x40
                appendModifierKeyword(buf, "volatile");
            }
            if ((modifiers & Modifier.TRANSIENT) != 0) {
                appendModifierKeyword(buf, "transient");
            }
        }
        if ((modifiers & Modifier.FINAL) != 0) {
            appendModifierKeyword(buf, "final");
        }
        if (modifierType == ModifierType.METHOD) {
            if ((modifiers & Modifier.SYNCHRONIZED) != 0) {
                appendModifierKeyword(buf, "synchronized");
            }
            if (isDefault) {
                appendModifierKeyword(buf, "default");
            }
        }
        if ((modifiers & 0x1000) != 0) {
            appendModifierKeyword(buf, "synthetic");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & 0x40) != 0) {
            // "bridge" and "volatile" overlap in bit 0x40
            appendModifierKeyword(buf, "bridge");
        }
        if (modifierType == ModifierType.METHOD && (modifiers & Modifier.NATIVE) != 0) {
            appendModifierKeyword(buf, "native");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & Modifier.STRICT) != 0) {
            appendModifierKeyword(buf, "strictfp");
        }
        // Ignored:
        // ACC_SUPER (0x0020): Treat superclass methods specially when invoked by the invokespecial instruction
    }
}
