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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fastzipfilereader.NestedJarHandler;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.reader.RandomAccessFileChannelReader;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.reader.RandomAccessReader;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.FileUtils;

/** A {@link Path} slice. */
public class PathSlice extends Slice {
    /** The {@link Path}. */
    public final Path path;

    /** The file length. */
    private final long fileLength;

    /** The {@link FileChannel} opened on the {@link Path}. */
    private FileChannel fileChannel;

    /** True if this is a top level file slice. */
    private final boolean isTopLevelFileSlice;

    /** True if {@link #close} has been called. */
    private final AtomicBoolean isClosed = new AtomicBoolean();

    /**
     * Constructor for treating a range of a file as a slice.
     *
     * @param parentSlice
     *            the parent slice
     * @param offset
     *            the offset of the sub-slice within the parent slice
     * @param length
     *            the length of the sub-slice
     * @param isDeflatedZipEntry
     *            true if this is a deflated zip entry
     * @param inflatedLengthHint
     *            the uncompressed size of a deflated zip entry, or -1 if unknown, or 0 of this is not a deflated
     *            zip entry.
     * @param nestedJarHandler
     *            the nested jar handler
     */
    private PathSlice(
            final PathSlice parentSlice,
            final long offset,
            final long length,
            final boolean isDeflatedZipEntry,
            final long inflatedLengthHint,
            final NestedJarHandler nestedJarHandler) {
        super(parentSlice, offset, length, isDeflatedZipEntry, inflatedLengthHint, nestedJarHandler);

        this.path = parentSlice.path;
        this.fileChannel = parentSlice.fileChannel;
        this.fileLength = parentSlice.fileLength;
        this.isTopLevelFileSlice = false;

        // Only mark toplevel file slices as open (sub slices don't need to be marked as
        // open since
        // they don't need to be closed, they just copy the resource references of the
        // toplevel slice)
    }

    /**
     * Constructor for toplevel file slice.
     *
     * @param path
     *            the path
     * @param isDeflatedZipEntry
     *            true if this is a deflated zip entry
     * @param inflatedLengthHint
     *            the uncompressed size of a deflated zip entry, or -1 if unknown, or 0 of this is not a deflated
     *            zip entry.
     * @param nestedJarHandler
     *            the nested jar handler
     * @throws IOException
     *             if the file cannot be opened.
     */
    public PathSlice(
            final Path path,
            final boolean isDeflatedZipEntry,
            final long inflatedLengthHint,
            final NestedJarHandler nestedJarHandler)
            throws IOException {
        this(path, isDeflatedZipEntry, inflatedLengthHint, nestedJarHandler, true);
    }

    /**
     * Constructor for toplevel file slice.
     *
     * @param path
     *            the path
     * @param isDeflatedZipEntry
     *            true if this is a deflated zip entry
     * @param inflatedLengthHint
     *            the uncompressed size of a deflated zip entry, or -1 if unknown, or 0 of this is not a deflated
     *            zip entry.
     * @param nestedJarHandler
     *            the nested jar handler
     * @param checkAccess
     *            whether it is needed to check read access and if it is a file
     * @throws IOException
     *             if the file cannot be opened.
     */
    public PathSlice(
            final Path path,
            final boolean isDeflatedZipEntry,
            final long inflatedLengthHint,
            final NestedJarHandler nestedJarHandler,
            final boolean checkAccess)
            throws IOException {
        super(0L, isDeflatedZipEntry, inflatedLengthHint, nestedJarHandler);

        if (checkAccess) {
            // Make sure the File is readable and is a regular file
            FileUtils.checkCanReadAndIsFile(path);
        }

        this.path = path;
        this.fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileLength = fileChannel.size();
        this.isTopLevelFileSlice = true;

        // Had to use 0L for sliceLength in call to super, since FileChannel wasn't open
        // yet => update sliceLength
        this.sliceLength = fileLength;

        // Mark toplevel slice as open
        nestedJarHandler.markSliceAsOpen(this);
    }

