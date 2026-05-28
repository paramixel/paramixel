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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fastzipfilereader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.ArraySlice;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.FileSlice;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.PathSlice;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.fileslice.Slice;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.FastPathResolver;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.FileUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** A physical zipfile, which is mmap'd using a {@link FileChannel}. */
class PhysicalZipFile {
    /** The {@link Path} backing this {@link PhysicalZipFile}, if any. */
    private Path path;

    /** The {@link File} backing this {@link PhysicalZipFile}, if any. */
    private File file;

    /** The path to the zipfile. */
    private final String pathStr;

    /** The {@link Slice} for the zipfile. */
    Slice slice;

    /** The nested jar handler. */
    NestedJarHandler nestedJarHandler;

    /** The cached hashCode. */
    private int hashCode;

    /**
     * Construct a {@link PhysicalZipFile} from a file on disk.
     *
     * @param file
     *            the file
     * @param nestedJarHandler
     *            the nested jar handler
     * @param log
     *            the log
     * @throws IOException
     *             if an I/O exception occurs.
     */
    PhysicalZipFile(final File file, final NestedJarHandler nestedJarHandler, final LogNode log) throws IOException {
        this.nestedJarHandler = nestedJarHandler;
        this.file = file;
        this.pathStr = FastPathResolver.resolve(FileUtils.currDirPath(), file.getPath());
        this.slice = new FileSlice(file, nestedJarHandler, log);
    }

    /**
     * Construct a {@link PhysicalZipFile} from a {@link Path}.
     *
     * @param path
     *            the path
     * @param nestedJarHandler
     *            the nested jar handler
     * @param log
     *            the log
     * @throws IOException
     *             if an I/O exception occurs.
     */
    PhysicalZipFile(final Path path, final NestedJarHandler nestedJarHandler, final LogNode log) throws IOException {
        this.nestedJarHandler = nestedJarHandler;
        this.path = path;
        this.pathStr = FastPathResolver.resolve(FileUtils.currDirPath(), path.toString());
        this.slice = new PathSlice(path, nestedJarHandler);
    }

    /**
     * Construct a {@link PhysicalZipFile} from a byte array.
     *
     * @param arr
     *            the array containing the zipfile.
     * @param outermostFile
     *            the outermost file
     * @param pathStr
     *            the path
     * @param nestedJarHandler
     *            the nested jar handler
     * @throws IOException
     *             if an I/O exception occurs.
     */
    PhysicalZipFile(
            final byte[] arr, final File outermostFile, final String pathStr, final NestedJarHandler nestedJarHandler)
            throws IOException {
        this.nestedJarHandler = nestedJarHandler;
        this.file = outermostFile;
        this.pathStr = pathStr;
        this.slice =
                new ArraySlice(arr, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L, nestedJarHandler);
    }

    /**
     * Construct a {@link PhysicalZipFile} by reading from the {@link InputStream} to an array in RAM, or spill to
     * disk if the {@link InputStream} is too long.
     *
     * @param inputStream
     *            the input stream
     * @param inputStreamLengthHint
     *            The number of bytes to read in inputStream, or -1 if unknown.
     * @param pathStr
     *            the source URL the InputStream was opened from, or the zip entry path of this entry in the parent
     *            zipfile
     * @param nestedJarHandler
     *            the nested jar handler
     * @param log
     *            the log
     * @throws IOException
     *             if an I/O exception occurs.
     */
    PhysicalZipFile(
            final InputStream inputStream,
            final long inputStreamLengthHint,
            final String pathStr,
            final NestedJarHandler nestedJarHandler,
            final LogNode log)
            throws IOException {
        this.nestedJarHandler = nestedJarHandler;
        this.pathStr = pathStr;
        // Try downloading the InputStream to a byte array. If this succeeds, this will result in an ArraySlice.
        // If it fails, the InputStream will be spilled to disk, resulting in a FileSlice.
        this.slice = nestedJarHandler.readAllBytesWithSpilloverToDisk(
                inputStream, /* tempFileBaseName = */ pathStr, inputStreamLengthHint, log);
        this.file = this.slice instanceof FileSlice ? ((FileSlice) this.slice).file : null;
    }

    /**
     * Get the {@link Path} for the outermost jar file of this PhysicalZipFile.
     *
     * @return the {@link Path} for the outermost jar file of this PhysicalZipFile, or null if this file was
     *         downloaded from a URL directly to RAM, or is backed by a {@link File}.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Get the {@link File} for the outermost jar file of this PhysicalZipFile.
     *
     * @return the {@link File} for the outermost jar file of this PhysicalZipFile, or null if this file was
     *         downloaded from a URL directly to RAM, or is backed by a {@link Path}.
     */
    public File getFile() {
        return file;
    }

    /**
     * Get the path for this PhysicalZipFile, which is the file path, if it is file-backed, or a compound nested jar
     * path, if it is memory-backed.
     *
     * @return the path for this PhysicalZipFile, which is the file path, if it is file-backed, or a compound nested
     *         jar path, if it is memory-backed.
     */
    public String getPathStr() {
        return pathStr;
    }

    /**
     * Get the length of the mapped file, or the initial remaining bytes in the wrapped ByteBuffer if a buffer was
     * wrapped.
     *
     * @return the length of the mapped file
     */
    public long length() {
        return slice.sliceLength;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = (file == null ? 0 : file.hashCode());
            if (hashCode == 0) {
                hashCode = 1;
            }
        }
        return hashCode;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PhysicalZipFile)) {
            return false;
        }
        final PhysicalZipFile other = (PhysicalZipFile) o;
        return Objects.equals(file, other.file);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return pathStr;
    }
}
