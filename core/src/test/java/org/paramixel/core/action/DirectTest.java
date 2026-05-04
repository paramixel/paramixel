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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.exception.FailException;
import org.paramixel.core.exception.SkipException;

@DisplayName("Direct")
class DirectTest {

    @Test
    @DisplayName("catches RuntimeException as failure")
    void catchesRuntimeExceptionAsFailure() {
        var expectedException = new RuntimeException("test exception");
        Action action = Direct.of("test", context -> {
            throw expectedException;
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getThrowable()).isPresent();
        assertThat(result.getStatus().getThrowable().get()).isSameAs(expectedException);
    }

    @Test
    @DisplayName("catches FailException as failure")
    void catchesFailExceptionAsFailure() {
        Action action = Direct.of("test", context -> {
            throw FailException.of("expected failure");
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("expected failure");
    }

    @Test
    @DisplayName("catches SkipException as skip")
    void catchesSkipExceptionAsSkip() {
        Action action = Direct.of("test", context -> {
            throw SkipException.of("database not available");
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("database not available");
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError")
    void rethrowsOutOfMemoryError() {
        Action action = Direct.of("test", context -> {
            throw new OutOfMemoryError("simulated oom");
        });

        Runner runner = Runner.builder().build();

        assertThatThrownBy(() -> runner.run(action))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("rethrows StackOverflowError")
    void rethrowsStackOverflowError() {
        Action action = Direct.of("test", context -> {
            throw new StackOverflowError("simulated stack overflow");
        });

        Runner runner = Runner.builder().build();

        assertThatThrownBy(() -> runner.run(action))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("simulated stack overflow");
    }

    @Test
    @DisplayName("rethrows ThreadDeath")
    void rethrowsThreadDeath() {
        Action action = Direct.of("test", context -> {
            throw new ThreadDeath();
        });

        Runner runner = Runner.builder().build();

        assertThatThrownBy(() -> runner.run(action)).isInstanceOf(ThreadDeath.class);
    }

    @Test
    @DisplayName("rethrows custom Error subclass")
    void rethrowsCustomErrorSubclass() {
        class CustomError extends Error {
            CustomError(String message) {
                super(message);
            }
        }

        Action action = Direct.of("test", context -> {
            throw new CustomError("custom error");
        });

        Runner runner = Runner.builder().build();

        assertThatThrownBy(() -> runner.run(action))
                .isInstanceOf(CustomError.class)
                .hasMessage("custom error");
    }

    @Test
    @DisplayName("Error does not notify listener actionThrowable or afterAction")
    void errorDoesNotNotifyListener() {
        AtomicBoolean actionThrowableCalled = new AtomicBoolean(false);
        AtomicBoolean afterActionCalled = new AtomicBoolean(false);

        Listener listener = new Listener() {
            @Override
            public void actionThrowable(Result result, Throwable throwable) {
                actionThrowableCalled.set(true);
            }

            @Override
            public void afterAction(Result result) {
                afterActionCalled.set(true);
            }
        };

        Action action = Direct.of("test", context -> {
            throw new OutOfMemoryError("simulated oom");
        });

        Runner runner = Runner.builder().listener(listener).build();

        assertThatThrownBy(() -> runner.run(action)).isInstanceOf(OutOfMemoryError.class);

        assertThat(actionThrowableCalled).isFalse();
        assertThat(afterActionCalled).isFalse();
    }

    @Test
    @DisplayName("RuntimeException notifies listener actionThrowable")
    void runtimeExceptionNotifiesListenerActionThrowable() {
        AtomicReference<Throwable> capturedThrowable = new AtomicReference<>();

        Listener listener = new Listener() {
            @Override
            public void actionThrowable(Result result, Throwable throwable) {
                capturedThrowable.set(throwable);
            }
        };

        var expectedException = new RuntimeException("expected");
        Action action = Direct.of("test", context -> {
            throw expectedException;
        });

        Runner runner = Runner.builder().listener(listener).build();
        runner.run(action);

        assertThat(capturedThrowable).hasValue(expectedException);
    }

    @Test
    @DisplayName("of rejects null name")
    void ofRejectsNullName() {
        assertThatThrownBy(() -> Direct.of(null, context -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of rejects blank name")
    void ofRejectsBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> Direct.of("", context -> {}));
        assertThatIllegalArgumentException().isThrownBy(() -> Direct.of("   ", context -> {}));
    }

    @Test
    @DisplayName("of rejects null executable")
    void ofRejectsNullExecutable() {
        assertThatThrownBy(() -> Direct.of("test", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("execute rejects null context")
    void executeRejectsNullContext() {
        Direct action = Direct.of("test", context -> {});

        assertThatThrownBy(() -> action.execute(null)).isInstanceOf(NullPointerException.class);
    }
}
