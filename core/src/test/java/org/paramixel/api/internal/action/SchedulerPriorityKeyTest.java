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

package org.paramixel.api.internal.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
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
}
