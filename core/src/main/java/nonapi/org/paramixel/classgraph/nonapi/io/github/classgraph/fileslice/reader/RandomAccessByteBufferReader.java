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
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.StringUtils;

/**
 * {@link RandomAccessReader} for a {@link ByteBuffer}. Reads in <b>little endian</b> order, as required by the
 * zipfile format.
 */
public class RandomAccessByteBufferReader implements RandomAccessReader {
    /** The byte buffer. */
    private final ByteBuffer byteBuffer;

    /** The slice start pos. */
    private final int sliceStartPos;

    /** The slice length. */
    private final int sliceLength;

    /**
     * Constructor.
     *
     * @param byteBuffer
     *            the byte buffer
     * @param sliceStartPos
     *            the slice start pos
     * @param sliceLength
     *            the slice length
     */
    public RandomAccessByteBufferReader(final ByteBuffer byteBuffer, final long sliceStartPos, final long sliceLength) {
        this.byteBuffer = byteBuffer.duplicate();
        this.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        this.sliceStartPos = (int) sliceStartPos;
        this.sliceLength = (int) sliceLength;
        ((Buffer) this.byteBuffer).position(this.sliceStartPos);
        ((Buffer) this.byteBuffer).limit(this.sliceStartPos + this.sliceLength);
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
            final int srcStart = (int) srcOffset;
            ((Buffer) byteBuffer).position(sliceStartPos + srcStart);
            byteBuffer.get(dstArr, dstArrStart, numBytesToRead);
            ((Buffer) byteBuffer).position(sliceStartPos);
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
            ((Buffer) byteBuffer).position(srcStart);
            ((Buffer) dstBuf).position(dstBufStart);
            ((Buffer) dstBuf).limit(dstBufStart + numBytesToRead);
            dstBuf.put(byteBuffer);
            ((Buffer) byteBuffer).limit(sliceStartPos + sliceLength);
            ((Buffer) byteBuffer).position(sliceStartPos);
            return numBytesToRead;
        } catch (BufferUnderflowException | IndexOutOfBoundsException | ReadOnlyBufferException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public byte readByte(final long offset) throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        return byteBuffer.get(idx);
    }

    @Override
    public int readUnsignedByte(final long offset) throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        return byteBuffer.get(idx) & 0xff;
    }

    @Override
    public int readUnsignedShort(final long offset) throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        return byteBuffer.getShort(idx) & 0xff;
    }

    @Override
    public short readShort(final long offset) throws IOException {
        return (short) readUnsignedShort(offset);
    }

    @Override
    public int readInt(final long offset) throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        return byteBuffer.getInt(idx);
    }

    @Override
    public long readUnsignedInt(final long offset) throws IOException {
        return readInt(offset) & 0xffffffffL;
    }

    @Override
    public long readLong(final long offset) throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        return byteBuffer.getLong(idx);
    }

    @Override
    public String readString(
            final long offset, final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException {
        final int idx = (int) (sliceStartPos + offset);
        final byte[] arr = new byte[numBytes];
        if (read(offset, arr, 0, numBytes) < numBytes) {
            throw new IOException("Premature EOF while reading string");
        }
        return StringUtils.readString(arr, idx, numBytes, replaceSlashWithDot, stripLSemicolon);
    }

    @Override
    public String readString(final long offset, final int numBytes) throws IOException {
        return readString(offset, numBytes, false, false);
    }
}
