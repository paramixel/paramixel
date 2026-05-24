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

package org.paramixel.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ThrowingRunnable;

@DisplayName("CleanUp")
class CleanUpTest {

    @Test
    @DisplayName("run executes runnable")
    void runExecutesRunnable() {
        var executed = new boolean[1];
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> executed[0] = true);

        cleanup.run();

        assertThat(executed[0]).isTrue();
    }

    @Test
    @DisplayName("run with null AutoCloseable is no-op")
    void runWithNullAutoCloseableIsNoOp() {
        CleanUp cleanup = CleanUp.of((AutoCloseable) null);

        cleanup.run();

        assertThat(cleanup.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("run captures RuntimeException")
    void runCapturesRuntimeException() {
        var exception = new RuntimeException("error");
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
        assertThat(cleanup.throwable()).isSameAs(exception);
    }

    @Test
    @DisplayName("run captures checked exception")
    void runCapturedCheckedException() {
        class CheckedException extends Exception {}

        var exception = new CheckedException();
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
        assertThat(cleanup.throwable()).isSameAs(exception);
    }

    @Test
    @DisplayName("run rethrows OutOfMemoryError")
    void runRethrowsOutOfMemoryError() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new OutOfMemoryError("simulated oom");
        });

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("run rethrows InternalError")
    void runRethrowsInternalError() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new InternalError("simulated ie");
        });

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(InternalError.class)
                .hasMessage("simulated ie");
    }

    @Test
    @DisplayName("run captures StackOverflowError")
    void runCapturesStackOverflowError() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new StackOverflowError("simulated soe");
        });

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
        assertThat(cleanup.throwable()).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("run captures AssertionError")
    void runCapturesAssertionError() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new AssertionError("assertion failed");
        });

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
        assertThat(cleanup.throwable()).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("run throws IllegalStateException on second call")
    void runThrowsIllegalStateExceptionOnSecondCall() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CleanUp has already run");
    }

    @Test
    @DisplayName("of AutoCloseable with non-null closeable executes close")
    void ofAutoCloseableWithNonNullExecutesClose() throws Throwable {
        var executionOrder = new ArrayList<String>();
        class TestCloseable implements AutoCloseable {
            @Override
            public void close() {
                executionOrder.add("closed");
            }
        }

        CleanUp cleanup = CleanUp.of(new TestCloseable());
        cleanup.run();

        assertThat(executionOrder).containsExactly("closed");
        assertThat(cleanup.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("of AutoCloseable captures close exception")
    void ofAutoCloseableCapturesCloseException() {
        class ThrowingCloseable implements AutoCloseable {
            @Override
            public void close() {
                throw new RuntimeException("close failed");
            }
        }

        CleanUp cleanup = CleanUp.of(new ThrowingCloseable());
        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
        assertThat(cleanup.throwable()).hasMessage("close failed");
    }

    @Test
    @DisplayName("runAndThrow returns normally when no exception")
    void runAndThrowReturnsNormallyWhenNoException() throws Throwable {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.runAndThrow();
    }

    @Test
    @DisplayName("runAndThrow throws captured exception")
    void runAndThrowThrowsCapturedException() {
        var exception = new RuntimeException("error");
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });

        assertThatThrownBy(() -> cleanup.runAndThrow()).isSameAs(exception);
    }

    @Test
    @DisplayName("runAndThrow rethrows OutOfMemoryError immediately")
    void runAndThrowRethrowsOutOfMemoryErrorImmediately() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new OutOfMemoryError("simulated oom");
        });

        assertThatThrownBy(() -> cleanup.runAndThrow())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("runAndThrow throws IllegalStateException on second call")
    void runAndThrowThrowsIllegalStateExceptionOnSecondCall() throws Throwable {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.runAndThrow();

        assertThatThrownBy(() -> cleanup.runAndThrow())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CleanUp has already run");
    }

    @Test
    @DisplayName("getThrowable returns null before run")
    void getThrowableReturnsNullBeforeRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        assertThat(cleanup.throwable()).isNull();
    }

    @Test
    @DisplayName("getThrowable returns null after successful run")
    void getThrowableReturnsNullAfterSuccessfulRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThat(cleanup.throwable()).isNull();
    }

    @Test
    @DisplayName("getThrowable returns exception after failed run")
    void getThrowableReturnsExceptionAfterFailedRun() {
        var exception = new RuntimeException("error");
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });

        cleanup.run();

        assertThat(cleanup.throwable()).isSameAs(exception);
    }

    @Test
    @DisplayName("isEmpty returns true before run")
    void isEmptyReturnsTrueBeforeRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        assertThat(cleanup.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty returns true after successful run")
    void isEmptyReturnsTrueAfterSuccessfulRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThat(cleanup.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty returns false after failed run")
    void isEmptyReturnsFalseAfterFailedRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new RuntimeException("error");
        });

        cleanup.run();

        assertThat(cleanup.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns false before run")
    void isNotEmptyReturnsFalseBeforeRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        assertThat(cleanup.isNotEmpty()).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns false after successful run")
    void isNotEmptyReturnsFalseAfterSuccessfulRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns true after failed run")
    void isNotEmptyReturnsTrueAfterFailedRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {
            throw new RuntimeException("error");
        });

        cleanup.run();

        assertThat(cleanup.isNotEmpty()).isTrue();
    }

    @Test
    @DisplayName("static runAndThrow with vararg runs all and throws first with suppressed")
    void staticRunAndThrowVarargRunsAllAndThrowsFirstWithSuppressed() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");
        var third = new RuntimeException("third");

        CleanUp cleanup1 = CleanUp.of((ThrowingRunnable) () -> {
            throw first;
        });
        CleanUp cleanup2 = CleanUp.of((ThrowingRunnable) () -> {
            throw second;
        });
        CleanUp cleanup3 = CleanUp.of((ThrowingRunnable) () -> {
            throw third;
        });

        assertThatThrownBy(() -> CleanUp.runAndThrow(cleanup1, cleanup2, cleanup3))
                .isSameAs(first)
                .satisfies(t -> {
                    assertThat(t.getSuppressed()).hasSize(2);
                    assertThat(t.getSuppressed()[0]).isSameAs(second);
                    assertThat(t.getSuppressed()[1]).isSameAs(third);
                });
    }

    @Test
    @DisplayName("static runAndThrow with vararg returns normally when no exceptions")
    void staticRunAndThrowVarargReturnsNormallyWhenNoExceptions() throws Throwable {
        CleanUp cleanup1 = CleanUp.of((ThrowingRunnable) () -> {});
        CleanUp cleanup2 = CleanUp.of((ThrowingRunnable) () -> {});

        CleanUp.runAndThrow(cleanup1, cleanup2);
    }

    @Test
    @DisplayName("static runAndThrow with vararg throws when first fails and second succeeds")
    void staticRunAndThrowVarargThrowsWhenFirstFailsAndSecondSucceeds() {
        var exception = new RuntimeException("error");
        CleanUp cleanup1 = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });
        CleanUp cleanup2 = CleanUp.of((ThrowingRunnable) () -> {});

        assertThatThrownBy(() -> CleanUp.runAndThrow(cleanup1, cleanup2)).isSameAs(exception);
    }

    @Test
    @DisplayName("static runAndThrow with vararg throws when first succeeds and second fails")
    void staticRunAndThrowVarargThrowsWhenFirstSucceedsAndSecondFails() {
        var exception = new RuntimeException("error");
        CleanUp cleanup1 = CleanUp.of((ThrowingRunnable) () -> {});
        CleanUp cleanup2 = CleanUp.of((ThrowingRunnable) () -> {
            throw exception;
        });

        assertThatThrownBy(() -> CleanUp.runAndThrow(cleanup1, cleanup2)).isSameAs(exception);
    }

    @Test
    @DisplayName("static runAndThrow with empty array does nothing")
    void staticRunAndThrowVarargWithEmptyArray() throws Throwable {
        CleanUp.runAndThrow(new CleanUp[0]);
    }

    @Test
    @DisplayName("static runAndThrow with collection runs all and throws first with suppressed")
    void staticRunAndThrowCollectionRunsAllAndThrowsFirstWithSuppressed() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");

        Collection<CleanUp> cleanUps = List.of(
                CleanUp.of((ThrowingRunnable) () -> {
                    throw first;
                }),
                CleanUp.of((ThrowingRunnable) () -> {
                    throw second;
                }));

        assertThatThrownBy(() -> CleanUp.runAndThrow(cleanUps)).isSameAs(first).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(1);
            assertThat(t.getSuppressed()[0]).isSameAs(second);
        });
    }

    @Test
    @DisplayName("static runAndThrow with collection returns normally when no exceptions")
    void staticRunAndThrowCollectionReturnsNormallyWhenNoExceptions() throws Throwable {
        Collection<CleanUp> cleanUps =
                List.of(CleanUp.of((ThrowingRunnable) () -> {}), CleanUp.of((ThrowingRunnable) () -> {}));

        CleanUp.runAndThrow(cleanUps);
    }

    @Test
    @DisplayName("static runAndThrow with empty collection does nothing")
    void staticRunAndThrowCollectionWithEmptyCollection() throws Throwable {
        CleanUp.runAndThrow(List.of());
    }

    @Test
    @DisplayName("hasRun returns false before run")
    void hasRunReturnsFalseBeforeRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        assertThat(cleanup.hasRun()).isFalse();
    }

    @Test
    @DisplayName("hasRun returns true after run")
    void hasRunReturnsTrueAfterRun() {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.run();

        assertThat(cleanup.hasRun()).isTrue();
    }

    @Test
    @DisplayName("hasRun returns true after runAndThrow")
    void hasRunReturnsTrueAfterRunAndThrow() throws Throwable {
        CleanUp cleanup = CleanUp.of((ThrowingRunnable) () -> {});

        cleanup.runAndThrow();

        assertThat(cleanup.hasRun()).isTrue();
    }

    @Test
    @DisplayName("static runAndThrow with vararg skips already-run instances")
    void staticRunAndThrowVarargSkipsAlreadyRunInstances() throws Throwable {
        CleanUp a = CleanUp.of((ThrowingRunnable) () -> {});
        CleanUp b = CleanUp.of((ThrowingRunnable) () -> {});

        a.run();
        b.run();

        CleanUp.runAndThrow(a, b);
    }

    @Test
    @DisplayName("static runAndThrow with vararg collects throwables from already-run instances")
    void staticRunAndThrowVarargCollectsThrowablesFromAlreadyRunInstances() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");

        CleanUp a = CleanUp.of((ThrowingRunnable) () -> {
            throw first;
        });
        CleanUp b = CleanUp.of((ThrowingRunnable) () -> {
            throw second;
        });

        a.run();
        b.run();

        assertThatThrownBy(() -> CleanUp.runAndThrow(a, b)).isSameAs(first).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(1);
            assertThat(t.getSuppressed()[0]).isSameAs(second);
        });
    }

    @Test
    @DisplayName("static runAndThrow with vararg runs un-run instances and collects from already-run")
    void staticRunAndThrowVarargRunsUnrunAndCollectsFromAlreadyRun() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");

        CleanUp a = CleanUp.of((ThrowingRunnable) () -> {
            throw first;
        });
        CleanUp b = CleanUp.of((ThrowingRunnable) () -> {
            throw second;
        });

        a.run();

        assertThat(a.hasRun()).isTrue();
        assertThat(b.hasRun()).isFalse();

        assertThatThrownBy(() -> CleanUp.runAndThrow(a, b)).isSameAs(first).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(1);
            assertThat(t.getSuppressed()[0]).isSameAs(second);
        });

        assertThat(b.hasRun()).isTrue();
    }

    @Test
    @DisplayName("static runAndThrow with collection skips already-run instances")
    void staticRunAndThrowCollectionSkipsAlreadyRunInstances() throws Throwable {
        CleanUp a = CleanUp.of((ThrowingRunnable) () -> {});
        CleanUp b = CleanUp.of((ThrowingRunnable) () -> {});

        a.run();
        b.run();

        CleanUp.runAndThrow(List.of(a, b));
    }
}
