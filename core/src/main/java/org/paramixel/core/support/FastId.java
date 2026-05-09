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

package org.paramixel.core.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates short pseudo-random-looking identifiers for Paramixel actions.
 *
 * <p>The generated identifiers use a four-character alphabetic encoding and avoid a small set of reserved words that
 * would be confusing in console output.
 *
 * <p>Internally, a full-period linear congruential generator (LCG) traverses all {@code 52⁴ = 7,311,616} possible
 * identifiers exactly once before cycling. Of those, 64 case-insensitive variants of four reserved words
 * ({@code "stag"}, {@code "pass"}, {@code "fail"}, {@code "skip"}) are filtered out. The probability of a retry is
 * {@code 64 / 7,311,616 ≈ 0.000875%}, or roughly one retry per 114,000 generated identifiers. For any realistic test
 * suite, at most one retry ever occurs. Identifiers cannot repeat within a JVM lifecycle.
 */
public class FastId {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int CHARSET_SIZE = 52;

    private static final long MAX_IDS = 7311616L; // 52^4

    private static final long MULTIPLIER = 4518593L;

    private static final long INCREMENT = 1L;

    private static final String[] FORBIDDEN = {"stag", "pass", "fail", "skip"};

    private static final AtomicLong counter =
            new AtomicLong(ThreadLocalRandom.current().nextLong(MAX_IDS));

    private FastId() {}

    /**
     * Generates the next identifier.
     *
     * @return a four-character identifier that is not in the reserved-word list
     */
    public static String generateId() {
        while (true) {
            long num = counter.getAndUpdate(n -> (n * MULTIPLIER + INCREMENT) % MAX_IDS);
            String id = encode(num);
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
        return new String(result);
    }

    private static boolean isForbidden(String id) {
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        for (String forbidden : FORBIDDEN) {
            if (lower.equals(forbidden)) {
                return true;
            }
        }
        return false;
    }
}
