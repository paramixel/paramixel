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

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.StringUtils;

/**
 * {@link RandomAccessReader} for a {@link File}. Reads in <b>little endian</b> order, as required by the zipfile
 * format.
 */
public class RandomAccessFileChannelReader implements RandomAccessReader {

    /** The file channel. */
    private final FileChannel fileChannel;

    /** The slice start pos. */
    private final long sliceStartPos;

    /** The slice length. */
    private final long sliceLength;

    /** The reusable byte buffer. */
    private ByteBuffer reusableByteBuffer;

    /** The scratch arr. */
    private final byte[] scratchArr = new byte[8];

    /** The scratch byte buf. */
    private final ByteBuffer scratchByteBuf = ByteBuffer.wrap(scratchArr);

    /** The utf 8 bytes. */
    private byte[] utf8Bytes;

    /**
     * Constructor.
     *
     * @param fileChannel
     *            the file channel
     * @param sliceStartPos
     *            the slice start pos
     * @param sliceLength
     *            the slice length
     */
    public RandomAccessFileChannelReader(
            final FileChannel fileChannel, final long sliceStartPos, final long sliceLength) {
        this.fileChannel = fileChannel;
        this.sliceStartPos = sliceStartPos;
        this.sliceLength = sliceLength;
    }

    @Override
    public int read(final long srcOffset, final ByteBuffer dstBuf, final int dstBufStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        try {
            if (srcOffset < 0L || numBytes < 0 || numBytes > sliceLength - srcOffset) {
                throw new IOException("Read index out of bounds");
            }
            final long srcStart = sliceStartPos + srcOffset;
            ((Buffer) dstBuf).position(dstBufStart);
            ((Buffer) dstBuf).limit(dstBufStart + numBytes);
            final int numBytesRead = fileChannel.read(dstBuf, srcStart);
            return numBytesRead == 0 ? -1 : numBytesRead;

        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public int read(final long srcOffset, final byte[] dstArr, final int dstArrStart, final int numBytes)
            throws IOException {
        if (numBytes == 0) {
            return 0;
        }
        try {
            if (srcOffset < 0L || numBytes < 0 || numBytes > sliceLength - srcOffset) {
                throw new IOException("Read index out of bounds");
            }
            if (reusableByteBuffer == null || reusableByteBuffer.array() != dstArr) {
                // If reusableByteBuffer is not set, or wraps a different array from a previous operation,
                // wrap dstArr with a new ByteBuffer
                reusableByteBuffer = ByteBuffer.wrap(dstArr);
            }
            // Read into reusableByteBuffer, which is backed with dstArr
            return read(srcOffset, reusableByteBuffer, dstArrStart, numBytes);

        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            throw new IOException("Read index out of bounds");
        }
    }

    @Override
    public byte readByte(final long offset) throws IOException {
        if (read(offset, scratchByteBuf, 0, 1) < 1) {
            throw new IOException("Premature EOF");
        }
        return scratchArr[0];
    }

    @Override
    public int readUnsignedByte(final long offset) throws IOException {
        if (read(offset, scratchByteBuf, 0, 1) < 1) {
            throw new IOException("Premature EOF");
        }
        return scratchArr[0] & 0xff;
    }

    @Override
    public short readShort(final long offset) throws IOException {
        return (short) readUnsignedShort(offset);
    }

    @Override
    public int readUnsignedShort(final long offset) throws IOException {
        if (read(offset, scratchByteBuf, 0, 2) < 2) {
            throw new IOException("Premature EOF");
        }
        return ((scratchArr[1] & 0xff) << 8) //
                | (scratchArr[0] & 0xff);
    }

    @Override
    public int readInt(final long offset) throws IOException {
        if (read(offset, scratchByteBuf, 0, 4) < 4) {
            throw new IOException("Premature EOF");
        }
        return ((scratchArr[3] & 0xff) << 24) //
                | ((scratchArr[2] & 0xff) << 16) //
                | ((scratchArr[1] & 0xff) << 8) //
                | (scratchArr[0] & 0xff);
    }

    @Override
    public long readUnsignedInt(final long offset) throws IOException {
        return readInt(offset) & 0xffffffffL;
    }

    @Override
    public long readLong(final long offset) throws IOException {
        if (read(offset, scratchByteBuf, 0, 8) < 8) {
            throw new IOException("Premature EOF");
        }
        return ((scratchArr[7] & 0xffL) << 56) //
                | ((scratchArr[6] & 0xffL) << 48) //
                | ((scratchArr[5] & 0xffL) << 40) //
                | ((scratchArr[4] & 0xffL) << 32) //
                | ((scratchArr[3] & 0xffL) << 24) //
                | ((scratchArr[2] & 0xffL) << 16) //
                | ((scratchArr[1] & 0xffL) << 8) //
                | (scratchArr[0] & 0xffL);
    }

    @Override
    public String readString(
            final long offset, final int numBytes, final boolean replaceSlashWithDot, final boolean stripLSemicolon)
            throws IOException {
        // Reuse UTF8 buffer array if it's non-null from a previous call, and if it's big enough
        if (utf8Bytes == null || utf8Bytes.length < numBytes) {
            utf8Bytes = new byte[numBytes];
        }
        if (read(offset, utf8Bytes, 0, numBytes) < numBytes) {
            throw new IOException("Premature EOF");
        }
        return StringUtils.readString(utf8Bytes, 0, numBytes, replaceSlashWithDot, stripLSemicolon);
    }

    @Override
    public String readString(final long offset, final int numBytes) throws IOException {
        return readString(offset, numBytes, false, false);
    }
}
