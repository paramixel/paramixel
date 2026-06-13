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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SchedulerPriorityKey")
class SchedulerPriorityKeyTest {

    @Test
    @DisplayName("continuation-first ordering prefers deeper prefix paths")
    void continuationFirstOrderingPrefersDeeperPrefixPaths() {
        var parent = SchedulerPriorityKey.root().child(0);
        var continuation = parent.child(0);

        assertThat(continuation.compareTo(parent)).isLessThan(0);
        assertThat(parent.compareTo(continuation)).isGreaterThan(0);
    }

    @Test
    @DisplayName("lexicographic ordering compares sibling indices")
    void lexicographicOrderingComparesSiblingIndices() {
        var left = SchedulerPriorityKey.root().child(0).child(2);
        var right = SchedulerPriorityKey.root().child(1);

        assertThat(left.compareTo(right)).isLessThan(0);
        assertThat(right.compareTo(left)).isGreaterThan(0);
    }

    @Test
    @DisplayName("child rejects negative index")
    void childRejectsNegativeIndex() {
        assertThatThrownBy(() -> SchedulerPriorityKey.root().child(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("childIndex must be non-negative");
    }

    @Nested
    @DisplayName("equals")
    class Equals {

        @Test
        @DisplayName("root key is reflexively equal")
        void rootKeyIsReflexivelyEqual() {
            var root = SchedulerPriorityKey.root();
            assertThat(root.equals(root)).isTrue();
        }

        @Test
        @DisplayName("identical child paths are equal")
        void identicalChildPathsAreEqual() {
            var a = SchedulerPriorityKey.root().child(0).child(1);
            var b = SchedulerPriorityKey.root().child(0).child(1);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("different child indices are not equal")
        void differentChildIndicesAreNotEqual() {
            var a = SchedulerPriorityKey.root().child(0);
            var b = SchedulerPriorityKey.root().child(1);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different depth paths are not equal")
        void differentDepthPathsAreNotEqual() {
            var a = SchedulerPriorityKey.root().child(0);
            var b = SchedulerPriorityKey.root().child(0).child(0);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("not equal to different type")
        void notEqualToDifferentType() {
            var key = SchedulerPriorityKey.root().child(0);
            assertThat(key).isNotEqualTo("not a key");
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() {
            var key = SchedulerPriorityKey.root().child(0);
            assertThat(key).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("hashCode")
    class HashCode {

        @Test
        @DisplayName("equal keys have equal hash codes")
        void equalKeysHaveEqualHashCodes() {
            var a = SchedulerPriorityKey.root().child(0).child(2);
            var b = SchedulerPriorityKey.root().child(0).child(2);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different paths have different hash codes")
        void differentPathsHaveDifferentHashCodes() {
            var a = SchedulerPriorityKey.root().child(0);
            var b = SchedulerPriorityKey.root().child(1);
            assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        }
    }

    @Test
    @DisplayName("child cache returns same cached instance under concurrent access")
    void childCacheReturnsSameInstanceUnderConcurrentAccess() throws Exception {
        var parent = SchedulerPriorityKey.root();
        var threadCount = 8;
        var latch = new CountDownLatch(1);
        var resultsByIndex = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<SchedulerPriorityKey>>();
        for (var i = 0; i < 4; i++) {
            resultsByIndex.put(i, new ConcurrentLinkedQueue<>());
        }
        var threads = new Thread[threadCount];

        for (var i = 0; i < threadCount; i++) {
            var index = i % 4; // use only indices 0–3 to force contention
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                    resultsByIndex.get(index).add(parent.child(index));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        latch.countDown();
        for (var thread : threads) {
            thread.join();
        }

        // After the fix: every call to child(index) returns the same reference
        for (var i = 0; i < 4; i++) {
            var expected = parent.child(i);
            assertThat(resultsByIndex.get(i))
                    .as("all concurrent child(" + i + ") calls return same reference")
                    .allMatch(key -> key == expected);
        }
    }
}
