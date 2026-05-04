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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FastIdTest {

    @Test
    @DisplayName("Test uniqueness of generated IDs")
    void testUniqueness() {
        Set<String> ids = new HashSet<>();
        int count = 100000;

        for (int i = 0; i < count; i++) {
            String id = FastId.generateId();
            assertThat(!ids.add(id)).isFalse();
        }
    }

    @Test
    @DisplayName("Test that forbidden words are not generated")
    void testForbiddenWords() {
        String[] forbidden = {
            "stag", "pass", "fail", "skip", "STAG", "PASS", "FAIL", "SKIP", "Stag", "Pass", "Fail", "Skip"
        };
        int count = 100000;

        for (int i = 0; i < count; i++) {
            String id = FastId.generateId();
            for (String word : forbidden) {
                assertThat(id).isNotEqualToIgnoringCase(word);
            }
        }
    }

    @Test
    @DisplayName("Test that IDs do not start from predictable position")
    void testRandomStartPosition() {
        String firstId = FastId.generateId();
        assertThat(firstId).isNotEqualTo("aaaa");
    }

    @Test
    @DisplayName("Test that consecutive IDs are not sequential")
    void testNonSequentialDistribution() {
        int count = 1000;
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = FastId.generateId();
        }

        int sequentialCount = 0;
        for (int i = 1; i < count; i++) {
            if (isSequential(ids[i - 1], ids[i])) {
                sequentialCount++;
            }
        }
        assertThat(sequentialCount).isLessThan(count / 100);
    }

    private static boolean isSequential(String prev, String next) {
        long prevVal = decode(prev);
        long nextVal = decode(next);
        long diff = nextVal - prevVal;
        return diff == 1 || diff == -7311615L;
    }

    private static long decode(String id) {
        long result = 0;
        for (int i = 0; i < 4; i++) {
            char c = id.charAt(i);
            if (c >= 'a' && c <= 'z') {
                result = result * 52 + (c - 'a');
            } else {
                result = result * 52 + (c - 'A' + 26);
            }
        }
        return result;
    }

    /*
    static void testPerformance() {
        System.out.println("\nTesting performance...");
        int count = 1000000;

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            FastId.generateId();
        }
        long duration = System.nanoTime() - start;

        double opsPerSecond = (count / (duration / 1_000_000_000.0));
        System.out.printf("PASS: Generated %,d IDs in %.2f ms (%.0f ops/sec)%n",
                count, duration / 1_000_000.0, opsPerSecond);
    }
    */
}
