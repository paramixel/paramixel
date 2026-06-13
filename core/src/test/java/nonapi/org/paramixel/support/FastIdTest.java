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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FastId")
class FastIdTest {

    @Test
    @DisplayName("generated IDs are unique")
    void generatedIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        int count = 100000;

        for (int i = 0; i < count; i++) {
            var id = FastId.generateId();
            assertThat(!ids.add(id)).isFalse();
        }
    }

    @Test
    @DisplayName("forbidden words are not generated")
    void forbiddenWordsAreNotGenerated() {
        String[] forbidden = {
            "stag", "succ", "fail", "skip", "STAG", "SUCC", "FAIL", "SKIP", "Stag", "Succ", "Fail", "Skip"
        };
        int count = 100000;

        for (int i = 0; i < count; i++) {
            var id = FastId.generateId();
            for (String word : forbidden) {
                assertThat(id).isNotEqualToIgnoringCase(word);
            }
        }
    }

    @Test
    @DisplayName("IDs do not start from predictable position")
    void idsDoNotStartFromPredictablePosition() {
        var firstId = FastId.generateId();
        assertThat(firstId).isNotEqualTo("aaaa");
    }

    @Test
    @DisplayName("consecutive IDs are not sequential")
    void consecutiveIdsAreNotSequence() {
        int count = 1000;
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = FastId.generateId();
        }

        int sequentialCount = 0;
        for (int i = 1; i < count; i++) {
            if (isSequence(ids[i - 1], ids[i])) {
                sequentialCount++;
            }
        }
        assertThat(sequentialCount).isLessThan(count / 100);
    }

    @Test
    @DisplayName("generated IDs have the requested length")
    void generatedIdsHaveRequestedLength() {
        assertThat(FastId.generateId(1)).hasSize(1).matches("[a-zA-Z]+");
        assertThat(FastId.generateId(4)).hasSize(4).matches("[a-zA-Z]+");
        assertThat(FastId.generateId(7)).hasSize(7).matches("[a-zA-Z]+");
        assertThat(FastId.generateId(8)).hasSize(8).matches("[a-zA-Z]+");
        assertThat(FastId.generateId(64)).hasSize(64).matches("[a-zA-Z]+");
    }

    @Test
    @DisplayName("throws on non-positive length")
    void throwsOnNonPositiveLength() {
        assertThatThrownBy(() -> FastId.generateId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FastId.generateId(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static boolean isSequence(final String prev, final String next) {
        long prevVal = decode(prev);
        long nextVal = decode(next);
        long diff = nextVal - prevVal;
        return diff == 1 || diff == -7311615L;
    }

    private static long decode(final String id) {
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
}
