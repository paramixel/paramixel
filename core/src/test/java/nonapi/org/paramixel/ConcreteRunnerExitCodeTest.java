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

import java.util.Map;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteRunner exitCode")
class ConcreteRunnerExitCodeTest {

    private static Configuration config() {
        return Configuration.of(Map.of());
    }

    private static Configuration config(String key, String value) {
        return Configuration.of(Map.of(key, value));
    }

    private static ConcreteDescriptor passedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);
        return descriptor;
    }

    private static ConcreteDescriptor failedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.FAILED);
        return descriptor;
    }

    private static ConcreteDescriptor skippedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.SKIPPED);
        return descriptor;
    }

    private static ConcreteDescriptor abortedDescriptor() {
        var descriptor = new ConcreteDescriptor(Step.of("test", v -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.ABORTED);
        return descriptor;
    }

    private int exitCode(Configuration configuration, ConcreteDescriptor descriptor) {
        var result = new ConcreteResult(descriptor, configuration);
        var runner = new ConcreteRunner(configuration, noOpListener());
        return exitCodeMethod(runner, result);
    }

    private int exitCode(Configuration configuration, Status statusOverride) {
        var result = new ConcreteResult(configuration, statusOverride);
        var runner = new ConcreteRunner(configuration, noOpListener());
        return exitCodeMethod(runner, result);
    }

    private Listener noOpListener() {
        return new Listener() {};
    }

    private int exitCodeMethod(ConcreteRunner runner, ConcreteResult result) {
        try {
            var method = ConcreteRunner.class.getDeclaredMethod("exitCode", Result.class);
            method.setAccessible(true);
            return (int) method.invoke(runner, result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("PASSED returns exit code 0")
    class Passed {

        @Test
        @DisplayName("with default configuration")
        void passedDefault() {
            assertThat(exitCode(config(), passedDescriptor())).isZero();
        }

        @Test
        @DisplayName("with FAILURE_ON_SKIP true")
        void passedWithFailureOnSkip() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_SKIP, "true"), passedDescriptor()))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("FAILED returns exit code 1")
    class Failed {

        @Test
        @DisplayName("with default configuration")
        void failedDefault() {
            assertThat(exitCode(config(), failedDescriptor())).isOne();
        }

        @Test
        @DisplayName("with FAILURE_ON_SKIP true")
        void failedWithFailureOnSkip() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_SKIP, "true"), failedDescriptor()))
                    .isOne();
        }
    }

    @Nested
    @DisplayName("SKIPPED returns exit code based on FAILURE_ON_SKIP")
    class Skipped {

        @Test
        @DisplayName("returns 0 when FAILURE_ON_SKIP is absent (default)")
        void skippedDefault() {
            assertThat(exitCode(config(), skippedDescriptor())).isZero();
        }

        @Test
        @DisplayName("returns 0 when FAILURE_ON_SKIP is false")
        void skippedWhenFailureOnSkipFalse() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_SKIP, "false"), skippedDescriptor()))
                    .isZero();
        }

        @Test
        @DisplayName("returns 1 when FAILURE_ON_SKIP is true")
        void skippedWhenFailureOnSkipTrue() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_SKIP, "true"), skippedDescriptor()))
                    .isOne();
        }

        @Test
        @DisplayName("returns 0 for no-tests SKIPPED with FAIL_IF_NO_TESTS false")
        void noTestsSkippedWithFailIfNoTestsFalse() {
            assertThat(exitCode(config(Configuration.FAIL_IF_NO_TESTS, "false"), Status.SKIPPED))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("ABORTED returns exit code based on FAILURE_ON_ABORT")
    class Aborted {

        @Test
        @DisplayName("returns 1 when FAILURE_ON_ABORT is absent (default)")
        void abortedDefault() {
            assertThat(exitCode(config(), abortedDescriptor())).isOne();
        }

        @Test
        @DisplayName("returns 1 when FAILURE_ON_ABORT is true")
        void abortedWhenFailureOnAbortTrue() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_ABORT, "true"), abortedDescriptor()))
                    .isOne();
        }

        @Test
        @DisplayName("returns 0 when FAILURE_ON_ABORT is false")
        void abortedWhenFailureOnAbortFalse() {
            assertThat(exitCode(config(Configuration.FAILURE_ON_ABORT, "false"), abortedDescriptor()))
                    .isZero();
        }
    }
}
