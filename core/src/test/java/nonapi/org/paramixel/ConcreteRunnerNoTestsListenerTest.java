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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.selector.Selector;

@DisplayName("ConcreteRunner no-tests listener lifecycle")
class ConcreteRunnerNoTestsListenerTest {

    private static final String NON_EXISTENT = "NonExistentClassXyz123";

    private static final class RecordingListener implements Listener {

        private final AtomicInteger runStarted = new AtomicInteger();
        private final List<String> callOrder = new ArrayList<>();

        @Override
        public void onRunStarted(final Configuration configuration) {
            runStarted.incrementAndGet();
            callOrder.add("onRunStarted");
        }

        @Override
        public void onDiscoveryStarted() {
            callOrder.add("onDiscoveryStarted");
        }

        @Override
        public void onDiscoveryCompleted(final Optional<Descriptor> root) {
            callOrder.add(root.isEmpty() ? "onDiscoveryCompleted(empty)" : "onDiscoveryCompleted");
        }

        @Override
        public void onRunCompleted(final Result result) {
            callOrder.add("onRunCompleted");
        }
    }

    private static void assertNoTestsLifecycle(final RecordingListener listener) {
        assertThat(listener.runStarted.get()).isEqualTo(1);
        assertThat(listener.callOrder)
                .containsExactly("onRunStarted", "onDiscoveryStarted", "onDiscoveryCompleted(empty)", "onRunCompleted");
    }

    @Test
    @DisplayName("run() no-tests path fires the full listener lifecycle and returns a skipped result")
    void runNoTestsFiresLifecycle() {
        var listener = new RecordingListener();
        var config = Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, NON_EXISTENT));

        var result = new ConcreteRunner(config, listener).run();

        assertNoTestsLifecycle(listener);
        assertThat(result.descriptor()).isEmpty();
        assertThat(result.exitCode()).isZero();
    }

    @Test
    @DisplayName("run(Selector) no-tests path fires the full listener lifecycle")
    void runSelectorNoTestsFiresLifecycle() {
        var listener = new RecordingListener();
        var selector =
                Selector.builder().classPattern(Pattern.compile(NON_EXISTENT)).build();

        var result = new ConcreteRunner(Configuration.of(Map.of()), listener).run(selector);

        assertNoTestsLifecycle(listener);
        assertThat(result.descriptor()).isEmpty();
    }

    @Test
    @DisplayName("failIfNoTests makes the no-tests result fail")
    void noTestsFailsWhenFailIfNoTestsIsSet() {
        var listener = new RecordingListener();
        var config = Configuration.of(Map.of(Configuration.FAIL_IF_NO_TESTS, "true"));
        var selector =
                Selector.builder().classPattern(Pattern.compile(NON_EXISTENT)).build();

        var result = new ConcreteRunner(config, listener).run(selector);

        assertNoTestsLifecycle(listener);
        assertThat(result.status().isFailed()).isTrue();
        assertThat(result.exitCode()).isOne();
    }
}
