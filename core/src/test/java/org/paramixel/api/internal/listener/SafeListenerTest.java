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

package org.paramixel.api.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.action.Descriptor;

@DisplayName("SafeListener")
class SafeListenerTest {

    @Test
    @DisplayName("catches recoverable exception from onRunStarted and reports to stderr")
    void catchesRecoverableExceptionFromOnRunStarted() {
        var delegate = new ThrowingListener("onRunStarted", new RuntimeException("boom"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onRunStarted());

        assertThat(captured).contains("Listener.onRunStarted threw exception: boom");
    }

    @Test
    @DisplayName("catches recoverable exception from onDiscoveryCompleted and reports to stderr")
    void catchesRecoverableExceptionFromOnDiscoveryCompleted() {
        var delegate = new ThrowingListener("onDiscoveryCompleted", new RuntimeException("discovery-err"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onDiscoveryCompleted(stubDescriptor()));

        assertThat(captured).contains("Listener.onDiscoveryCompleted threw exception: discovery-err");
    }

    @Test
    @DisplayName("catches recoverable exception from onBeforeExecution and reports to stderr")
    void catchesRecoverableExceptionFromOnBeforeExecution() {
        var delegate = new ThrowingListener("onBeforeExecution", new RuntimeException("before-err"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onBeforeExecution(stubDescriptor()));

        assertThat(captured).contains("Listener.onBeforeExecution threw exception: before-err");
    }

    @Test
    @DisplayName("catches recoverable exception from onAfterExecution and reports to stderr")
    void catchesRecoverableExceptionFromOnAfterExecution() {
        var delegate = new ThrowingListener("onAfterExecution", new RuntimeException("after-err"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onAfterExecution(stubDescriptor()));

        assertThat(captured).contains("Listener.onAfterExecution threw exception: after-err");
    }

    @Test
    @DisplayName("catches recoverable exception from onRunCompleted and reports to stderr")
    void catchesRecoverableExceptionFromOnRunCompleted() {
        var delegate = new ThrowingListener("onRunCompleted", new RuntimeException("complete-err"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onRunCompleted(stubResult()));

        assertThat(captured).contains("Listener.onRunCompleted threw exception: complete-err");
    }

    @Test
    @DisplayName("uses ANSI prefix when ansiEnabled is true")
    void usesAnsiPrefixWhenEnabled() {
        var delegate = new ThrowingListener("onRunStarted", new RuntimeException("ansi-test"));
        Listener safe = new SafeListener(delegate, true);

        var captured = captureStdErr(() -> safe.onRunStarted());

        assertThat(captured).contains("PARAMIXEL");
        assertThat(captured).containsSequence("\033[", "1;34m");
    }

    @Test
    @DisplayName("uses plain prefix when ansiEnabled is false")
    void usesPlainPrefixWhenDisabled() {
        var delegate = new ThrowingListener("onRunStarted", new RuntimeException("plain-test"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onRunStarted());

        assertThat(captured).contains("[PARAMIXEL] Listener.onRunStarted");
    }

    @Test
    @DisplayName("rethrows VirtualMachineError as unrecoverable")
    void rethrowsVirtualMachineErrorAsUnrecoverable() {
        var delegate = new ThrowingListener("onRunStarted", new InternalError("vm-failure"));
        Listener safe = new SafeListener(delegate, false);

        assertThatThrownBy(safe::onRunStarted).isInstanceOf(InternalError.class);
    }

    @Test
    @DisplayName("does not rethrow StackOverflowError")
    void doesNotRethrowStackOverflowError() {
        var delegate = new ThrowingListener("onRunStarted", new StackOverflowError("stack"));
        Listener safe = new SafeListener(delegate, false);

        var captured = captureStdErr(() -> safe.onRunStarted());

        assertThat(captured).contains("Listener.onRunStarted threw exception: stack");
    }

    @Test
    @DisplayName("constructor rejects null delegate")
    void constructorRejectsNullDelegate() {
        assertThatThrownBy(() -> new SafeListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate must not be null");
    }

    @Test
    @DisplayName("two-arg constructor rejects null delegate")
    void twoArgConstructorRejectsNullDelegate() {
        assertThatThrownBy(() -> new SafeListener(null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate must not be null");
    }

    private static String captureStdErr(Runnable action) {
        var baos = new ByteArrayOutputStream();
        var original = System.err;
        try {
            System.setErr(new PrintStream(baos, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setErr(original);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static Descriptor stubDescriptor() {
        return new Descriptor() {
            @Override
            public org.paramixel.api.action.Metadata metadata() {
                return null;
            }

            @Override
            public java.util.Optional<Descriptor> parent() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<Descriptor> children() {
                return java.util.List.of();
            }
        };
    }

    private static Result stubResult() {
        return new Result() {
            @Override
            public org.paramixel.api.Status status() {
                return org.paramixel.api.Status.PASSED;
            }

            @Override
            public java.util.Optional<Descriptor> descriptor() {
                return java.util.Optional.empty();
            }
        };
    }

    private static final class ThrowingListener implements Listener {

        private final String methodName;
        private final Throwable toThrow;

        ThrowingListener(String methodName, Throwable toThrow) {
            this.methodName = methodName;
            this.toThrow = toThrow;
        }

        @Override
        public void onRunStarted() {
            if ("onRunStarted".equals(methodName)) {
                throwIfPossible();
            }
        }

        @Override
        public void onDiscoveryCompleted(Descriptor root) {
            if ("onDiscoveryCompleted".equals(methodName)) {
                throwIfPossible();
            }
        }

        @Override
        public void onBeforeExecution(Descriptor descriptor) {
            if ("onBeforeExecution".equals(methodName)) {
                throwIfPossible();
            }
        }

        @Override
        public void onAfterExecution(Descriptor descriptor) {
            if ("onAfterExecution".equals(methodName)) {
                throwIfPossible();
            }
        }

        @Override
        public void onRunCompleted(Result result) {
            if ("onRunCompleted".equals(methodName)) {
                throwIfPossible();
            }
        }

        private void throwIfPossible() {
            if (toThrow instanceof RuntimeException re) {
                throw re;
            }
            if (toThrow instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(toThrow);
        }
    }
}
