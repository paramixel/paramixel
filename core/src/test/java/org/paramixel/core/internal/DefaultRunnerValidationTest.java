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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.internal.listener.SafeListener;

@DisplayName("DefaultRunner validation")
class DefaultRunnerValidationTest {

    @Test
    @DisplayName("DefaultRunner rejects null listener")
    void defaultRunnerRejectsNullListener() {
        assertThatThrownBy(() -> new DefaultRunner(Configuration.defaultProperties(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("DefaultRunner rejects null action")
    void defaultRunnerRejectsNullAction() {
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {});

        assertThatThrownBy(() -> runner.run((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Runner.Builder configuration rejects null")
    void runnerBuilderConfigurationRejectsNull() {
        assertThatThrownBy(() -> Runner.builder().configuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration must not be null");
    }

    @Test
    @DisplayName("Runner.Builder listener rejects null")
    void runnerBuilderListenerRejectsNull() {
        assertThatThrownBy(() -> Runner.builder().listener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener must not be null");
    }

    @Test
    @DisplayName("DefaultRunner with null configuration falls back to default properties")
    void defaultRunnerWithNullConfigurationFallsBack() {
        DefaultRunner runner = new DefaultRunner(null, new Listener() {});
        Action action = Direct.builder("test").execute(context -> {}).build();

        assertThat(runner.run(action).getStatus().isPass()).isTrue();
        assertThat(runner.getConfiguration()).containsKey(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("DefaultRunner stores configuration")
    void defaultRunnerStoresConfiguration() {
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {});

        assertThat(runner.getConfiguration()).containsKey(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("DefaultRunner stores listener as provided")
    void defaultRunnerStoresListenerAsProvided() {
        Listener rawListener = new Listener() {};
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), rawListener);

        assertThat(runner.getListener()).isSameAs(rawListener);
    }

    @Test
    @DisplayName("DefaultRunner does not double-wrap SafeListener in getListener")
    void defaultRunnerDoesNotDoubleWrapSafeListenerInGetListener() {
        SafeListener safeListener = new SafeListener(new Listener() {});
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), safeListener);

        assertThat(runner.getListener()).isSameAs(safeListener);
    }

    @Nested
    @DisplayName("run() execution path")
    class RunExecutionPath {

        @Test
        @DisplayName("calls listener runStarted")
        void callsListenerRunStarted() {
            AtomicBoolean runStartedCalled = new AtomicBoolean();
            Listener listener = new Listener() {
                @Override
                public void runStarted(Runner runner) {
                    runStartedCalled.set(true);
                }
            };
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), listener);

            runner.run(Noop.of("test"));

            assertThat(runStartedCalled).isTrue();
        }

        @Test
        @DisplayName("calls listener runCompleted")
        void callsListenerRunCompleted() {
            AtomicBoolean runCompletedCalled = new AtomicBoolean();
            Listener listener = new Listener() {
                @Override
                public void runCompleted(Runner runner, Result result) {
                    runCompletedCalled.set(true);
                }
            };
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), listener);

            runner.run(Noop.of("test"));

            assertThat(runCompletedCalled).isTrue();
        }

        @Test
        @DisplayName("runStarted is called before runCompleted")
        void runStartedIsCalledBeforeRunCompleted() {
            AtomicReference<String> firstCallback = new AtomicReference<>();
            Listener listener = new Listener() {
                @Override
                public void runStarted(Runner runner) {
                    firstCallback.compareAndSet(null, "runStarted");
                }

                @Override
                public void runCompleted(Runner runner, Result result) {
                    firstCallback.compareAndSet(null, "runCompleted");
                }
            };
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), listener);

            runner.run(Noop.of("test"));

            assertThat(firstCallback).hasValue("runStarted");
        }

        @Test
        @DisplayName("nested parallel with low global parallelism completes")
        void nestedParallelWithLowGlobalParallelismCompletes() {
            Action action = nestedParallel(5);
            DefaultRunner runner = new DefaultRunner(Map.of(Configuration.RUNNER_PARALLELISM, "1"), new Listener() {});

            Result result = runner.run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("resolveParallelism throws ConfigurationException for non-numeric value")
        void resolveParallelismThrowsConfigurationExceptionForNonNumericValue() {
            assertThatThrownBy(
                            () -> new DefaultRunner(Map.of(Configuration.RUNNER_PARALLELISM, "abc"), new Listener() {}))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected integer");
        }

        @Test
        @DisplayName("resolveParallelism throws ConfigurationException for zero")
        void resolveParallelismThrowsConfigurationExceptionForZero() {
            assertThatThrownBy(
                            () -> new DefaultRunner(Map.of(Configuration.RUNNER_PARALLELISM, "0"), new Listener() {}))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer");
        }

        @Test
        @DisplayName("resolveParallelism throws ConfigurationException for negative value")
        void resolveParallelismThrowsConfigurationExceptionForNegativeValue() {
            assertThatThrownBy(
                            () -> new DefaultRunner(Map.of(Configuration.RUNNER_PARALLELISM, "-1"), new Listener() {}))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("expected positive integer");
        }

        @Test
        @DisplayName("close delegates to listener close")
        void closeDelegatesToListenerClose() {
            AtomicBoolean listenerClosed = new AtomicBoolean();
            Listener listener = new Listener() {
                @Override
                public void close() {
                    listenerClosed.set(true);
                }
            };
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), listener);

            runner.close();

            assertThat(listenerClosed).isTrue();
        }

        @Test
        @DisplayName("run wraps listener in SafeListener so throwing listener does not propagate")
        void runWrapsListenerInSafeListenerSoThrowingListenerDoesNotPropagate() {
            Listener throwingListener = new Listener() {
                @Override
                public void runStarted(Runner runner) {
                    throw new RuntimeException("listener error");
                }
            };
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), throwingListener);

            Result result = runner.run(Noop.of("test"));

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("run returns result matching action")
        void runReturnsResultMatchingAction() {
            DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {});
            Action action = Noop.of("test");

            Result result = runner.run(action);

            assertThat(result.getStatus().isPass()).isTrue();
            assertThat(result.getAction()).isSameAs(action);
        }
    }

    private static Action nestedParallel(int depth) {
        if (depth == 0) {
            return Noop.of("leaf");
        }
        return Parallel.builder("parallel-" + depth)
                .child(nestedParallel(depth - 1))
                .build();
    }
}
