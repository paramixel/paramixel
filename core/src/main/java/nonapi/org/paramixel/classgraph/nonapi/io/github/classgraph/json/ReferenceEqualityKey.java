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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.json;

/**
 * An object for wrapping a HashMap key so that the hashmap performs reference equality on the keys, not equals()
 * equality.
 *
 * @param <K>
 *            the key type
 */
public class ReferenceEqualityKey<K> {

    /** The wrapped key. */
    private final K wrappedKey;

    /**
     * Constructor.
     *
     * @param wrappedKey
     *            the wrapped key
     */
    public ReferenceEqualityKey(final K wrappedKey) {
        this.wrappedKey = wrappedKey;
    }

    /**
     * Get the wrapped key.
     *
     * @return the wrapped key.
     */
    public K get() {
        return wrappedKey;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final K key = wrappedKey;
        // Don't call key.hashCode(), because that can be an expensive (deep) hashing method,
        // e.g. for ByteBuffer, it is based on the entire contents of the buffer
        return key == null ? 0 : System.identityHashCode(key);
    }

    /**
     * Equals.
     *
     * @param obj
     *            the obj
     * @return true, if successful
     */
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ReferenceEqualityKey)) {
            return false;
        }
        return wrappedKey == ((ReferenceEqualityKey<?>) obj).wrappedKey;
    }

    /**
     * To string.
     *
     * @return the string
     */
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final K key = wrappedKey;
        return key == null ? "null" : key.toString();
    }
}
