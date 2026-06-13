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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nonapi.org.paramixel.ConcreteConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("CompositeListener")
class CompositeListenerTest {

    private static final Descriptor STUB_DESCRIPTOR = new Descriptor() {

        private final Action action = Step.of("test-action", ignored -> {});

        @Override
        public Action action() {
            return action;
        }

        @Override
        public String id() {
            return "test-id";
        }

        @Override
        public boolean isPassed() {
            return true;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public boolean isSkipped() {
            return false;
        }

        @Override
        public boolean isAborted() {
            return false;
        }

        @Override
        public Optional<Instant> startedAt() {
            return Optional.of(Instant.ofEpochMilli(1L));
        }

        @Override
        public Optional<Instant> completedAt() {
            return Optional.of(Instant.ofEpochMilli(1L));
        }

        @Override
        public Optional<String> message() {
            return Optional.empty();
        }

        @Override
        public Optional<Throwable> throwable() {
            return Optional.empty();
        }

        @Override
        public boolean isCompleted() {
            return true;
        }

        @Override
        public Optional<Descriptor> parent() {
            return Optional.empty();
        }

        @Override
        public List<Descriptor> children() {
            return List.of();
        }
    };

    private static final Result STUB_RESULT = new Result() {

        @Override
        public Optional<Descriptor> descriptor() {
            return Optional.of(STUB_DESCRIPTOR);
        }

        @Override
        public boolean isPassed() {
            return true;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public boolean isSkipped() {
            return false;
        }

        @Override
        public boolean isAborted() {
            return false;
        }

        @Override
        public Optional<Instant> startedAt() {
            return Optional.empty();
        }

        @Override
        public Optional<Instant> completedAt() {
            return Optional.empty();
        }
    };

    private static final Configuration STUB_CONFIGURATION = new ConcreteConfiguration(Map.of());

    @Test
    @DisplayName("List constructor rejects null listeners")
    void listConstructorRejectsNullListeners() {
        assertThatThrownBy(() -> new CompositeListener((List<Listener>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners is null");
    }

    @Test
    @DisplayName("List constructor rejects empty listeners")
    void listConstructorRejectsEmptyListeners() {
        assertThatThrownBy(() -> new CompositeListener(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners is empty");
    }

    @Test
    @DisplayName("List constructor rejects null element")
    void listConstructorRejectsNullElement() {
        var list = new ArrayList<Listener>();
        list.add(null);
        assertThatThrownBy(() -> new CompositeListener(list))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners contains null element");
    }

    @Test
    @DisplayName("List constructor with ansiEnabled rejects null listeners")
    void listConstructorWithAnsiRejectsNullListeners() {
        assertThatThrownBy(() -> new CompositeListener((List<Listener>) null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners is null");
    }

    @Test
    @DisplayName("List constructor with ansiEnabled rejects empty listeners")
    void listConstructorWithAnsiRejectsEmptyListeners() {
        assertThatThrownBy(() -> new CompositeListener(List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners is empty");
    }

    @Test
    @DisplayName("varargs constructor rejects null listeners")
    void varargsConstructorRejectsNullListeners() {
        assertThatThrownBy(() -> new CompositeListener((Listener[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners is null");
    }

    @Test
    @DisplayName("varargs constructor rejects empty listeners")
    void varargsConstructorRejectsEmptyListeners() {
        assertThatThrownBy(() -> new CompositeListener(new Listener[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners is empty");
    }

    @Test
    @DisplayName("varargs constructor with ansiEnabled rejects null listeners")
    void varargsConstructorWithAnsiRejectsNullListeners() {
        assertThatThrownBy(() -> new CompositeListener(true, (Listener[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners is null");
    }

    @Test
    @DisplayName("varargs constructor with ansiEnabled rejects empty listeners")
    void varargsConstructorWithAnsiRejectsEmptyListeners() {
        assertThatThrownBy(() -> new CompositeListener(false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners is empty");
    }

    @Test
    @DisplayName("varargs constructor rejects null element")
    void varargsConstructorRejectsNullElement() {
        assertThatThrownBy(() -> new CompositeListener((Listener) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners contains null element");
    }

    @Test
    @DisplayName("delegates onRunStarted to all listeners")
    void delegatesOnRunStartedToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onRunStarted() {
                calls.add("l1-onRunStarted");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onRunStarted() {
                calls.add("l2-onRunStarted");
            }
        };

        new CompositeListener(l1, l2).onRunStarted();

        assertThat(calls).containsExactly("l1-onRunStarted", "l2-onRunStarted");
    }

    @Test
    @DisplayName("delegates onDiscoveryCompleted to all listeners")
    void delegatesOnDiscoveryCompletedToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onDiscoveryCompleted(Descriptor root) {
                calls.add("l1-onDiscoveryCompleted");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onDiscoveryCompleted(Descriptor root) {
                calls.add("l2-onDiscoveryCompleted");
            }
        };

        new CompositeListener(l1, l2).onDiscoveryCompleted(STUB_DESCRIPTOR);

        assertThat(calls).containsExactly("l1-onDiscoveryCompleted", "l2-onDiscoveryCompleted");
    }

    @Test
    @DisplayName("onDiscoveryCompleted rejects null root")
    void onDiscoveryCompletedRejectsNullRoot() {
        var noop = Listener.defaultListener();
        var cl = new CompositeListener(noop);

        assertThatThrownBy(() -> cl.onDiscoveryCompleted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root is null");
    }

    @Test
    @DisplayName("delegates initialize to all listeners")
    void delegatesInitializeToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void initialize(Configuration configuration) {
                calls.add("l1-initialize");
            }
        };
        var l2 = new Listener() {

            @Override
            public void initialize(Configuration configuration) {
                calls.add("l2-initialize");
            }
        };

        new CompositeListener(l1, l2).initialize(STUB_CONFIGURATION);

        assertThat(calls).containsExactly("l1-initialize", "l2-initialize");
    }

    @Test
    @DisplayName("initialize rejects null configuration")
    void initializeRejectsNullConfiguration() {
        var noop = Listener.defaultListener();
        var cl = new CompositeListener(noop);

        assertThatThrownBy(() -> cl.initialize(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("configuration is null");
    }

    @Test
    @DisplayName("delegates onDiscoveryStarted to all listeners")
    void delegatesOnDiscoveryStartedToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onDiscoveryStarted() {
                calls.add("l1-onDiscoveryStarted");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onDiscoveryStarted() {
                calls.add("l2-onDiscoveryStarted");
            }
        };

        new CompositeListener(l1, l2).onDiscoveryStarted();

        assertThat(calls).containsExactly("l1-onDiscoveryStarted", "l2-onDiscoveryStarted");
    }

    @Test
    @DisplayName("delegates onBeforeExecution to all listeners")
    void delegatesOnBeforeExecutionToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onBeforeExecution(Descriptor descriptor) {
                calls.add("l1-onBeforeExecution");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onBeforeExecution(Descriptor descriptor) {
                calls.add("l2-onBeforeExecution");
            }
        };

        new CompositeListener(l1, l2).onBeforeExecution(STUB_DESCRIPTOR);

        assertThat(calls).containsExactly("l1-onBeforeExecution", "l2-onBeforeExecution");
    }

    @Test
    @DisplayName("onBeforeExecution rejects null descriptor")
    void onBeforeExecutionRejectsNullDescriptor() {
        var noop = Listener.defaultListener();
        var cl = new CompositeListener(noop);

        assertThatThrownBy(() -> cl.onBeforeExecution(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("descriptor is null");
    }

    @Test
    @DisplayName("delegates onAfterExecution to all listeners")
    void delegatesOnAfterExecutionToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onAfterExecution(Descriptor descriptor) {
                calls.add("l1-onAfterExecution");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onAfterExecution(Descriptor descriptor) {
                calls.add("l2-onAfterExecution");
            }
        };

        new CompositeListener(l1, l2).onAfterExecution(STUB_DESCRIPTOR);

        assertThat(calls).containsExactly("l1-onAfterExecution", "l2-onAfterExecution");
    }

    @Test
    @DisplayName("onAfterExecution rejects null descriptor")
    void onAfterExecutionRejectsNullDescriptor() {
        var noop = Listener.defaultListener();
        var cl = new CompositeListener(noop);

        assertThatThrownBy(() -> cl.onAfterExecution(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("descriptor is null");
    }

    @Test
    @DisplayName("delegates onRunCompleted to all listeners")
    void delegatesOnRunCompletedToAllListeners() {
        var calls = new ArrayList<String>();
        var l1 = new Listener() {

            @Override
            public void onRunCompleted(Result result) {
                calls.add("l1-onRunCompleted");
            }
        };
        var l2 = new Listener() {

            @Override
            public void onRunCompleted(Result result) {
                calls.add("l2-onRunCompleted");
            }
        };

        new CompositeListener(l1, l2).onRunCompleted(STUB_RESULT);

        assertThat(calls).containsExactly("l1-onRunCompleted", "l2-onRunCompleted");
    }

    @Test
    @DisplayName("recoverable exception from one delegate does not prevent others from being called")
    void recoverableExceptionDoesNotPreventOtherDelegates() {
        var calls = new ArrayList<String>();
        var failing = new Listener() {

            @Override
            public void onRunStarted() {
                calls.add("failing-onRunStarted");
                throw new RuntimeException("delegate failure");
            }
        };
        var succeeding = new Listener() {

            @Override
            public void onRunStarted() {
                calls.add("succeeding-onRunStarted");
            }
        };

        var originalErr = System.err;
        var errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            new CompositeListener(failing, succeeding).onRunStarted();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(calls).containsExactly("failing-onRunStarted", "succeeding-onRunStarted");
    }

    @Test
    @DisplayName("recoverable exception from initialize does not prevent other delegates")
    void initializeContinuesOnDelegateException() {
        var calls = new ArrayList<String>();
        var failing = new Listener() {

            @Override
            public void initialize(Configuration configuration) {
                calls.add("failing-initialize");
                throw new RuntimeException("delegate failure");
            }
        };
        var succeeding = new Listener() {

            @Override
            public void initialize(Configuration configuration) {
                calls.add("succeeding-initialize");
            }
        };

        var originalErr = System.err;
        var errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            new CompositeListener(failing, succeeding).initialize(STUB_CONFIGURATION);
        } finally {
            System.setErr(originalErr);
        }

        assertThat(calls).containsExactly("failing-initialize", "succeeding-initialize");
    }

    @Test
    @DisplayName("recoverable exception from onDiscoveryStarted does not prevent other delegates")
    void onDiscoveryStartedContinuesOnDelegateException() {
        var calls = new ArrayList<String>();
        var failing = new Listener() {

            @Override
            public void onDiscoveryStarted() {
                calls.add("failing-onDiscoveryStarted");
                throw new RuntimeException("delegate failure");
            }
        };
        var succeeding = new Listener() {

            @Override
            public void onDiscoveryStarted() {
                calls.add("succeeding-onDiscoveryStarted");
            }
        };

        var originalErr = System.err;
        var errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            new CompositeListener(failing, succeeding).onDiscoveryStarted();
        } finally {
            System.setErr(originalErr);
        }

        assertThat(calls).containsExactly("failing-onDiscoveryStarted", "succeeding-onDiscoveryStarted");
    }

    @Test
    @DisplayName("logs recoverable exception to stderr with ANSI prefix when ansi is enabled")
    void logsRecoverableExceptionWithAnsiPrefix() {
        var failing = new Listener() {

            @Override
            public void onRunStarted() {
                throw new RuntimeException("delegate oops");
            }
        };

        var originalErr = System.err;
        var errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            new CompositeListener(true, failing).onRunStarted();
        } finally {
            System.setErr(originalErr);
        }

        var output = errContent.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("CompositeListener delegate Listener.onRunStarted threw exception: delegate oops");
        assertThat(output).contains(Constants.PARAMIXEL_ANSI);
    }

    @Test
    @DisplayName("logs recoverable exception to stderr with plain prefix when ansi is disabled")
    void logsRecoverableExceptionWithPlainPrefix() {
        var failing = new Listener() {

            @Override
            public void onRunStarted() {
                throw new RuntimeException("delegate oops");
            }
        };

        var originalErr = System.err;
        var errContent = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
            new CompositeListener(false, failing).onRunStarted();
        } finally {
            System.setErr(originalErr);
        }

        var output = errContent.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("CompositeListener delegate Listener.onRunStarted threw exception: delegate oops");
        assertThat(output).contains(Constants.PARAMIXEL_PLAIN);
    }

    @Test
    @DisplayName("rethrows unrecoverable error from delegate")
    void rethrowsUnrecoverableErrorFromDelegate() {
        var failing = new Listener() {

            @Override
            public void onRunStarted() {
                throw new OutOfMemoryError("simulated OOM");
            }
        };

        assertThatThrownBy(() -> new CompositeListener(failing).onRunStarted())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated OOM");
    }
}
