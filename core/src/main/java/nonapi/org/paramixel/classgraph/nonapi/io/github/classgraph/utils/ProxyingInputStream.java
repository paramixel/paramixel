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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * A proxying {@link InputStream} implementation that compiles for JDK 7 but can support the methods added in JDK 8
 * by reflection.
 */
public class ProxyingInputStream extends InputStream {
    private InputStream inputStream;

    private static Method readAllBytes;
    private static Method readNBytes1;
    private static Method readNBytes3;
    private static Method skipNBytes;
    private static Method transferTo;

    static {
        // Use reflection for InputStream methods not present in JDK 7.
        // TODO Switch to direct method calls once JDK 8 is required, and add back missing @Override annotations
        try {
            readAllBytes = InputStream.class.getDeclaredMethod("readAllBytes");
        } catch (NoSuchMethodException | SecurityException e1) {
            // Ignore
        }
        try {
            readNBytes1 = InputStream.class.getDeclaredMethod("readNBytes", int.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // Ignore
        }
        try {
            readNBytes3 = InputStream.class.getDeclaredMethod("readNBytes", byte[].class, int.class, int.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // Ignore
        }
        try {
            skipNBytes = InputStream.class.getDeclaredMethod("skipNBytes", long.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // Ignore
        }
        try {
            transferTo = InputStream.class.getDeclaredMethod("transferTo", OutputStream.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // Ignore
        }
    }

    /**
     * A proxying {@link InputStream} implementation that compiles for JDK 7 but can support the methods added in
     * JDK 8 by reflection.
     *
     * @param inputStream
     *            the {@link InputStream} to wrap.
     */
    public ProxyingInputStream(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return inputStream.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    // No @Override, since this method is not present in JDK 7
    public byte[] readAllBytes() throws IOException {
        if (readAllBytes == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (byte[]) readAllBytes.invoke(inputStream);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // No @Override, since this method is not present in JDK 7
    public byte[] readNBytes(final int len) throws IOException {
        if (readNBytes1 == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (byte[]) readNBytes1.invoke(inputStream, len);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // No @Override, since this method is not present in JDK 7
    public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
        if (readNBytes3 == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (int) readNBytes3.invoke(inputStream, b, off, len);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        return inputStream.skip(n);
    }

    // No @Override, since this method is not present in JDK 7
    public void skipNBytes(final long n) throws IOException {
        if (skipNBytes == null) {
            throw new UnsupportedOperationException();
        }
        try {
            skipNBytes.invoke(inputStream, n);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // No @Override, since this method is not present in JDK 7
    public long transferTo(final OutputStream out) throws IOException {
        if (transferTo == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (long) transferTo.invoke(inputStream, out);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String toString() {
        return inputStream.toString();
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            try {
                inputStream.close();
            } finally {
                inputStream = null;
            }
        }
    }
}
