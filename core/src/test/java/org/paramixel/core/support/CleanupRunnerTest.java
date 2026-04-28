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
import org.paramixel.core.support.CleanupRunner.Executable;

@DisplayName("CleanupRunner")
class CleanupRunnerTest {

    @Test
    @DisplayName("constructor rejects null mode")
    void constructorRejectsNullMode() {
        assertThatThrownBy(() -> new CleanupRunner(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

    @Test
    @DisplayName("add and run executes in reverse order with REVERSE mode")
    void addAndRunExecutesInReverseOrder() {
        List<String> executionOrder = new ArrayList<>();
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"))
                .add(() -> executionOrder.add("third"));

        runner.run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add returns this for method chaining")
    void addReturnsThis() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        CleanupRunner result = runner.add(() -> {});

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("add rejects null executable")
    void addRejectsNullExecutable() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((Executable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("add with list registers all executables in REVERSE mode")
    void addListRegistersAllExecutables() {
        List<String> executionOrder = new ArrayList<>();
        List<Executable> tasks = List.of(
                () -> executionOrder.add("first"),
                () -> executionOrder.add("second"),
                () -> executionOrder.add("third"));

        new CleanupRunner(CleanupRunner.Mode.REVERSE).add(tasks).run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add with list registers all executables in FORWARD mode")
    void addListRegistersAllExecutablesInForwardMode() {
        List<String> executionOrder = new ArrayList<>();
        List<Executable> tasks = List.of(
                () -> executionOrder.add("first"),
                () -> executionOrder.add("second"),
                () -> executionOrder.add("third"));

        new CleanupRunner(CleanupRunner.Mode.FORWARD).add(tasks).run();

        assertThat(executionOrder).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("add with list rejects null list")
    void addListRejectsNullList() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((List<Executable>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg registers all executables in REVERSE mode")
    void addVarargRegistersAllExecutables() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(
                        () -> executionOrder.add("first"),
                        () -> executionOrder.add("second"),
                        () -> executionOrder.add("third"))
                .run();

        assertThat(executionOrder).containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("add with vararg registers all executables in FORWARD mode")
    void addVarargRegistersAllExecutablesInForwardMode() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.FORWARD)
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        CleanupRunner result = runner.add(() -> {}, () -> {});

        assertThat(result).isSameAs(runner);
    }

    @Test
    @DisplayName("add with vararg rejects null array")
    void addVarargRejectsNullArray() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add((Executable[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executables must not be null");
    }

    @Test
    @DisplayName("add with vararg rejects null element")
    void addVarargRejectsNullElement() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.add(() -> {}, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("add with vararg and no args does nothing")
    void addVarargWithNoArgsDoesNothing() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        runner.add().run();

        assertThat(runner.count()).isZero();
    }

    @Test
    @DisplayName("addWhen with supplier true executes executable")
    void addWhenSupplierTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .addWhen(() -> true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with supplier false skips executable")
    void addWhenSupplierFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .addWhen(() -> false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen with boolean true executes executable")
    void addWhenBooleanTrueExecutesAction() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .addWhen(true, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).containsExactly("executed");
    }

    @Test
    @DisplayName("addWhen with boolean false skips executable")
    void addWhenBooleanFalseSkipsAction() {
        List<String> executionOrder = new ArrayList<>();

        new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .addWhen(false, () -> executionOrder.add("executed"))
                .run();

        assertThat(executionOrder).isEmpty();
    }

    @Test
    @DisplayName("addWhen rejects null condition supplier")
    void addWhenRejectsNullCondition() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.addWhen((Supplier<Boolean>) null, () -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("condition must not be null");
    }

    @Test
    @DisplayName("addWhen rejects null executable")
    void addWhenRejectsNullExecutable() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE);

        assertThatThrownBy(() -> runner.addWhen(() -> true, (Executable) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("executable must not be null");
    }

    @Test
    @DisplayName("run collects exceptions from failing executables in FORWARD mode")
    void runCollectsExceptionsInForwardMode() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.FORWARD)
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {});

        runner.run();

        assertThatThrownBy(() -> runner.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CleanupRunner has already run");
    }

    @Test
    @DisplayName("run returns CleanupResult with taskCount")
    void runReturnsCleanupResultWithTaskCount() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {})
                .add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.taskCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("count returns correct size")
    void countReturnsCorrectSize() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {})
                .add(() -> {});

        assertThat(runner.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("executables returns unmodifiable list")
    void executablesReturnsUnmodifiableList() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {});

        assertThatThrownBy(() -> runner.executables().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getException returns exception for failed executable")
    void getExceptionReturnsExceptionForFailedTask() {
        RuntimeException expectedException = new RuntimeException("failed");
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
                    throw expectedException;
                });

        CleanupResult result = runner.run();

        assertThat(result.getException(1)).isPresent().contains(expectedException);
    }

    @Test
    @DisplayName("getException returns empty for successful executable")
    void getExceptionReturnsEmptyForSuccessfulTask() {
        CleanupRunner runner =
                new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getException(0)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("getException returns empty for out of bounds index")
    void getExceptionReturnsEmptyForOutOfBounds() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.getException(-1)).isEmpty();
        assertThat(result.getException(1)).isEmpty();
    }

    @Test
    @DisplayName("isSuccess returns true for successful executable")
    void isSuccessReturnsTrueForSuccessfulTask() {
        CleanupRunner runner =
                new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.isSuccess(0)).isTrue();
        assertThat(result.isSuccess(1)).isTrue();
    }

    @Test
    @DisplayName("isSuccess returns false for failed executable")
    void isSuccessReturnsFalseForFailedTask() {
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
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

        var exceptions = result.exceptionsByIndex();
        assertThat(exceptions).hasSize(2);
        assertThat(exceptions.get(1)).isSameAs(exception1);
        assertThat(exceptions.get(3)).isSameAs(exception2);
    }

    @Test
    @DisplayName("exceptionsByIndex returns empty when no failures")
    void exceptionsByIndexReturnsEmptyWhenNoFailures() {
        CleanupRunner runner =
                new CleanupRunner(CleanupRunner.Mode.REVERSE).add(() -> {}).add(() -> {});

        CleanupResult result = runner.run();

        assertThat(result.exceptionsByIndex()).isEmpty();
    }

    @Test
    @DisplayName("runAndThrow executes and throws first exception with suppressed in REVERSE mode")
    void runAndThrowExecutesAndThrowsFirstWithSuppressed() {
        RuntimeException firstException = new RuntimeException("first");
        RuntimeException secondException = new RuntimeException("second");
        RuntimeException thirdException = new RuntimeException("third");

        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
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

        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.FORWARD)
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
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> {})
                .add(() -> {})
                .add(() -> {});

        runner.runAndThrow();
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in REVERSE mode")
    void runAndThrowRunsIfNotYetRun() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.REVERSE)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        runner.runAndThrow();

        assertThat(executionOrder).containsExactly("second", "first");
    }

    @Test
    @DisplayName("runAndThrow runs if not yet run in FORWARD mode")
    void runAndThrowRunsIfNotYetRunInForwardMode() throws Throwable {
        List<String> executionOrder = new ArrayList<>();
        CleanupRunner runner = new CleanupRunner(CleanupRunner.Mode.FORWARD)
                .add(() -> executionOrder.add("first"))
                .add(() -> executionOrder.add("second"));

        runner.runAndThrow();

        assertThat(executionOrder).containsExactly("first", "second");
    }

    @Test
    @DisplayName("executable can throw checked exception")
    void executableCanThrowCheckedException() throws Exception {
        class CheckedException extends Exception {}

        assertThatThrownBy(() -> new CleanupRunner(CleanupRunner.Mode.REVERSE)
                        .add(() -> {
                            throw new CheckedException();
                        })
                        .runAndThrow())
                .isInstanceOf(CheckedException.class);
    }
}
