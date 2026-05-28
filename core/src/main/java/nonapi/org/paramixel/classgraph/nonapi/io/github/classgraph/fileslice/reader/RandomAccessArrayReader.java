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
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.StringUtils;

/**
 * {@link RandomAccessReader} backed by a byte array. Reads in <b>little endian</b> order, as required by the
 * zipfile format.
 */
public class RandomAccessArrayReader implements RandomAccessReader {
    /** The array. */
    private final byte[] arr;

    /** The start index of the slice within the array. */
    private final int sliceStartPos;

    /** The length of the slice within the array. */
    private final int sliceLength;

    /**
     * Constructor for slicing an array.
     *
     * @param arr
     *            the array to slice.
     * @param sliceStartPos
     *            the start index of the slice within the array.
     * @param sliceLength
     *            the length of the slice within the array.
     */
    public RandomAccessArrayReader(final byte[] arr, final int sliceStartPos, final int sliceLength) {
        this.arr = arr;
        this.sliceStartPos = sliceStartPos;
        this.sliceLength = sliceLength;
    }

    @Override
    public int read(final long srcOffset, final byte[] dstArr, final int dstArrStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        if (srcOffset < 0L || numBytes < 0 || numBytes > sliceLength - srcOffset) {
            throw new IOException("Read index out of bounds");
        }
        try {
            final int numBytesToRead = Math.max(Math.min(numBytes, dstArr.length - dstArrStart), 0);
            if (numBytesToRead == 0) {
                return -1;
            }
            final int srcStart = (int) (sliceStartPos + srcOffset);
            System.arraycopy(arr, srcStart, dstArr, dstArrStart, numBytesToRead);
            return numBytesToRead;
        } catch (final IndexOutOfBoundsException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public int read(final long srcOffset, final ByteBuffer dstBuf, final int dstBufStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        if (srcOffset < 0L || numBytes < 0 || numBytes > sliceLength - srcOffset) {
            throw new IOException("Read index out of bounds");
        }
        try {
            final int numBytesToRead = Math.max(Math.min(numBytes, dstBuf.capacity() - dstBufStart), 0);
            if (numBytesToRead == 0) {
                return -1;
            }
            final int srcStart = (int) (sliceStartPos + srcOffset);
            ((Buffer) dstBuf).position(dstBufStart);
            ((Buffer) dstBuf).limit(dstBufStart + numBytesToRead);
            dstBuf.put(arr, srcStart, numBytesToRead);
            return numBytesToRead;
        } catch (BufferUnderflowException | IndexOutOfBoundsException | ReadOnlyBufferException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public byte readByte(final long offset) throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return arr[idx];
    }

    @Override
    public int readUnsignedByte(final long offset) throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return arr[idx] & 0xff;
    }

    @Override
    public short readShort(final long offset) throws IOException {
        return (short) readUnsignedShort(offset);
    }

    @Override
    public int readUnsignedShort(final long offset) throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return ((arr[idx + 1] & 0xff) << 8) //
                | (arr[idx] & 0xff);
    }

    @Override
    public int readInt(final long offset) throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return ((arr[idx + 3] & 0xff) << 24) //
                | ((arr[idx + 2] & 0xff) << 16) //
                | ((arr[idx + 1] & 0xff) << 8) //
                | (arr[idx] & 0xff);
    }

    @Override
    public long readUnsignedInt(final long offset) throws IOException {
        return readInt(offset) & 0xffffffffL;
    }

    @Override
    public long readLong(final long offset) throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return ((arr[idx + 7] & 0xffL) << 56) //
                | ((arr[idx + 6] & 0xffL) << 48) //
                | ((arr[idx + 5] & 0xffL) << 40) //
                | ((arr[idx + 4] & 0xffL) << 32) //
                | ((arr[idx + 3] & 0xffL) << 24) //
                | ((arr[idx + 2] & 0xffL) << 16) //
                | ((arr[idx + 1] & 0xffL) << 8) //
                | (arr[idx] & 0xffL);
    }

    @Override
    public String readString(
            final long offset, final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException {
        final int idx = sliceStartPos + (int) offset;
        return StringUtils.readString(arr, idx, numBytes, replaceSlashWithDot, stripLSemicolon);
    }

    @Override
    public String readString(final long offset, final int numBytes) throws IOException {
        return readString(offset, numBytes, false, false);
    }
}
