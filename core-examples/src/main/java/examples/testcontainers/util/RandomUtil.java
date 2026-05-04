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

package examples.testcontainers.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {

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

    private RandomUtil() {
        // Intentionally empty
    }

    public static String getRandomString(final int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive, got: " + length);
        }

        var buffer = new char[length];
        final ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < length; i++) {
            buffer[i] = CHARS[random.nextInt(62)];
        }

        return new String(buffer);
    }
}
