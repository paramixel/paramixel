/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package examples.testcontainers.util;

import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;

/**
 * Fast ID generator for alphanumeric strings.
 *
 * <p>This class generates random alphanumeric IDs optimized for performance
 * in high-throughput scenarios. It uses {@link ThreadLocalRandom} for
 * concurrent access without synchronization overhead.</p>
 *
 * <p>The generated IDs contain:
 * <ul>
 *   <li>Uppercase letters A-Z</li>
 *   <li>Lowercase letters a-z</li>
 *   <li>Digits 0-9</li>
 * </ul>
 *
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li>No synchronization overhead (uses thread-local random)</li>
 *   <li>Pre-computed character array for O(1) lookup</li>
 *   <li>Single array allocation per ID generation</li>
 *   <li>No String concatenation in loops</li>
 * </ul>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * String id = FastId.getId(16);  // Generates 16-character ID
 * }</pre>
 *
 * @see ThreadLocalRandom
 */
public final class RandomUtil {

    /**
     * Character set containing all valid ID characters.
     * Array indexes 0-61 map to: 0-9, A-Z, a-z
     */
    private static final char[] CHARS = new char[62];

    static {
        int idx = 0;
        // Digits 0-9
        for (char c = '0'; c <= '9'; c++) {
            CHARS[idx++] = c;
        }
        // Uppercase A-Z
        for (char c = 'A'; c <= 'Z'; c++) {
            CHARS[idx++] = c;
        }
        // Lowercase a-z
        for (char c = 'a'; c <= 'z'; c++) {
            CHARS[idx++] = c;
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private RandomUtil() {
        // Utility class
    }

    /**
     * Generates a random alphanumeric ID of the specified length.
     *
     * <p>The generated ID will contain only:
     * <ul>
     *   <li>Uppercase letters (A-Z)</li>
     *   <li>Lowercase letters (a-z)</li>
     *   <li>Digits (0-9)</li>
     * </ul>
     *
     * <p>This method is thread-safe and optimized for concurrent access.</p>
     *
     * @param length the length of the ID to generate (must be positive)
     * @return a random alphanumeric ID of the specified length
     * @throws IllegalArgumentException if length is not positive
     */
    public static String getRandomString(final @NonNull int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive, got: " + length);
        }

        final char[] buffer = new char[length];
        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < length; i++) {
            buffer[i] = CHARS[random.nextInt(62)];
        }

        return new String(buffer);
    }
}
