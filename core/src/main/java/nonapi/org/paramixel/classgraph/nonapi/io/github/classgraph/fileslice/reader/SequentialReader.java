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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.reader;

import java.io.IOException;

/** Interface for sequentially reading values in byte order. */
public interface SequentialReader {
    /**
     * Read a byte at the current cursor position.
     *
     * @return The byte at the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    byte readByte() throws IOException;

    /**
     * Read an unsigned byte at the current cursor position.
     *
     * @return The unsigned byte at the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readUnsignedByte() throws IOException;

    /**
     * Read a short at the current cursor position.
     *
     * @return The short at the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    short readShort() throws IOException;

    /**
     * Read a unsigned short at the current cursor position.
     *
     * @return The unsigned shortat the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readUnsignedShort() throws IOException;

    /**
     * Read a int at the current cursor position.
     *
     * @return The int at the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readInt() throws IOException;

    /**
     * Read a unsigned int at the current cursor position.
     *
     * @return The int at the current cursor position, as a long.
     * @throws IOException
     *             If there was an exception while reading.
     */
    long readUnsignedInt() throws IOException;

    /**
     * Read a long at the current cursor position.
     *
     * @return The long at the current cursor position.
     * @throws IOException
     *             If there was an exception while reading.
     */
    long readLong() throws IOException;

    /**
     * Skip the given number of bytes.
     *
     * @param bytesToSkip
     *            The number of bytes to skip.
     * @throws IOException
     *             If there was an exception while reading.
     */
    void skip(final int bytesToSkip) throws IOException;

    /**
     * Reads the "modified UTF8" format defined in the Java classfile spec, optionally replacing '/' with '.', and
     * optionally removing the prefix "L" and the suffix ";".
     *
     * @param numBytes
     *            The number of bytes of the UTF8 encoding of the string.
     * @param replaceSlashWithDot
     *            If true, replace '/' with '.'.
     * @param stripLSemicolon
     *            If true, string final ';' character.
     * @return The string.
     * @throws IOException
     *             If an I/O exception occurs.
     */
    String readString(final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException;

    /**
     * Reads the "modified UTF8" format defined in the Java classfile spec.
     *
     * @param numBytes
     *            The number of bytes of the UTF8 encoding of the string.
     * @return The string.
     * @throws IOException
     *             If an I/O exception occurs.
     */
    String readString(final int numBytes) throws IOException;
}
