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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.recycler;

/**
 * An AutoCloseable wrapper for a recyclable object instance. Obtained by calling
 * {@link Recycler#acquireRecycleOnClose()} in a try-with-resources statement, so that when the try block exits, the
 * acquired instance is recycled.
 *
 * @param <T>
 *            the type to recycle
 * @param <E>
 *            the exception type that may be thrown when a recyclable item is acquired.
 */
public class RecycleOnClose<T, E extends Exception> implements AutoCloseable {
    /** The recycler. */
    private final Recycler<T, E> recycler;

    /** The instance. */
    private final T instance;

    /**
     * Acquire or allocate an instance.
     *
     * @param recycler
     *            The {@link Recycler}.
     * @param instance
     *            An object instance that was obtained by calling {@link Recycler#acquire()} on the recycler.
     * @throws IllegalArgumentException
     *             If {@link Recycler#newInstance()} returned null.
     */
    RecycleOnClose(final Recycler<T, E> recycler, final T instance) {
        this.recycler = recycler;
        this.instance = instance;
    }

    /**
     * Get the object instance.
     *
     * @return The object instance.
     */
    public T get() {
        return instance;
    }

    /** Recycle an instance. Calls {@link Resettable#reset()} if the instance implements {@link Resettable}. */
    @Override
    public void close() {
        recycler.recycle(instance);
    }
}
