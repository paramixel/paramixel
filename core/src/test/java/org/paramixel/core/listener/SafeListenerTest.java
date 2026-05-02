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

package org.paramixel.core.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

@DisplayName("SafeListener")
class SafeListenerTest {

    @Test
    @DisplayName("of rejects null delegate")
    void ofRejectsNullDelegate() {
        assertThatThrownBy(() -> SafeListener.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate must not be null");
    }

    @Test
    @DisplayName("catches and logs RuntimeException from delegate")
    void catchesAndLogsRuntimeExceptionFromDelegate() {
        RuntimeException expectedException = new RuntimeException("listener failed");
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw expectedException;
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);

        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        runner.run(noop);

        assertThat(noop.getResult().getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("catches and logs checked Exception from delegate")
    void catchesAndLogsCheckedExceptionFromDelegate() {
        Listener throwingListener = new Listener() {
            @Override
            public void afterAction(Context context, Action action, Result result) {
                throw new RuntimeException("wrapped checked exception");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);

        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        runner.run(noop);

        assertThat(noop.getResult().getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError from runStarted")
    void rethrowsOutOfMemoryErrorFromRunStarted() {
        Listener throwingListener = new Listener() {
            @Override
            public void runStarted(Runner runner, Action action) {
                throw new OutOfMemoryError("simulated oom");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError from beforeAction")
    void rethrowsOutOfMemoryErrorFromBeforeAction() {
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw new OutOfMemoryError("simulated oom");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError from actionThrowable")
    void rethrowsOutOfMemoryErrorFromActionThrowable() {
        Listener throwingListener = new Listener() {
            @Override
            public void actionThrowable(Context context, Action action, Throwable throwable) {
                throw new OutOfMemoryError("simulated oom");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        Action action = org.paramixel.core.action.Direct.of("test", context -> {
            throw new RuntimeException("action failed");
        });
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(action)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError from afterAction")
    void rethrowsOutOfMemoryErrorFromAfterAction() {
        Listener throwingListener = new Listener() {
            @Override
            public void afterAction(Context context, Action action, Result result) {
                throw new OutOfMemoryError("simulated oom");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError from runCompleted")
    void rethrowsOutOfMemoryErrorFromRunCompleted() {
        Listener throwingListener = new Listener() {
            @Override
            public void runCompleted(Runner runner, Action action) {
                throw new OutOfMemoryError("simulated oom");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("rethrows custom Error subclass")
    void rethrowsCustomErrorSubclass() {
        class CustomError extends Error {
            CustomError(String message) {
                super(message);
            }
        }

        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw new CustomError("custom error");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop))
                .isInstanceOf(CustomError.class)
                .hasMessage("custom error");
    }

    @Test
    @DisplayName("catches RuntimeException from runStarted")
    void catchesRuntimeExceptionFromRunStarted() {
        Listener throwingListener = new Listener() {
            @Override
            public void runStarted(Runner runner, Action action) {
                throw new RuntimeException("runStarted failed");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        runner.run(noop);

        assertThat(noop.getResult().getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("catches RuntimeException from actionThrowable")
    void catchesRuntimeExceptionFromActionThrowable() {
        Listener throwingListener = new Listener() {
            @Override
            public void actionThrowable(Context context, Action action, Throwable throwable) {
                throw new RuntimeException("actionThrowable failed");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        Action action = org.paramixel.core.action.Direct.of("test", context -> {
            throw new RuntimeException("action failed");
        });
        Runner runner = Runner.builder().listener(safeListener).build();

        runner.run(action);

        assertThat(action.getResult().getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("catches RuntimeException from runCompleted")
    void catchesRuntimeExceptionFromRunCompleted() {
        Listener throwingListener = new Listener() {
            @Override
            public void runCompleted(Runner runner, Action action) {
                throw new RuntimeException("runCompleted failed");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        runner.run(noop);

        assertThat(noop.getResult().getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("rethrows StackOverflowError")
    void rethrowsStackOverflowError() {
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw new StackOverflowError("stack overflow");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("rethrows ThreadDeath")
    void rethrowsThreadDeath() {
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw new ThreadDeath();
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        assertThatThrownBy(() -> runner.run(noop)).isInstanceOf(ThreadDeath.class);
    }

    @Test
    @DisplayName("happy path delegates successfully")
    void happyPathDelegatesSuccessfully() {
        int[] counts = new int[5];
        Listener trackingListener = new Listener() {
            @Override
            public void runStarted(Runner runner, Action action) {
                counts[0]++;
            }

            @Override
            public void beforeAction(Context context, Action action) {
                counts[1]++;
            }

            @Override
            public void afterAction(Context context, Action action, Result result) {
                counts[2]++;
            }

            @Override
            public void runCompleted(Runner runner, Action action) {
                counts[3]++;
            }
        };

        SafeListener safeListener = SafeListener.of(trackingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();
        runner.run(noop);

        assertThat(counts[0]).isEqualTo(1);
        assertThat(counts[1]).isEqualTo(1);
        assertThat(counts[2]).isEqualTo(1);
        assertThat(counts[3]).isEqualTo(1);
    }

    @Test
    @DisplayName("logs RuntimeException to System.err")
    void logsRuntimeExceptionToStderr() {
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw new RuntimeException("test error message");
            }
        };

        SafeListener safeListener = SafeListener.of(throwingListener);
        org.paramixel.core.action.Noop noop = org.paramixel.core.action.Noop.of("test");
        Runner runner = Runner.builder().listener(safeListener).build();

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            runner.run(noop);
        } finally {
            System.setErr(originalErr);
        }

        String errOutput = errContent.toString(StandardCharsets.UTF_8);
        assertThat(errOutput).contains("Listener.beforeAction threw exception: test error message");
    }
}