    /**
     * Constructor for toplevel file slice.
     *
     * @param path
     *            the path
     * @param nestedJarHandler
     *            the nested jar handler
     * @throws IOException
     *             if the file cannot be opened.
     */
    public PathSlice(final Path path, final NestedJarHandler nestedJarHandler) throws IOException {
        this(path, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L, nestedJarHandler);
    }

    /**
     * Slice the file.
     *
     * @param offset
     *            the offset of the sub-slice within the parent slice
     * @param length
     *            the length of the sub-slice
     * @param isDeflatedZipEntry
     *            true if this is a deflated zip entry
     * @param inflatedLengthHint
     *            the uncompressed size of a deflated zip entry, or -1 if unknown, or 0 of this is not a deflated
     *            zip entry.
     * @return the slice
     */
    @Override
    public Slice slice(
            final long offset, final long length, final boolean isDeflatedZipEntry, final long inflatedLengthHint) {
        if (this.isDeflatedZipEntry) {
            throw new IllegalArgumentException("Cannot slice a deflated zip entry");
        }
        return new PathSlice(this, offset, length, isDeflatedZipEntry, inflatedLengthHint, nestedJarHandler);
    }

    /**
     * Read directly from FileChannel (slow path, but handles >2GB).
     *
     * @return the random access reader
     */
    @Override
    public RandomAccessReader randomAccessReader() {
        // Return a RandomAccessReader that uses the FileChannel
        return new RandomAccessFileChannelReader(fileChannel, sliceStartPos, sliceLength);
    }

    /**
     * Load the slice as a byte array.
     *
     * @return the byte[]
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    @Override
    public byte[] load() throws IOException {
        if (isDeflatedZipEntry) {
            // Inflate into RAM if deflated
            if (inflatedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("Uncompressed size is larger than 2GB");
            }
            try (InputStream inputStream = open()) {
                return NestedJarHandler.readAllBytesAsArray(inputStream, inflatedLengthHint);
            }
        } else {
            // Copy from FileChannel to byte array
            if (sliceLength > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("File is larger than 2GB");
            }
            final RandomAccessReader reader = randomAccessReader();
            final byte[] content = new byte[(int) sliceLength];
            if (reader.read(0, content, 0, content.length) < content.length) {
                // Should not happen
                throw new IOException("File is truncated");
            }
            return content;
        }
    }

    /**
     * Read the slice into a {@link ByteBuffer} (or memory-map the slice to a {@link MappedByteBuffer}, if
     * {@link ClassGraph#enableMemoryMapping()} was called.)
     *
     * @return the byte buffer
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    @Override
    public ByteBuffer read() throws IOException {
        if (isDeflatedZipEntry) {
            // Inflate to RAM if deflated (unfortunately there is no lazy-loading ByteBuffer
            // that will
            // decompress partial streams on demand, so we have to decompress the whole zip
            // entry)
            if (inflatedLengthHint > FileUtils.MAX_BUFFER_SIZE) {
                throw new IOException("Uncompressed size is larger than 2GB");
            }
            return ByteBuffer.wrap(load());
        }
        // Copy from FileChannel to byte array, then wrap in a ByteBuffer
        if (sliceLength > FileUtils.MAX_BUFFER_SIZE) {
            throw new IOException("File is larger than 2GB");
        }
        return ByteBuffer.wrap(load());
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /** Close the slice. Unmaps any backing {@link MappedByteBuffer}. */
    @Override
    public void close() {
        if (!isClosed.getAndSet(true)) {
            if (isTopLevelFileSlice && fileChannel != null) {
                // Only close the FileChannel in the toplevel file slice, so that it is only
                // closed once
                try {
                    // Closing raf will also close the associated FileChannel
                    fileChannel.close();
                } catch (final IOException e) {
                    // Ignore
                }
                fileChannel = null;
            }
            fileChannel = null;
            nestedJarHandler.markSliceAsClosed(this);
        }
    }
}
