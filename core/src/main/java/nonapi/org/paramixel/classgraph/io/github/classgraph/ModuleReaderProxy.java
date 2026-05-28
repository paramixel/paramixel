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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;

/** A ModuleReader proxy, written using reflection to preserve backwards compatibility with JDK 7 and 8. */
public class ModuleReaderProxy implements Closeable {
    /** The module reader. */
    private final AutoCloseable moduleReader;

    /** Class<Collector> collectorClass = Class.forName("java.util.stream.Collector"); */
    private static Class<?> collectorClass;

    /** Collector<Object, ?, List<Object>> collectorsToList = Collectors.toList(); */
    private static Object collectorsToList;

    private ReflectionUtils reflectionUtils;

    /**
     * Constructor.
     *
     * @param moduleRef
     *            the module ref
     * @throws IOException
     *             If an I/O exception occurs.
     */
    ModuleReaderProxy(final ModuleRef moduleRef) throws IOException {
        try {
            reflectionUtils = moduleRef.reflectionUtils;
            if (collectorClass == null || collectorsToList == null) {
                collectorClass = reflectionUtils.classForNameOrNull("java.util.stream.Collector");
                final Class<?> collectorsClass = reflectionUtils.classForNameOrNull("java.util.stream.Collectors");
                if (collectorsClass != null) {
                    collectorsToList =
                            reflectionUtils.invokeStaticMethod(/* throwException = */ true, collectorsClass, "toList");
                }
            }
            moduleReader = (AutoCloseable)
                    reflectionUtils.invokeMethod(/* throwException = */ true, moduleRef.getReference(), "open");
            if (moduleReader == null) {
                throw new IllegalArgumentException("moduleReference.open() should not return null");
            }
        } catch (final SecurityException e) {
            throw new IOException("Could not open module " + moduleRef.getName(), e);
        }
    }

    /** Calls ModuleReader#close(). */
    @Override
    public void close() {
        try {
            moduleReader.close();
        } catch (final Exception e) {
            // Ignore
        }
    }

    /**
     * Get the list of resources accessible to a ModuleReader.
     *
     * From the documentation for ModuleReader#list(): "Whether the stream of elements includes names corresponding
     * to directories in the module is module reader specific. In lazy implementations then an IOException may be
     * thrown when using the stream to list the module contents. If this occurs then the IOException will be wrapped
     * in an java.io.UncheckedIOException and thrown from the method that caused the access to be attempted.
     * SecurityException may also be thrown when using the stream to list the module contents and access is denied
     * by the security manager."
     *
     * @return A list of the paths of resources in the module.
     * @throws SecurityException
     *             If the module cannot be accessed.
     */
    public List<String> list() throws SecurityException {
        if (collectorsToList == null) {
            throw new IllegalArgumentException("Could not call Collectors.toList()");
        }
        final Object /* Stream<String> */ resourcesStream =
                reflectionUtils.invokeMethod(/* throwException = */ true, moduleReader, "list");
        if (resourcesStream == null) {
            throw new IllegalArgumentException("Could not call moduleReader.list()");
        }
        final Object resourcesList = reflectionUtils.invokeMethod(
                /* throwException = */ true, resourcesStream, "collect", collectorClass, collectorsToList);
        if (resourcesList == null) {
            throw new IllegalArgumentException("Could not call moduleReader.list().collect(Collectors.toList())");
        }
        @SuppressWarnings("unchecked")
        final List<String> resourcesListTyped = (List<String>) resourcesList;
        return resourcesListTyped;
    }

    /**
     * Use the proxied ModuleReader to open the named resource as an InputStream.
     *
     * @param path
     *            The path to the resource to open.
     *
     * @return An {@link InputStream} for the content of the resource.
     * @throws SecurityException
     *             If the module cannot be accessed.
     * @throws IllegalArgumentException
     *             If the module cannot be accessed.
     */
    public InputStream open(final String path) throws SecurityException {
        final Object /* Optional<InputStream> */ optionalInputStream =
                reflectionUtils.invokeMethod(/* throwException = */ true, moduleReader, "open", String.class, path);
        if (optionalInputStream == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#open for path " + path);
        }
        final InputStream inputStream =
                (InputStream) reflectionUtils.invokeMethod(/* throwException = */ true, optionalInputStream, "get");
        if (inputStream == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#open(String)#get()");
        }
        return inputStream;
    }

    /**
     * Use the proxied ModuleReader to open the named resource as a ByteBuffer. Call {@link #release(ByteBuffer)}
     * when you have finished with the ByteBuffer.
     *
     * @param path
     *            The path to the resource to open.
     * @return A {@link ByteBuffer} for the content of the resource.
     * @throws SecurityException
     *             If the module cannot be accessed.
     * @throws OutOfMemoryError
     *             if the resource is larger than 2GB, the maximum capacity of a byte buffer.
     */
    public ByteBuffer read(final String path) throws SecurityException, OutOfMemoryError {
        final Object /* Optional<ByteBuffer> */ optionalByteBuffer =
                reflectionUtils.invokeMethod(/* throwException = */ true, moduleReader, "read", String.class, path);
        if (optionalByteBuffer == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#read(String)");
        }
        final ByteBuffer byteBuffer =
                (ByteBuffer) reflectionUtils.invokeMethod(/* throwException = */ true, optionalByteBuffer, "get");
        if (byteBuffer == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#read(String).get()");
        }
        return byteBuffer;
    }

    /**
     * Release a {@link ByteBuffer} allocated by calling {@link #read(String)}.
     *
     * @param byteBuffer
     *            The {@link ByteBuffer} to release.
     */
    public void release(final ByteBuffer byteBuffer) {
        reflectionUtils.invokeMethod(
                /* throwException = */ true, moduleReader, "release", ByteBuffer.class, byteBuffer);
    }

    /**
     * Use the proxied ModuleReader to find the named resource as a URI.
     *
     * @param path
     *            The path to the resource to open.
     * @return A {@link URI} for the resource.
     * @throws SecurityException
     *             If the module cannot be accessed.
     */
    public URI find(final String path) {
        final Object /* Optional<URI> */ optionalURI =
                reflectionUtils.invokeMethod(/* throwException = */ true, moduleReader, "find", String.class, path);
        if (optionalURI == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#find(String)");
        }
        final URI uri = (URI) reflectionUtils.invokeMethod(/* throwException = */ true, optionalURI, "get");
        if (uri == null) {
            throw new IllegalArgumentException("Got null result from ModuleReader#find(String).get()");
        }
        return uri;
    }
}
