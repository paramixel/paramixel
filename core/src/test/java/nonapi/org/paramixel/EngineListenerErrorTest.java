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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

/**
 * Pins the engine's listener-error policy: listener failures never fail a node, except a {@link VirtualMachineError},
 * which must never be swallowed.
 */
@DisplayName("Engine listener errors")
@org.junit.jupiter.api.Timeout(60)
class EngineListenerErrorTest {

    private static Runner runner(final Listener listener) {
        return Runner.builder()
                .configuration(Configuration.of(Map.of()))
                .listener(listener)
                .build();
    }

    @Test
    @DisplayName("a listener that throws in onAfterExecution does not fail the node")
    void afterExecutionFailureIsSwallowed() {
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                throw new IllegalStateException("listener failed");
            }
        };
        var calls = new AtomicInteger();

        var result = runner(listener).run(Step.step("step", context -> calls.incrementAndGet()));

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a listener that throws in onBeforeExecution does not fail the node")
    void beforeExecutionFailureIsSwallowed() {
        var listener = new Listener() {
            @Override
            public void onBeforeExecution(final Descriptor descriptor) {
                throw new IllegalStateException("listener failed");
            }
        };

        var result = runner(listener).run(Step.step("step", context -> {}));

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("an AssertionError from a listener is swallowed")
    void assertionErrorFromListenerIsSwallowed() {
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                throw new AssertionError("listener assertion");
            }
        };

        var result = runner(listener).run(Step.step("step", context -> {}));

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a StackOverflowError from onAfterExecution is swallowed")
    void stackOverflowErrorIsSwallowed() {
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                throw new StackOverflowError("listener overflow");
            }
        };

        var result = runner(listener).run(Step.step("step", context -> {}));

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a VirtualMachineError from onAfterExecution is rethrown")
    void virtualMachineErrorIsRethrown() {
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                throw new OutOfMemoryError("simulated");
            }
        };

        try {
            runner(listener).run(Step.step("step", context -> {}));
            org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown(OutOfMemoryError.class);
        } catch (OutOfMemoryError expected) {
            assertThat(expected).hasMessage("simulated");
        }
    }

    @Test
    @DisplayName("a listener failure does not change the reported result detail")
    void resultDetailIsUnaffected() {
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                throw new IllegalStateException("listener failed");
            }
        };

        var result = runner(listener).run(Step.step("failing", context -> {
            throw org.paramixel.api.exception.FailException.fail("step failed");
        }));

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message()).hasValue("step failed");
        assertThat(result).isInstanceOf(Result.class);
    }
}
