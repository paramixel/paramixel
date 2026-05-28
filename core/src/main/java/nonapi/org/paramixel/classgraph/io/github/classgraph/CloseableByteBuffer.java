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
import java.nio.ByteBuffer;

/**
 * A wrapper for {@link ByteBuffer} that implements the {@link Closeable} interface, releasing the
 * {@link ByteBuffer} when it is no longer needed.
 */
public class CloseableByteBuffer implements Closeable {
    private ByteBuffer byteBuffer;
    private Runnable onClose;

    /**
     * A wrapper for {@link ByteBuffer} that implements the {@link Closeable} interface, releasing the
     * {@link ByteBuffer} when it is no longer needed.
     *
     * @param byteBuffer
     *            The {@link ByteBuffer} to wrap
     * @param onClose
     *            The method to run when {@link #close()} is called.
     */
    CloseableByteBuffer(final ByteBuffer byteBuffer, final Runnable onClose) {
        this.byteBuffer = byteBuffer;
        this.onClose = onClose;
    }

    /**
     * Get the wrapped ByteBuffer.
     *
     * @return The wrapped {@link ByteBuffer}.
     */
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /** Release the wrapped {@link ByteBuffer}. */
    @Override
    public void close() throws IOException {
        if (onClose != null) {
            try {
                onClose.run();
            } catch (final Exception e) {
                // Ignore
            }
            onClose = null;
        }
        byteBuffer = null;
    }
}
