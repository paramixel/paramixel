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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;
import org.paramixel.api.selector.Selector;

@DisplayName("No-discovery run returns empty result")
class NoDiscoveryRunReturnsNullTest {

    @Test
    @DisplayName("selector with no matches returns empty and empty result descriptor")
    void selectorWithNoMatchesReturnsEmpty() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        var result = runner.run(Selector.classRegex("nonexistent.ClassName"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("direct action run returns result with present descriptor")
    void directActionRunReturnsNonNull() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        var result = runner.run(Step.of("test", obj -> {}));

        assertThat(result.descriptor()).isPresent();
    }

    private static final class NoOpListener implements Listener {

        @Override
        public void onRunStarted() {}

        @Override
        public void onDiscoveryCompleted(final org.paramixel.api.action.Descriptor root) {}

        @Override
        public void onBeforeExecution(final org.paramixel.api.action.Descriptor descriptor) {}

        @Override
        public void onAfterExecution(final org.paramixel.api.action.Descriptor descriptor) {}

        @Override
        public void onRunCompleted(final org.paramixel.api.Result result) {}
    }
}
