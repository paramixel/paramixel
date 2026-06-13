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

package nonapi.org.paramixel.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Short pseudo-random identifier generator for Paramixel actions.
 *
 * <p>Produces four-character alphabetic identifiers that avoid a small set of reserved words
 * ({@code "stag"}, {@code "succ"}, {@code "fail"}, {@code "skip"}) and their case variants.
 * Identifiers do not repeat until approximately 7.3&nbsp;million have been generated.
 *
 * <p>Internally, a full-period linear congruential generator (LCG) traverses all
 * {@code 52⁴ = 7,311,616} possible identifiers exactly once before cycling. Of those,
 * 64 case-insensitive variants of the four reserved words are filtered out. The probability
 * of a retry is {@code 64 / 7,311,616 ≈ 0.000875%}, or roughly one retry per 114,000
 * generated identifiers.
 *
 * <p>This is a utility class with a private constructor; use {@link #generateId()} to obtain
 * identifiers. The {@code generateId} method is thread-safe.
 */
public class FastId {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int CHARSET_SIZE = 52;

    private static final long MAX_IDS = 7311616L; // 52^4

    private static final long MULTIPLIER = 4518593L;

    private static final long INCREMENT = 1L;

    /**
     * Reserved words (lowercase) that must not appear as generated identifiers.
     */
    private static final String[] FORBIDDEN = {"stag", "succ", "fail", "skip"};

    /**
     * Current LCG state, initialized to a random offset and advanced atomically per call.
     */
    private static final AtomicLong counter =
            new AtomicLong(ThreadLocalRandom.current().nextLong(MAX_IDS));

    /**
     * Prevents instantiation; this is a utility class.
     */
    private FastId() {
        // Intentionally empty
    }

    /**
     * Returns the next four-character identifier in the LCG sequence, retrying if the result
     * matches a reserved word.
     *
     * <p>Retry is extremely rare (approximately once per 114,000 calls). The internal counter is
     * advanced atomically, so this method is safe for concurrent use.
     *
     * @return a four-character identifier that does not match any reserved word; never {@code null}
     */
    public static String generateId() {
        return generateId(4);
    }

    /**
     * Returns an identifier of the requested length generated from one or more LCG
     * four-character chunks.
     *
     * <p>When the requested length is not a multiple of four, the output is truncated from a
     * ceiling-length LCG sequence; when it is a multiple of four, the output is the exact
     * concatenation. For lengths other than four, no reserved-word checking is performed on
     * the truncated portions.
     *
     * @param length the desired identifier length; must be positive
     * @return an identifier of the requested length; never {@code null}
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    public static String generateId(final int length) {
        Arguments.requirePositive(length, "length must be positive, was: " + length);

        if (length == 4) {
            return generateLcgChunk();
        }

        int chunks = (length + 3) / 4;
        var stringBuilder = new StringBuilder(length);
        for (int i = 0; i < chunks; i++) {
            stringBuilder.append(generateLcgChunk());
        }
        return stringBuilder.substring(0, length);
    }

    /**
     * Generates a single four-character LCG chunk, retrying on reserved words.
     *
     * @return a four-character identifier that does not match any reserved word; never {@code null}
     */
    private static String generateLcgChunk() {
        while (true) {
            long num = counter.getAndUpdate(n -> (n * MULTIPLIER + INCREMENT) % MAX_IDS);
            var id = encode(num);
            if (!isForbidden(id)) {
                return id;
            }
        }
    }

    private static String encode(long num) {
        char[] result = new char[4];
        for (int i = 3; i >= 0; i--) {
            result[i] = CHARSET.charAt((int) (num % CHARSET_SIZE));
            num /= CHARSET_SIZE;
        }
        return String.valueOf(result);
    }

    private static boolean isForbidden(final String id) {
        for (String forbidden : FORBIDDEN) {
            if (equalsIgnoreCase(id, forbidden)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsIgnoreCase(final String a, final String b) {
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (Character.toLowerCase(a.charAt(i)) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
