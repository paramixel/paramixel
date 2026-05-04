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
        assertThatThrownBy(() -> Cleanup.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

    @Test
    @DisplayName("add and run executes in reverse order with REVERSE mode")
    void addAndRunExecutesInReverseOrder() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"))
                .add(() -> executionOrder.add("third"));

        cleanup.run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add and run executes in forward order with FORWARD mode")
    void addAndRunExecutesInForwardOrder() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"))
                .add(() -> executionOrder.add("third"));

        cleanup.run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add returns this for method chaining")
    void addReturnsThis() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        Cleanup result = cleanup.add(() -> {});

        assertThat(result).isSameAs(cleanup);
    }

    @Test
    @DisplayName("add rejects null executable")
    void addRejectsNullExecutable() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.add((Executable) null))
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

        Cleanup.of(Cleanup.Mode.REVERSE).add(tasks).run();

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

        Cleanup.of(Cleanup.Mode.FORWARD).add(tasks).run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add with list rejects null list")
    void addListRejectsNullList() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.add((List<Executable>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg registers all executables in REVERSE mode")
    void addVarargRegistersAllGetExecutables() {
        List<String> executionOrder = new ArrayList<>();

        Cleanup.of(Cleanup.Mode.REVERSE)
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

        Cleanup.of(Cleanup.Mode.FORWARD)
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
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        Cleanup result = cleanup.add(() -> {}, () -> {});

        assertThat(result).isSameAs(cleanup);
    }

    @Test
    @DisplayName("add with vararg rejects null array")
    void addVarargRejectsNullArray() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.add((Executable[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg rejects null element")
    void addVarargRejectsNullElement() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.add(() -> {}, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("add with vararg and no args does nothing")
    void addVarargWithNoArgsDoesNothing() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        cleanup.add().run();

        assertThat(cleanup.getCount()).isZero();
    }

    @Test
    @DisplayName("addWhen with supplier true executes executable")
    void addWhenSupplierTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        Cleanup.of(Cleanup.Mode.REVERSE)
                .addWhen(() -> true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with supplier false skips executable")
    void addWhenSupplierFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        Cleanup.of(Cleanup.Mode.REVERSE)
                .addWhen(() -> false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen with boolean true executes executable")
    void addWhenBooleanTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        Cleanup.of(Cleanup.Mode.REVERSE)
                .addWhen(true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with boolean false skips executable")
    void addWhenBooleanFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        Cleanup.of(Cleanup.Mode.REVERSE)
                .addWhen(false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen rejects null condition supplier")
    void addWhenRejectsNullCondition() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.addWhen((Supplier<Boolean>) null, () -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("condition must not be null");
    }

    @Test
    @DisplayName("addWhen rejects null executable")
    void addWhenRejectsNullExecutable() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThatThrownBy(() -> cleanup.addWhen(() -> true, (Executable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("run collects exceptions from failing executables in FORWARD mode")
    void runCollectsExceptionsInForwardMode() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw new RuntimeException("error 1");
                })
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 2");
                })
                .add(() -> {});

        CleanupResult result = cleanup.run();

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
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 1");
                })
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("error 2");
                })
                .add(() -> {});

        CleanupResult result = cleanup.run();

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
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {});

        cleanup.run();

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cleanup has already run");
    }

    @Test
    @DisplayName("run returns CleanupResult with taskCount")
    void runReturnsCleanupResultWithTaskGetCount() {
        Cleanup cleanup =
                Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        CleanupResult result = cleanup.run();

        assertThat(result.getExecutableCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("count returns correct size")
    void getCountReturnsCorrectSize() {
        Cleanup cleanup =
                Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        assertThat(cleanup.getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getExecutables returns immutable snapshot")
    void getExecutablesReturnsImmutableSnapshot() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {});

        assertThatThrownBy(() -> cleanup.getExecutables().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getExecutables returns snapshot not affected by later mutations")
    void getExecutablesReturnsSnapshotNotAffectedByLaterMutations() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD);
        List<Executable> snapshot = cleanup.getExecutables();
        assertThat(snapshot).isEmpty();

        cleanup.add(() -> {});
        assertThat(snapshot).isEmpty();
        assertThat(cleanup.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getExecutables snapshot reflects executables at time of call")
    void getExecutablesSnapshotReflectsStateAtCallTime() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {}).add(() -> {});
        List<Executable> snapshot = cleanup.getExecutables();
        assertThat(snapshot).hasSize(2);
    }

    @Test
    @DisplayName("getException returns exception for failed executable")
    void getExceptionReturnsExceptionForFailedTask() {
        RuntimeException expectedException = new RuntimeException("failed");
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {
            throw expectedException;
        });

        CleanupResult result = cleanup.run();

        assertThat(result.getException(1)).isPresent().contains(expectedException);
    }

    @Test
    @DisplayName("getException returns empty for successful executable")
    void getExceptionReturnsEmptyForSuccessfulTask() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = cleanup.run();

        assertThat(result.getException(0)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("getException returns empty for out of bounds index")
    void getExceptionReturnsEmptyForOutOfBounds() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {});

        CleanupResult result = cleanup.run();

        assertThat(result.getException(-1)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("isSuccess returns true for successful executable")
    void isSuccessReturnsTrueForSuccessfulTask() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = cleanup.run();

        assertThat(result.isSuccess(0)).isTrue();
        assertThat(result.isSuccess(1)).isTrue();
    }

    @Test
    @DisplayName("isSuccess returns false for failed executable")
    void isSuccessReturnsFalseForFailedTask() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {
            throw new RuntimeException("error");
        });

        CleanupResult result = cleanup.run();

        assertThat(result.isSuccess(0)).isTrue();
        assertThat(result.isSuccess(1)).isFalse();
    }

    @Test
    @DisplayName("exceptionsByIndex returns only failures")
    void exceptionsByIndexReturnsOnlyFailures() {
        RuntimeException exception1 = new RuntimeException("error 1");
        RuntimeException exception2 = new RuntimeException("error 2");
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
                    throw exception1;
                })
                .add(() -> {})
                .add(() -> {
                    throw exception2;
                })
                .add(() -> {});

        CleanupResult result = cleanup.run();

        var exceptions = result.getExceptionsByIndex();
        assertThat(exceptions).hasSize(2);
        assertThat(exceptions.get(1)).isSameAs(exception1);
        assertThat(exceptions.get(3)).isSameAs(exception2);
    }

    @Test
    @DisplayName("exceptionsByIndex returns empty when no failures")
    void exceptionsByIndexReturnsEmptyWhenNoFailures() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = cleanup.run();

        assertThat(result.getExceptionsByIndex()).isEmpty();
    }

    @Test
    @DisplayName("runAndThrow executes and throws first exception with suppressed in REVERSE mode")
    void runAndThrowExecutesAndThrowsFirstWithSuppressed() {
        RuntimeException firstException = new RuntimeException("first");
        RuntimeException secondException = new RuntimeException("second");
        RuntimeException thirdException = new RuntimeException("third");

        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
                .add(() -> {
                    throw thirdException;
                })
                .add(() -> {
                    throw secondException;
                })
                .add(() -> {
                    throw firstException;
                });

        assertThatThrownBy(() -> cleanup.runAndThrow()).isSameAs(firstException).satisfies(t -> {
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

        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw firstException;
                })
                .add(() -> {
                    throw secondException;
                })
                .add(() -> {
                    throw thirdException;
                });

        assertThatThrownBy(() -> cleanup.runAndThrow()).isSameAs(firstException).satisfies(t -> {
            assertThat(t.getSuppressed()).hasSize(2);
            assertThat(t.getSuppressed()[0]).isSameAs(secondException);
            assertThat(t.getSuppressed()[1]).isSameAs(thirdException);
        });
    }

    @Test
    @DisplayName("runAndThrow does nothing when no exceptions")
    void runAndThrowDoesNothingWhenNoExceptions() throws Throwable {
        Cleanup cleanup =
                Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {}).add(() -> {}).add(() -> {});

        cleanup.runAndThrow();
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in REVERSE mode")
    void runAndThrowRunsIfNotYetRun() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        cleanup.runAndThrow();

        assertThat(executionOrder).containsExactly("second", "first");
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in FORWARD mode")
    void runAndThrowRunsIfNotYetRunInForwardMode() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        cleanup.runAndThrow();

        assertThat(executionOrder).containsExactly("first", "second");
    }

    @Test
    @DisplayName("executable can throw checked exception")
    void executableCanThrowCheckedException() throws Exception {
        class CheckedException extends Exception {}

        assertThatThrownBy(() -> Cleanup.of(Cleanup.Mode.REVERSE)
                        .add(() -> {
                            throw new CheckedException();
                        })
                        .runAndThrow())
                .isInstanceOf(CheckedException.class);
    }

    @Test
    @DisplayName("hasRun returns false before run")
    void hasRunReturnsFalseBeforeRun() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        assertThat(cleanup.hasRun()).isFalse();
    }

    @Test
    @DisplayName("hasRun returns true after run")
    void hasRunReturnsTrueAfterRun() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {});

        cleanup.run();

        assertThat(cleanup.hasRun()).isTrue();
    }

    @Test
    @DisplayName("hasRun returns false after reset")
    void hasRunReturnsFalseAfterReset() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE).add(() -> {});

        cleanup.run();
        cleanup.reset();

        assertThat(cleanup.hasRun()).isFalse();
    }

    @Test
    @DisplayName("reset preserves executables and allows re-run")
    void resetPreservesExecutablesAndAllowsReRun() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second");

        cleanup.reset();
        assertThat(cleanup.getCount()).isEqualTo(2);
        assertThat(cleanup.hasRun()).isFalse();

        cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second", "first", "second");
    }

    @Test
    @DisplayName("clear clears executables and allows re-run")
    void clearClearsExecutablesAndAllowsReRun() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second");

        cleanup.clear();
        assertThat(cleanup.getCount()).isZero();

        cleanup.add(() -> executionOrder.add("third")).add(() -> executionOrder.add("fourth"));
        cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second", "third", "fourth");
    }

    @Test
    @DisplayName("reset returns this for method chaining")
    void resetReturnsThis() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        Cleanup result = cleanup.reset();

        assertThat(result).isSameAs(cleanup);
    }

    @Test
    @DisplayName("clear returns this for method chaining")
    void clearReturnsThis() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.REVERSE);

        Cleanup result = cleanup.clear();

        assertThat(result).isSameAs(cleanup);
    }

    @Test
    @DisplayName("reset on unrun cleanup preserves executables")
    void resetOnUnrunCleanupPreservesExecutables() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> executionOrder.add("executed"));

        cleanup.reset();
        assertThat(cleanup.hasRun()).isFalse();

        cleanup.run();
        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("clear on unrun cleanup clears executables and resets state")
    void clearOnUnrunCleanupClearsExecutables() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> executionOrder.add("executed"));

        cleanup.clear();
        assertThat(cleanup.hasRun()).isFalse();
        assertThat(cleanup.getCount()).isZero();
    }

    @Test
    @DisplayName("clear then run is a no-op since executables are removed")
    void clearThenRunIsNoOp() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second");

        cleanup.clear();
        assertThat(cleanup.getCount()).isZero();
        assertThat(cleanup.hasRun()).isFalse();

        CleanupResult result = cleanup.run();
        assertThat(executionOrder).containsExactly("first", "second");
        assertThat(result.getExecutableCount()).isZero();
        assertThat(result.hasExceptions()).isFalse();
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
        Cleanup.of(Cleanup.Mode.FORWARD).addCloseable(closeable).run();

        assertThat(executionOrder).containsExactly("closed");
    }

    @Test
    @DisplayName("addAutoCloseable skips null")
    void addAutoCloseableSkipsNull() throws Throwable {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).addCloseable((AutoCloseable) null);

        cleanup.run();

        assertThat(cleanup.getCount()).isZero();
    }

    @Test
    @DisplayName("addAutoCloseable returns this")
    void addAutoCloseableReturnsThis() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD);

        Cleanup result = cleanup.addCloseable(() -> {});

        assertThat(result).isSameAs(cleanup);
    }

    @Test
    @DisplayName("addAutoCloseable returns this even with null")
    void addAutoCloseableReturnsThisEvenWithNull() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD);

        Cleanup result = cleanup.addCloseable((AutoCloseable) null);

        assertThat(result).isSameAs(cleanup);
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

        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).addCloseable(new ThrowingCloseable());
        CleanupResult result = cleanup.run();

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

        Cleanup.of(Cleanup.Mode.FORWARD)
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

        Cleanup.of(Cleanup.Mode.REVERSE)
                .addCloseable(new TestCloseable("first"))
                .addCloseable(new TestCloseable("second"))
                .addCloseable(new TestCloseable("third"))
                .run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("run rethrows Error")
    void runRethrowsError() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {
            throw new OutOfMemoryError("simulated oom");
        });

        assertThatThrownBy(() -> cleanup.run())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("run stops executing further tasks on Error")
    void runStopsExecutingFurtherTasksOnError() {
        List<String> executionOrder = new ArrayList<>();
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> {
                    throw new StackOverflowError("simulated soe");
                })
                .add(() -> executionOrder.add("third"));

        assertThatThrownBy(() -> cleanup.run()).isInstanceOf(StackOverflowError.class);

        assertThat(executionOrder).containsExactly("first");
    }

    @Test
    @DisplayName("run collects RuntimeException but propagates Error")
    void runCollectsRuntimeExceptionButPropagatesError() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw new RuntimeException("non-fatal");
                })
                .add(() -> {
                    throw new OutOfMemoryError("simulated oom");
                })
                .add(() -> {});

        assertThatThrownBy(() -> cleanup.run()).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("runAndThrow propagates Error")
    void runAndThrowPropagatesError() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {
            throw new OutOfMemoryError("simulated oom");
        });

        assertThatThrownBy(() -> cleanup.runAndThrow())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated oom");
    }

    @Test
    @DisplayName("CleanupResult getException returns empty for negative index")
    void cleanupResultGetExceptionReturnsEmptyForNegativeIndex() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {});
        CleanupResult result = cleanup.run();

        assertThat(result.getException(-1)).isEmpty();
    }

    @Test
    @DisplayName("CleanupResult getException returns empty for out-of-range index")
    void cleanupResultGetExceptionReturnsEmptyForOutOfRangeIndex() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {});
        CleanupResult result = cleanup.run();

        assertThat(result.getException(100)).isEmpty();
    }

    @Test
    @DisplayName("CleanupResult isSuccess returns false for negative index")
    void cleanupResultIsSuccessReturnsFalseForNegativeIndex() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {});
        CleanupResult result = cleanup.run();

        assertThat(result.isSuccess(-1)).isFalse();
    }

    @Test
    @DisplayName("CleanupResult isSuccess returns false for out-of-range index")
    void cleanupResultIsSuccessReturnsFalseForOutOfRangeIndex() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {});
        CleanupResult result = cleanup.run();

        assertThat(result.isSuccess(100)).isFalse();
    }

    @Test
    @DisplayName("CleanupResult isSuccess returns false for index with exception")
    void cleanupResultIsSuccessReturnsFalseForIndexWithException() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {
            throw new RuntimeException("fail");
        });
        CleanupResult result = cleanup.run();

        assertThat(result.isSuccess(0)).isFalse();
    }

    @Test
    @DisplayName("CleanupResult getExceptionsByIndex returns all failures")
    void cleanupResultGetExceptionsByIndexReturnsAllFailures() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD)
                .add(() -> {
                    throw new RuntimeException("first");
                })
                .add(() -> {})
                .add(() -> {
                    throw new RuntimeException("third");
                });
        CleanupResult result = cleanup.run();

        var failures = result.getExceptionsByIndex();
        assertThat(failures).hasSize(2);
        assertThat(failures.get(0)).hasMessage("first");
        assertThat(failures.get(2)).hasMessage("third");
    }

    @Test
    @DisplayName("CleanupResult hasExceptions returns false when no exceptions")
    void cleanupResultHasExceptionsReturnsFalseWhenNoExceptions() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {}).add(() -> {});
        CleanupResult result = cleanup.run();

        assertThat(result.hasExceptions()).isFalse();
    }

    @Test
    @DisplayName("CleanupResult hasExceptions returns true when exceptions present")
    void cleanupResultHasExceptionsReturnsTrueWhenExceptionsPresent() {
        Cleanup cleanup = Cleanup.of(Cleanup.Mode.FORWARD).add(() -> {
            throw new RuntimeException("fail");
        });
        CleanupResult result = cleanup.run();

        assertThat(result.hasExceptions()).isTrue();
    }
}
