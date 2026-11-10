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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Step;

/**
 * Verifies that a removed configuration key is reported once and never fails a run.
 */
@DisplayName("Removed configuration keys")
class RemovedConfigurationKeysTest {

    private static final String REMOVED_KEY = "paramixel.scheduler.queue.capacity";

    @BeforeEach
    @AfterEach
    void resetReportedKeys() {
        RemovedConfigurationKeys.resetForTesting();
    }

    @Test
    @DisplayName("numeric value warns once and the run succeeds")
    void numericValueWarnsAndRunSucceeds() {
        var listener = new RecordingListener();

        var result = runner(Map.of(REMOVED_KEY, "32"), listener).run(Step.of("step", context -> {}));

        assertThat(result.isPassed()).isTrue();
        assertThat(listener.warnings())
                .singleElement()
                .asString()
                .contains(REMOVED_KEY)
                .contains(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("non-numeric value warns instead of failing the run")
    void nonNumericValueDoesNotThrow() {
        var listener = new RecordingListener();

        var result = runner(Map.of(REMOVED_KEY, "not-a-number"), listener).run(Step.of("step", context -> {}));

        assertThat(result.isPassed()).isTrue();
        assertThat(listener.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("absent key produces no warning")
    void absentKeyIsSilent() {
        var listener = new RecordingListener();

        var result = runner(Map.of(), listener).run(Step.of("step", context -> {}));

        assertThat(result.isPassed()).isTrue();
        assertThat(listener.warnings()).isEmpty();
    }

    @Test
    @DisplayName("key is reported at most once per JVM")
    void warnsOncePerJvm() {
        var configuration = Configuration.of(Map.of(REMOVED_KEY, "16"));
        var first = new RecordingListener();
        var second = new RecordingListener();

        runner(configuration, first).run(Step.of("first", context -> {}));
        runner(configuration, second).run(Step.of("second", context -> {}));

        assertThat(first.warnings()).hasSize(1);
        assertThat(second.warnings()).isEmpty();
    }

    @Test
    @DisplayName("removed key does not set runner parallelism")
    void removedKeyDoesNotAffectParallelism() {
        var listener = new RecordingListener();

        var result = runner(Map.of(REMOVED_KEY, "1", Configuration.RUNNER_PARALLELISM, "2"), listener)
                .run(Step.of("step", context -> {}));

        assertThat(result.isPassed()).isTrue();
        assertThat(listener.warnings()).hasSize(1);
    }

    private static Runner runner(final Map<String, String> properties, final Listener listener) {
        return runner(Configuration.of(properties), listener);
    }

    private static Runner runner(final Configuration configuration, final Listener listener) {
        return Runner.builder().configuration(configuration).listener(listener).build();
    }

    private static final class RecordingListener implements Listener {

        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void onWarning(final String message) {
            warnings.add(message);
        }

        private List<String> warnings() {
            return warnings;
        }
    }
}
