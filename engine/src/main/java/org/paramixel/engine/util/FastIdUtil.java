/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast ID generator for alphanumeric strings that guarantees generated IDs
 * never contain restricted words (case-insensitive).
 *
 * <p>Restricted substrings:</p>
 * <ul>
 *   <li>PASS</li>
 *   <li>TEST</li>
 *   <li>FAIL</li>
 *   <li>SKIP</li>
 * </ul>
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class FastIdUtil {

    /**
     * Character set containing all valid ID characters.
     */
    private static final char[] CHARS = new char[62];

    /**
     * Forbidden words (uppercase).
     */
    private static final String[] FORBIDDEN = {"PASS", "TEST", "FAIL", "SKIP"};

    static {
        int idx = 0;

        for (char c = '0'; c <= '9'; c++) {
            CHARS[idx++] = c;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            CHARS[idx++] = c;
        }
        for (char c = 'a'; c <= 'z'; c++) {
            CHARS[idx++] = c;
        }
    }

    /**
     * Prevents instantiation of this utility class.
     *
     * <p>This class exposes only static methods.
     *
     * @since 0.0.1
     */
    private FastIdUtil() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Generates a random alphanumeric ID that does not contain
     * restricted substrings (case-insensitive).
     *
     * @param length ID length (must be positive)
     * @return valid random ID
     * @throws IllegalArgumentException if {@code length <= 0}
     * @since 0.0.1
     */
    public static String getId(final int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive, got: " + length);
        }

        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final char[] buffer = new char[length];

        // retry loop (almost never repeats)
        while (true) {

            for (int i = 0; i < length; i++) {
                buffer[i] = CHARS[random.nextInt(62)];
            }

            if (!containsForbidden(buffer)) {
                return new String(buffer);
            }
        }
    }

    /**
     * Checks a buffer for forbidden substrings.
     *
     * <p>This method performs allocation-free ASCII case-insensitive matching.
     *
     * @param buffer the candidate id buffer; never {@code null}
     * @return {@code true} when any forbidden word occurs as a contiguous substring
     * @since 0.0.1
     */
    private static boolean containsForbidden(final char[] buffer) {
        final int len = buffer.length;

        for (String word : FORBIDDEN) {
            final int wlen = word.length();

            if (wlen > len) {
                continue;
            }

            for (int i = 0; i <= len - wlen; i++) {
                boolean match = true;

                for (int j = 0; j < wlen; j++) {
                    char c = buffer[i + j];

                    // fast ASCII uppercase conversion
                    if (c >= 'a' && c <= 'z') {
                        c -= 32;
                    }

                    if (c != word.charAt(j)) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    return true;
                }
            }
        }

        return false;
    }
}
