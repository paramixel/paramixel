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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner selector warnings")
class RunnerSelectorWarningTest {

    private static final class WarningListener implements Listener {

        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void onWarning(final String message) {
            warnings.add(message);
        }
    }

    @Test
    @DisplayName("warns when a match key is set and the selector does not use configured()")
    void warnsWhenMatchKeyIsIgnored() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.MATCH_TAG_REGEX, "smoke")))
                .listener(listener)
                .build();

        runner.run(
                Selector.builder().classPattern(Pattern.compile("NoSuchClass")).build());

        assertThat(listener.warnings).hasSize(1);
        assertThat(listener.warnings.get(0))
                .contains(Configuration.MATCH_TAG_REGEX)
                .contains("Selector.configured()");
    }

    @Test
    @DisplayName("does not warn when the selector uses configured()")
    void doesNotWarnForConfiguredSelector() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, "NoSuchClass")))
                .listener(listener)
                .build();

        runner.run(Selector.configured());

        assertThat(listener.warnings).isEmpty();
    }

    @Test
    @DisplayName("does not warn when no match key is set")
    void doesNotWarnWithoutMatchKeys() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .listener(listener)
                .build();

        runner.run(
                Selector.builder().classPattern(Pattern.compile("NoSuchClass")).build());

        assertThat(listener.warnings).isEmpty();
    }

    @Test
    @DisplayName("warns about a pool key that no Parallel uses")
    void warnsAboutUnusedPoolKey() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.parallelism.misspelled", "2")))
                .listener(listener)
                .build();

        runner.run(Step.step("step", context -> {}));

        assertThat(listener.warnings).hasSize(1);
        assertThat(listener.warnings.get(0))
                .contains("paramixel.parallelism.misspelled")
                .contains("no Parallel in this run uses it");
    }

    @Test
    @DisplayName("warns about an unknown paramixel key")
    void warnsAboutUnknownParamixelKey() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.unknownKey", "value")))
                .listener(listener)
                .build();

        runner.run(
                Selector.builder().classPattern(Pattern.compile("NoSuchClass")).build());

        assertThat(listener.warnings).hasSize(1);
        assertThat(listener.warnings.get(0)).contains("paramixel.unknownKey");
    }

    @Test
    @DisplayName("a discovery run still runs hooks and factories")
    void discoveryRunStillExecutes() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(
                        Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, ".*ActionResolverSmokeFixture")))
                .listener(listener)
                .build();

        var result = runner.run(Selector.configured());

        assertThat(result.descriptor()).isPresent();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(listener.warnings).isEmpty();
    }

    @Test
    @DisplayName("no-tests lifecycle still reports warnings")
    void noTestsLifecycleReportsWarnings() {
        var listener = new WarningListener();
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.MATCH_TAG_REGEX, "smoke")))
                .listener(listener)
                .build();

        var result = runner.run(
                Selector.builder().classPattern(Pattern.compile("NoSuchClass")).build());

        assertThat(result.descriptor()).isEmpty();
        assertThat(listener.warnings).hasSize(1);
    }

    @Test
    @DisplayName("onDiscoveryCompleted receives an empty optional when nothing is discovered")
    void discoveryCompletedIsEmptyOptional() {
        var discovered = new CopyOnWriteArrayList<Optional<Descriptor>>();
        var runner = Runner.builder()
                .listener(new Listener() {
                    @Override
                    public void onDiscoveryCompleted(final Optional<Descriptor> root) {
                        discovered.add(root);
                    }
                })
                .build();

        runner.run(
                Selector.builder().classPattern(Pattern.compile("NoSuchClass")).build());

        assertThat(discovered).containsExactly(Optional.empty());
    }
}
