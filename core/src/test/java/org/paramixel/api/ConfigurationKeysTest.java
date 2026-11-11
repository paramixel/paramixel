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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("Configuration keys")
class ConfigurationKeysTest {

    private static final class WarningListener implements Listener {

        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void onWarning(final String message) {
            warnings.add(message);
        }
    }

    @Test
    @DisplayName("poolKey builds the documented key")
    void poolKey() {
        assertThat(Configuration.poolKey("docker")).isEqualTo("paramixel.parallelism.docker");
        assertThat(Configuration.PARALLELISM_POOL_PREFIX).isEqualTo("paramixel.parallelism.");
        assertThat(Configuration.RUNNER_PARALLELISM).isEqualTo("paramixel.parallelism");
    }

    @Test
    @DisplayName("poolKey rejects a null pool")
    void poolKeyRejectsNull() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Configuration.poolKey(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("every documented key is accepted without a warning")
    void knownKeysDoNotWarn() {
        var listener = new WarningListener();
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                "2",
                Configuration.ANSI,
                "false",
                Configuration.FAILURE_ON_SKIP,
                "true",
                Configuration.FAILURE_ON_ABORT,
                "false",
                Configuration.FAIL_IF_NO_TESTS,
                "true",
                Configuration.FAIL_FAST,
                "true",
                Configuration.MATCH_CLASS_REGEX,
                "NoSuchClass",
                "paramixel.parallelism.docker",
                "2"));

        Runner.builder().configuration(configuration).listener(listener).build().run(Selector.configured());

        assertThat(listener.warnings).isEmpty();
    }

    @Test
    @DisplayName("an unknown paramixel key warns once")
    void unknownKeyWarnsOnce() {
        var listener = new WarningListener();
        var configuration = Configuration.of(Map.of("paramixel.unknown", "1"));

        Runner.builder()
                .configuration(configuration)
                .listener(listener)
                .build()
                .run(Selector.builder()
                        .classPattern(Pattern.compile("NoSuchClass"))
                        .build());

        assertThat(listener.warnings).hasSize(1);
        assertThat(listener.warnings.get(0)).contains("paramixel.unknown");
    }

    @Test
    @DisplayName("a non-paramixel key does not warn")
    void foreignKeyDoesNotWarn() {
        var listener = new WarningListener();
        var configuration = Configuration.of(Map.of("example.setting", "1"));

        Runner.builder()
                .configuration(configuration)
                .listener(listener)
                .build()
                .run(Selector.builder()
                        .classPattern(Pattern.compile("NoSuchClass"))
                        .build());

        assertThat(listener.warnings).isEmpty();
    }
}
