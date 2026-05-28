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
import java.nio.ByteBuffer;

/** Interface for random access to values in byte order. */
public interface RandomAccessReader {
    /**
     * Read bytes into a {@link ByteBuffer}.
     *
     * @param srcOffset
     *            The offset to start reading from.
     * @param dstBuf
     *            The {@link ByteBuffer} to write into.
     * @param dstBufStart
     *            The offset within the destination buffer to start writing at.
     * @param numBytes
     *            The number of bytes to read.
     * @return The number of bytes actually read, or -1 if no more bytes could be read.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int read(long srcOffset, ByteBuffer dstBuf, int dstBufStart, int numBytes) throws IOException;

    /**
     * Read bytes into a byte array.
     *
     * @param srcOffset
     *            The offset to start reading from.
     * @param dstArr
     *            The byte array to write into.
     * @param dstArrStart
     *            The offset within the destination array to start writing at.
     * @param numBytes
     *            The number of bytes to read.
     * @return The number of bytes actually read, or -1 if no more bytes could be read.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int read(long srcOffset, byte[] dstArr, int dstArrStart, int numBytes) throws IOException;

    /**
     * Read a byte at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The byte at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    byte readByte(final long offset) throws IOException;

    /**
     * Read an unsigned byte at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The unsigned byte at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readUnsignedByte(final long offset) throws IOException;

    /**
     * Read a short at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The short at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    short readShort(final long offset) throws IOException;

    /**
     * Read a unsigned short at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The unsigned short at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readUnsignedShort(final long offset) throws IOException;

    /**
     * Read a int at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The int at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    int readInt(final long offset) throws IOException;

    /**
     * Read a unsigned int at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The int at the offset, as a long.
     * @throws IOException
     *             If there was an exception while reading.
     */
    long readUnsignedInt(final long offset) throws IOException;

    /**
     * Read a long at a specific offset (without changing the current cursor offset).
     *
     * @param offset
     *            The buffer offset to read from.
     * @return The long at the offset.
     * @throws IOException
     *             If there was an exception while reading.
     */
    long readLong(final long offset) throws IOException;

    /**
     * Reads the "modified UTF8" format defined in the Java classfile spec, optionally replacing '/' with '.', and
     * optionally removing the prefix "L" and the suffix ";".
     *
     * @param offset
     *            The start offset of the string.
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
    String readString(
            final long offset, final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException;

    /**
     * Reads the "modified UTF8" format defined in the Java classfile spec.
     *
     * @param offset
     *            The start offset of the string.
     * @param numBytes
     *            The number of bytes of the UTF8 encoding of the string.
     * @return The string.
     * @throws IOException
     *             If an I/O exception occurs.
     */
    String readString(final long offset, final int numBytes) throws IOException;
}
