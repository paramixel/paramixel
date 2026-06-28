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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.selector.Selector;

@DisplayName("ConcreteRunner no-tests listener lifecycle")
class ConcreteRunnerNoTestsListenerTest {

    @Test
    @DisplayName("run() no-tests path fires full listener lifecycle")
    void initializeCalledInNoTestsPath() {
        var initializeCalls = new AtomicInteger(0);
        var callOrder = new ArrayList<String>();

        var listener = new Listener() {
            @Override
            public void initialize(final Configuration configuration) {
                initializeCalls.incrementAndGet();
                callOrder.add("initialize");
            }

            @Override
            public void onRunStarted() {
                callOrder.add("onRunStarted");
            }

            @Override
            public void onDiscoveryStarted() {
                callOrder.add("onDiscoveryStarted");
            }

            @Override
            public void onDiscoveryCompleted(final Descriptor root) {
                callOrder.add("onDiscoveryCompleted");
            }

            @Override
            public void onRunCompleted(final Result result) {
                callOrder.add("onRunCompleted");
            }
        };

        var config = Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, "NonExistentClassXyz123"));
        var runner = new ConcreteRunner(config, listener);

        runner.run();

        assertThat(initializeCalls.get()).isEqualTo(1);
        assertThat(callOrder)
                .containsExactly(
                        "initialize", "onRunStarted", "onDiscoveryStarted", "onDiscoveryCompleted", "onRunCompleted");
    }

    @Test
    @DisplayName("run(Selector) no-tests path fires full listener lifecycle")
    void runSelectorNoTestsCallsAllCallbacks() {
        var initializeCalls = new AtomicInteger(0);
        var callOrder = new ArrayList<String>();

        var listener = new Listener() {
            @Override
            public void initialize(final Configuration configuration) {
                initializeCalls.incrementAndGet();
                callOrder.add("initialize");
            }

            @Override
            public void onRunStarted() {
                callOrder.add("onRunStarted");
            }

            @Override
            public void onDiscoveryStarted() {
                callOrder.add("onDiscoveryStarted");
            }

            @Override
            public void onDiscoveryCompleted(final Descriptor root) {
                callOrder.add("onDiscoveryCompleted");
            }

            @Override
            public void onRunCompleted(final Result result) {
                callOrder.add("onRunCompleted");
            }
        };

        var selector = Selector.classRegex("NonExistentClassXyz123");
        var runner = new ConcreteRunner(Configuration.defaultConfiguration(), listener);

        var result = runner.run(selector);

        assertThat(initializeCalls.get()).isEqualTo(1);
        assertThat(callOrder)
                .containsExactly(
                        "initialize", "onRunStarted", "onDiscoveryStarted", "onDiscoveryCompleted", "onRunCompleted");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("runAndReturnExitCode(Selector) no-tests path fires full listener lifecycle")
    void runAndReturnExitCodeSelectorNoTestsCallsAllCallbacks() {
        var initializeCalls = new AtomicInteger(0);
        var callOrder = new ArrayList<String>();

        var listener = new Listener() {
            @Override
            public void initialize(final Configuration configuration) {
                initializeCalls.incrementAndGet();
                callOrder.add("initialize");
            }

            @Override
            public void onRunStarted() {
                callOrder.add("onRunStarted");
            }

            @Override
            public void onDiscoveryStarted() {
                callOrder.add("onDiscoveryStarted");
            }

            @Override
            public void onDiscoveryCompleted(final Descriptor root) {
                callOrder.add("onDiscoveryCompleted");
            }

            @Override
            public void onRunCompleted(final Result result) {
                callOrder.add("onRunCompleted");
            }
        };

        var selector = Selector.classRegex("NonExistentClassXyz123");
        var runner = new ConcreteRunner(Configuration.defaultConfiguration(), listener);

        int exitCode = runner.runAndReturnExitCode(selector);

        assertThat(initializeCalls.get()).isEqualTo(1);
        assertThat(callOrder)
                .containsExactly(
                        "initialize", "onRunStarted", "onDiscoveryStarted", "onDiscoveryCompleted", "onRunCompleted");
        assertThat(exitCode).isZero();
    }
}
