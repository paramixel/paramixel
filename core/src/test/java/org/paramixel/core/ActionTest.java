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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;

@DisplayName("Action")
class ActionTest {

    @Test
    @DisplayName("creates noop actions that complete without doing work")
    void createsNoopActionsThatCompleteWithoutDoingWork() {
        Action action = Noop.of("noop");

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("returns unmodifiable children and links each child to its parent")
    void returnsUnmodifiableChildrenAndLinksEachChildToItsParent() {
        Action first = Noop.of("first");
        Action second = Noop.of("second");
        Action root = Sequential.of("root", first, second);

        assertThat(root.getChildren()).containsExactly(first, second);
        assertThat(first.getParent()).contains(root);
        assertThat(second.getParent()).contains(root);
        assertThatThrownBy(() -> root.getChildren().remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }
}
