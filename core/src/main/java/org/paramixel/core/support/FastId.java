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
 * High-performance unique ID generator producing 4-character strings from a-z and A-Z.
 *
 * <p>Uses a full-period Linear Congruential Generator (LCG) to permute the ID space,
 * ensuring uniqueness while producing unpredictable, well-distributed IDs.
 * Avoids IDs that match (case-insensitive): PASS, FAIL, SKIP, STAG
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
     * Generates a unique 4-character ID from a-z and A-Z.
     * Never returns IDs matching (case-insensitive): PASS, FAIL, SKIP, STAG
     * @return a unique 4-character ID
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

    /**
     * Encodes a number as a base-52 string using the character set.
     */
    private static String encode(long num) {
        char[] result = new char[4];
        for (int i = 3; i >= 0; i--) {
            result[i] = CHARSET.charAt((int) (num % CHARSET_SIZE));
            num /= CHARSET_SIZE;
        }
        return new String(result);
    }

    /**
     * Checks if the ID contains any forbidden substrings (case-insensitive).
     */
    private static boolean isForbidden(String id) {
        String lower = id.toLowerCase();
        for (String forbidden : FORBIDDEN) {
            if (lower.equals(forbidden)) {
                return true;
            }
        }
        return false;
    }
}
