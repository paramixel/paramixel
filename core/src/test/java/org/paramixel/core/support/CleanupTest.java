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

package org.paramixel.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.support.Cleanup.Executable;

@DisplayName("Cleanup")
class CleanupTest {

    @Test
    @DisplayName("constructor rejects null mode")
    void constructorRejectsNullMode() {
        assertThatThrownBy(() -> new Cleanup(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

    @Test
    @DisplayName("add and run executes in reverse order with REVERSE mode")
    void addAndRunExecutesInReverseOrder() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"))
                .add(() -> executionOrder.add("third"));

        runner.run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add and run executes in forward order with FORWARD mode")
    void addAndRunExecutesInForwardOrder() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"))
                .add(() -> executionOrder.add("third"));

        runner.run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add returns this for method chaining")
    void addReturnsThis() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        Cleanup result = runner.add(() -> {});

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("add rejects null executable")
    void addRejectsNullExecutable() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((Executable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("add with list registers all executables in REVERSE mode")
    void addListRegistersAllGetExecutables() {
        List<String> executionOrder = new ArrayList<>();
        List<Executable> tasks = List.of(
                () -> executionOrder.add("first"),
                () -> executionOrder.add("second"),
                () -> executionOrder.add("third"));

        new Cleanup(Cleanup.Mode.REVERSE).add(tasks).run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add with list registers all executables in FORWARD mode")
    void addListRegistersAllGetExecutablesInForwardMode() {
        List<String> executionOrder = new ArrayList<>();
        List<Executable> tasks = List.of(
                () -> executionOrder.add("first"),
                () -> executionOrder.add("second"),
                () -> executionOrder.add("third"));

        new Cleanup(Cleanup.Mode.FORWARD).add(tasks).run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add with list rejects null list")
    void addListRejectsNullList() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((List<Executable>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg registers all executables in REVERSE mode")
    void addVarargRegistersAllGetExecutables() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.REVERSE)
                .add(
                        () -> executionOrder.add("first"),
                        () -> executionOrder.add("second"),
                        () -> executionOrder.add("third"))
                .run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add with vararg registers all executables in FORWARD mode")
    void addVarargRegistersAllGetExecutablesInForwardMode() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.FORWARD)
                .add(
                        () -> executionOrder.add("first"),
                        () -> executionOrder.add("second"),
                        () -> executionOrder.add("third"))
                .run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add with vararg returns this for method chaining")
    void addVarargReturnsThis() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        Cleanup result = runner.add(() -> {}, () -> {});

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("add with vararg rejects null array")
    void addVarargRejectsNullArray() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((Executable[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg rejects null element")
    void addVarargRejectsNullElement() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add(() -> {}, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("add with vararg and no args does nothing")
    void addVarargWithNoArgsDoesNothing() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        runner.add().run();

        assertThat(runner.getCount()).isZero();
    }

    @Test
    @DisplayName("addWhen with supplier true executes executable")
    void addWhenSupplierTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.REVERSE)
                .addWhen(() -> true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with supplier false skips executable")
    void addWhenSupplierFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.REVERSE)
                .addWhen(() -> false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen with boolean true executes executable")
    void addWhenBooleanTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.REVERSE)
                .addWhen(true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with boolean false skips executable")
    void addWhenBooleanFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        new Cleanup(Cleanup.Mode.REVERSE)
                .addWhen(false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen rejects null condition supplier")
    void addWhenRejectsNullCondition() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.addWhen((Supplier<Boolean>) null, () -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("condition must not be null");
    }

    @Test
    @DisplayName("addWhen rejects null executable")
    void addWhenRejectsNullExecutable() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> runner.addWhen(() -> true, (Executable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("run collects exceptions from failing executables in FORWARD mode")
    void runCollectsExceptionsInForwardMode() {
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw new RuntimeException("error 1");
                })
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 2");
                })
                .add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.getException(0))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("error 1"));
        assertThat(result.getException(1)).isEmpty();
        assertThat(result.getException(2))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("error 2"));
        assertThat(result.getException(3)).isEmpty();
    }

    @Test
    @DisplayName("run collects exceptions from failing executables in REVERSE mode")
    void runCollectsExceptions() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 1");
                })
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 2");
                })
                .add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.getException(0)).isEmpty();
        assertThat(result.getException(1))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("error 1"));
        assertThat(result.getException(2)).isEmpty();
        assertThat(result.getException(3))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("error 2"));
        assertThat(result.getException(4)).isEmpty();
    }

    @Test
    @DisplayName("run throws on second call")
    void runThrowsOnSecondCall() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {});

        runner.run();

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cleanup has already run");
    }

    @Test
    @DisplayName("run returns CleanupResult with taskCount")
    void runReturnsCleanupResultWithTaskGetCount() {
        Cleanup runner =
                new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getExecutableCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("count returns correct size")
    void getCountReturnsCorrectSize() {
        Cleanup runner =
                new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        assertThat(runner.getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("executables returns unmodifiable list")
    void getExecutablesReturnsUnmodifiableList() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {});

        assertThatThrownBy(() -> runner.getExecutables().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getException returns exception for failed executable")
    void getExceptionReturnsExceptionForFailedTask() {
        RuntimeException expectedException = new RuntimeException("failed");
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {
            throw expectedException;
        });

        CleanupResult result = runner.run();

        assertThat(result.getException(1)).isPresent().contains(expectedException);
    }

    @Test
    @DisplayName("getException returns empty for successful executable")
    void getExceptionReturnsEmptyForSuccessfulTask() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getException(0)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("getException returns empty for out of bounds index")
    void getExceptionReturnsEmptyForOutOfBounds() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getException(-1)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("isSuccess returns true for successful executable")
    void isSuccessReturnsTrueForSuccessfulTask() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.isSuccess(0)).isTrue();
        assertThat(result.isSuccess(1)).isTrue();
    }

    @Test
    @DisplayName("isSuccess returns false for failed executable")
    void isSuccessReturnsFalseForFailedTask() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {
            throw new RuntimeException("error");
        });

        CleanupResult result = runner.run();

        assertThat(result.isSuccess(0)).isTrue();
        assertThat(result.isSuccess(1)).isFalse();
    }

    @Test
    @DisplayName("exceptionsByIndex returns only failures")
    void exceptionsByIndexReturnsOnlyFailures() {
        RuntimeException exception1 = new RuntimeException("error 1");
        RuntimeException exception2 = new RuntimeException("error 2");
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
                    throw exception1;
                })
                .add(() -> {})
                .add(() -> {
                    throw exception2;
                })
                .add(() -> {});

        CleanupResult result = runner.run();

        var exceptions = result.getExceptionsByIndex();
        assertThat(exceptions).hasSize(2);
        assertThat(exceptions.get(1)).isSameAs(exception1);
        assertThat(exceptions.get(3)).isSameAs(exception2);
    }

    @Test
    @DisplayName("exceptionsByIndex returns empty when no failures")
    void exceptionsByIndexReturnsEmptyWhenNoFailures() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getExceptionsByIndex()).isEmpty();
    }

    @Test
    @DisplayName("runAndThrow executes and throws first exception with suppressed in REVERSE mode")
    void runAndThrowExecutesAndThrowsFirstWithSuppressed() {
        RuntimeException firstException = new RuntimeException("first");
        RuntimeException secondException = new RuntimeException("second");
        RuntimeException thirdException = new RuntimeException("third");

        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE)
                .add(() -> {
                    throw thirdException;
                })
                .add(() -> {
                    throw secondException;
                })
                .add(() -> {
                    throw firstException;
                });

        assertThatThrownBy(() -> runner.runAndThrow()).isSameAs(firstException).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(2);
            assertThat(t.getSuppressed()[0]).isSameAs(secondException);
            assertThat(t.getSuppressed()[1]).isSameAs(thirdException);
        });
    }

    @Test
    @DisplayName("runAndThrow executes and throws first exception with suppressed in FORWARD mode")
    void runAndThrowExecutesAndThrowsFirstWithSuppressedInForwardMode() {
        RuntimeException firstException = new RuntimeException("first");
        RuntimeException secondException = new RuntimeException("second");
        RuntimeException thirdException = new RuntimeException("third");

        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw firstException;
                })
                .add(() -> {
                    throw secondException;
                })
                .add(() -> {
                    throw thirdException;
                });

        assertThatThrownBy(() -> runner.runAndThrow()).isSameAs(firstException).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(2);
            assertThat(t.getSuppressed()[0]).isSameAs(secondException);
            assertThat(t.getSuppressed()[1]).isSameAs(thirdException);
        });
    }

    @Test
    @DisplayName("runAndThrow does nothing when no exceptions")
    void runAndThrowDoesNothingWhenNoExceptions() throws Throwable {
        Cleanup runner =
                new Cleanup(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        runner.runAndThrow();
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in REVERSE mode")
    void runAndThrowRunsIfNotYetRun() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        runner.runAndThrow();

        assertThat(executionOrder).containsExactly("second", "first");
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in FORWARD mode")
    void runAndThrowRunsIfNotYetRunInForwardMode() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        runner.runAndThrow();

        assertThat(executionOrder).containsExactly("first", "second");
    }

    @Test
    @DisplayName("executable can throw checked exception")
    void executableCanThrowCheckedException() throws Exception {
        class CheckedException extends Exception {}

        assertThatThrownBy(() -> new Cleanup(Cleanup.Mode.REVERSE)
                        .add(() -> {
                            throw new CheckedException();
                        })
                        .runAndThrow())
                .isInstanceOf(CheckedException.class);
    }

    @Test
    @DisplayName("hasRun returns false before run")
    void hasRunReturnsFalseBeforeRun() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        assertThat(runner.hasRun()).isFalse();
    }

    @Test
    @DisplayName("hasRun returns true after run")
    void hasRunReturnsTrueAfterRun() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {});

        runner.run();

        assertThat(runner.hasRun()).isTrue();
    }

    @Test
    @DisplayName("hasRun returns false after reset")
    void hasRunReturnsFalseAfterReset() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE).add(() -> {});

        runner.run();
        runner.reset();

        assertThat(runner.hasRun()).isFalse();
    }

    @Test
    @DisplayName("reset clears executables and allows re-run")
    void resetClearsGetExecutablesAndAllowsReRun() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        runner.run();
        assertThat(executionOrder).containsExactly("first", "second");

        runner.reset();
        assertThat(runner.getCount()).isZero();

        runner.add(() -> executionOrder.add("third")).add(() -> executionOrder.add("fourth"));
        runner.run();
        assertThat(executionOrder).containsExactly("first", "second", "third", "fourth");
    }

    @Test
    @DisplayName("reset returns this for method chaining")
    void resetReturnsThis() {
        Cleanup runner = new Cleanup(Cleanup.Mode.REVERSE);

        Cleanup result = runner.reset();

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("reset on unrun runner is safe")
    void resetOnUnrunRunnerIsSafe() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD).add(() -> executionOrder.add("executed"));

        runner.reset();
        assertThat(runner.hasRun()).isFalse();

        runner.run();
        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addAutoCloseable executes close")
    void addAutoCloseableExecutesClose() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        class TestCloseable implements AutoCloseable {
            @Override
            public void close() {
                executionOrder.add("closed");
            }
        }

        TestCloseable closeable = new TestCloseable();
        new Cleanup(Cleanup.Mode.FORWARD).addCloseable(closeable).run();

        assertThat(executionOrder).containsExactly("closed");
    }

    @Test
    @DisplayName("addAutoCloseable skips null")
    void addAutoCloseableSkipsNull() throws Throwable {
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD).addCloseable((AutoCloseable) null);

        runner.run();

        assertThat(runner.getCount()).isZero();
    }

    @Test
    @DisplayName("addAutoCloseable returns this")
    void addAutoCloseableReturnsThis() {
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD);

        Cleanup result = runner.addCloseable(() -> {});

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("addAutoCloseable returns this even with null")
    void addAutoCloseableReturnsThisEvenWithNull() {
        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD);

        Cleanup result = runner.addCloseable((AutoCloseable) null);

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("addAutoCloseable collects exception")
    void addAutoCloseableCollectsException() {
        class ThrowingCloseable implements AutoCloseable {
            @Override
            public void close() throws Exception {
                throw new RuntimeException("close failed");
            }
        }

        Cleanup runner = new Cleanup(Cleanup.Mode.FORWARD).addCloseable(new ThrowingCloseable());
        CleanupResult result = runner.run();

        assertThat(result.hasExceptions()).isTrue();
        assertThat(result.getException(0))
                .isPresent()
                .hasValueSatisfying(e -> assertThat(e.getMessage()).isEqualTo("close failed"));
    }

    @Test
    @DisplayName("addAutoCloseable with forward mode executes in order")
    void addAutoCloseableWithForwardMode() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        class TestCloseable implements AutoCloseable {
            private final String name;

            TestCloseable(String name) {
                this.name = name;
            }

            @Override
            public void close() {
                executionOrder.add(name);
            }
        }

        new Cleanup(Cleanup.Mode.FORWARD)
                .addCloseable(new TestCloseable("first"))
                .addCloseable(new TestCloseable("second"))
                .addCloseable(new TestCloseable("third"))
                .run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("addAutoCloseable with reverse mode executes in reverse order")
    void addAutoCloseableWithReverseMode() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        class TestCloseable implements AutoCloseable {
            private final String name;

            TestCloseable(String name) {
                this.name = name;
            }

            @Override
            public void close() {
                executionOrder.add(name);
            }
        }

        new Cleanup(Cleanup.Mode.REVERSE)
                .addCloseable(new TestCloseable("first"))
                .addCloseable(new TestCloseable("second"))
                .addCloseable(new TestCloseable("third"))
                .run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }
}
