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

package org.paramixel.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Listener;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Direct;
import org.paramixel.core.spi.listener.SafeListener;

@DisplayName("DefaultRunner validation")
class DefaultRunnerValidationTest {

    @Test
    @DisplayName("routing executor rejects invalid public method arguments")
    void routingExecutorRejectsInvalidPublicMethodArguments() {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        assertThatThrownBy(() -> executorService.awaitTermination(-1, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be non-negative");
        assertThatThrownBy(() -> executorService.awaitTermination(1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("unit must not be null");
        assertThatThrownBy(() -> executorService.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    @DisplayName("routing executor shutdownNow returns combined tasks")
    void routingExecutorShutdownNowReturnsCombinedTasks() {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        List<Runnable> tasks = executorService.shutdownNow();
        assertThat(tasks).isNotNull();
    }

    @Test
    @DisplayName("routing executor isShutdown returns true after shutdown")
    void routingExecutorIsShutdownReturnsTrueAfterShutdown() {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        executorService.shutdown();
        assertThat(executorService.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("routing executor isTerminated returns true after shutdown and termination")
    void routingExecutorIsTerminatedReturnsTrueAfterShutdownAndTermination() throws Exception {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(executorService.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("DefaultRunner rejects null action")
    void defaultRunnerRejectsNullAction() {
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);

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
    @DisplayName("Runner.Builder executorService rejects null")
    void runnerBuilderExecutorServiceRejectsNull() {
        assertThatThrownBy(() -> Runner.builder().executorService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executorService must not be null");
    }

    @Test
    @DisplayName("DefaultRunner with null configuration falls back to default properties")
    void defaultRunnerWithNullConfigurationFallsBack() {
        DefaultRunner runner = new DefaultRunner(null, new Listener() {}, null);
        Action action = Direct.of("test", context -> {});

        assertThat(runner.run(action).getStatus().isPass()).isTrue();
        assertThat(runner.getConfiguration()).containsKey(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("DefaultRunner with external executor service uses it for runner work")
    void defaultRunnerWithExternalExecutorService() throws Exception {
        ExecutorService externalExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            DefaultRunner runner =
                    new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, externalExecutor);
            Action action = Direct.of("test", context -> {});

            assertThat(runner.run(action).getStatus().isPass()).isTrue();
        } finally {
            externalExecutor.shutdownNow();
            externalExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("routing executor awaitTermination with valid timeout succeeds")
    void routingExecutorAwaitTerminationWithValidTimeout() throws Exception {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();
    }

    @Test
    @DisplayName("DefaultRunner stores configuration")
    void defaultRunnerStoresConfiguration() {
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);

        assertThat(runner.getConfiguration()).containsKey(Configuration.RUNNER_PARALLELISM);
    }

    @Test
    @DisplayName("DefaultRunner stores listener as provided")
    void defaultRunnerStoresListenerAsProvided() {
        Listener rawListener = new Listener() {};
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), rawListener, null);

        assertThat(runner.getListener()).isSameAs(rawListener);
    }

    @Test
    @DisplayName("DefaultRunner does not double-wrap SafeListener in getListener")
    void defaultRunnerDoesNotDoubleWrapSafeListenerInGetListener() {
        SafeListener safeListener = new SafeListener(new Listener() {});
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), safeListener, null);

        assertThat(runner.getListener()).isSameAs(safeListener);
    }
}
