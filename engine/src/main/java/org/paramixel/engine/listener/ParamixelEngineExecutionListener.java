/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

/**
 * Delegates execution events to descriptor-specific listeners.
 *
 * <p>This listener acts as a router. It inspects the runtime type of a descriptor and forwards
 * events to a specialized listener for that descriptor type.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe for concurrent event dispatch. It updates shared counters via
 * {@link AbstractEngineExecutionListener.ExecutionSummary}.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelEngineExecutionListener extends AbstractEngineExecutionListener {

    /**
     * Printer used for emitting execution events.
     */
    private final Consumer<String> printer;

    /**
     * Delegate listener invoked in addition to Paramixel-specific reporting.
     */
    private final EngineExecutionListener delegate;

    /**
     * Mapping from descriptor implementation type to its specialized listener.
     *
     * <p>This map is initialized once and not mutated afterward.
     */
    private final Map<Class<?>, EngineExecutionListener> engineListeners;

    /**
     * Creates a listener that prints to standard output with a no-op delegate.
     *
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener() {
        this(
                System.out::println,
                new EngineExecutionListener() {
                    // INTENTIONALLY EMPTY
                },
                Integer.MAX_VALUE);
    }

    /**
     * Creates a listener that prints to standard output and delegates to {@code delegate}.
     *
     * @param delegate the delegate listener; never {@code null}
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener(final @NonNull EngineExecutionListener delegate) {
        this(System.out::println, delegate, Integer.MAX_VALUE);
    }

    /**
     * Creates a listener that prints to standard output and delegates to {@code delegate}.
     *
     * @param delegate the delegate listener; never {@code null}
     * @param summaryClassNameMaxLength the maximum rendered class-name length
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener(
            final @NonNull EngineExecutionListener delegate, final int summaryClassNameMaxLength) {
        this(System.out::println, delegate, summaryClassNameMaxLength);
    }

    /**
     * Creates a listener that prints to the provided printer with a no-op delegate.
     *
     * @param printer the printer to receive output lines; never {@code null}
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener(final @NonNull Consumer<String> printer) {
        this(
                Objects.requireNonNull(printer, "printer must not be null"),
                new EngineExecutionListener() {
                    // INTENTIONALLY EMPTY
                },
                Integer.MAX_VALUE);
    }

    /**
     * Creates a new instance.
     *
     * @param printer the printer
     * @param delegate the delegate
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener(
            final @NonNull Consumer<String> printer, final @NonNull EngineExecutionListener delegate) {
        this(printer, delegate, Integer.MAX_VALUE);
    }

    /**
     * Creates a new instance.
     *
     * @param printer the printer
     * @param delegate the delegate
     * @param summaryClassNameMaxLength the maximum rendered class-name length
     * @since 0.0.1
     */
    public ParamixelEngineExecutionListener(
            final @NonNull Consumer<String> printer,
            final @NonNull EngineExecutionListener delegate,
            final int summaryClassNameMaxLength) {
        this.printer = Objects.requireNonNull(printer, "printer must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.engineListeners = new HashMap<>();
        engineListeners.put(
                ParamixelEngineDescriptor.class,
                new ParamixelEngineDescriptorEngineExecutionListener(printer, summaryClassNameMaxLength));
        engineListeners.put(
                ParamixelTestClassDescriptor.class, new ParamixelTestClassDescriptorEngineExecutionListener(printer));
        engineListeners.put(
                ParamixelTestArgumentDescriptor.class,
                new ParamixelTestArgumentDescriptorEngineExecutionListener(printer));
        engineListeners.put(
                ParamixelTestMethodDescriptor.class, new ParamixelTestMethodDescriptorEngineExecutionListener(printer));
    }

    /**
     * Returns the nearest parent class display name for a descriptor.
     *
     * <p>This method walks {@link TestDescriptor#getParent()} until it finds a
     * {@link ParamixelTestClassDescriptor}.
     *
     * @param testDescriptor the descriptor to inspect; may be {@code null}
     * @return the parent class display name, or {@code null} when not found
     * @since 0.0.1
     */
    private String getParentClassName(final @NonNull TestDescriptor testDescriptor) {
        TestDescriptor current = testDescriptor;
        while (current != null) {
            if (current instanceof ParamixelTestClassDescriptor) {
                return current.getDisplayName();
            }
            current = current.getParent().orElse(null);
        }
        return null;
    }

    @Override
    public void executionStarted(final @NonNull TestDescriptor testDescriptor) {
        delegate.executionStarted(testDescriptor);

        ExecutionSummary summary = getExecutionSummary();

        if (testDescriptor instanceof ParamixelTestClassDescriptor) {
            String className = testDescriptor.getDisplayName();
            summary.setCurrentClassName(className);
        } else if (testDescriptor instanceof ParamixelTestArgumentDescriptor) {
            String className = getParentClassName(testDescriptor);
            if (className != null) {
                summary.incrementClassArgumentCount(className);
            }
        } else if (testDescriptor instanceof ParamixelTestMethodDescriptor) {
            String className = getParentClassName(testDescriptor);
            if (className != null) {
                summary.incrementClassMethodCount(className);
            }
        }

        EngineExecutionListener engineExecutionListener = engineListeners.get(testDescriptor.getClass());
        if (engineExecutionListener != null) {
            engineExecutionListener.executionStarted(testDescriptor);
            return;
        }

        String threadName = Thread.currentThread().getName();
        printer.accept(INFO + " " + threadName + " | START | " + testDescriptor.getUniqueId());
    }

    @Override
    public void executionSkipped(final @NonNull TestDescriptor testDescriptor, @NonNull final String reason) {
        delegate.executionSkipped(testDescriptor, reason);

        ExecutionSummary summary = getExecutionSummary();
        if (testDescriptor instanceof ParamixelTestClassDescriptor) {
            summary.incrementTestClassSkipped();
        } else if (testDescriptor instanceof ParamixelTestArgumentDescriptor) {
            summary.incrementTestArgumentSkipped();
        } else if (testDescriptor instanceof ParamixelTestMethodDescriptor) {
            summary.incrementTestMethodSkipped();
        }

        EngineExecutionListener engineExecutionListener = engineListeners.get(testDescriptor.getClass());
        if (engineExecutionListener != null) {
            engineExecutionListener.executionSkipped(testDescriptor, reason);
            return;
        }

        String threadName = Thread.currentThread().getName();
        printer.accept("[INFO] " + threadName + " | SKIPPED | " + testDescriptor.getUniqueId() + ", reason: " + reason);
    }

    @Override
    public void executionFinished(
            final @NonNull TestDescriptor testDescriptor, final @NonNull TestExecutionResult testExecutionResult) {
        delegate.executionFinished(testDescriptor, testExecutionResult);

        ExecutionSummary summary = getExecutionSummary();
        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL: {
                if (testDescriptor instanceof ParamixelTestClassDescriptor) {
                    summary.incrementTestClassPassed();
                    String className = testDescriptor.getDisplayName();
                    summary.incrementClassPassed(className);
                } else if (testDescriptor instanceof ParamixelTestArgumentDescriptor) {
                    summary.incrementTestArgumentPassed();
                } else if (testDescriptor instanceof ParamixelTestMethodDescriptor) {
                    summary.incrementTestMethodPassed();
                }
                break;
            }
            case FAILED: {
                if (testDescriptor instanceof ParamixelTestClassDescriptor) {
                    summary.incrementTestClassFailed();
                    String className = testDescriptor.getDisplayName();
                    summary.incrementClassFailed(className);
                } else if (testDescriptor instanceof ParamixelTestArgumentDescriptor) {
                    summary.incrementTestArgumentFailed();
                } else if (testDescriptor instanceof ParamixelTestMethodDescriptor) {
                    summary.incrementTestMethodFailed();
                }
                break;
            }
            case ABORTED: {
                if (testDescriptor instanceof ParamixelTestClassDescriptor) {
                    summary.incrementTestClassSkipped();
                } else if (testDescriptor instanceof ParamixelTestArgumentDescriptor) {
                    summary.incrementTestArgumentSkipped();
                } else if (testDescriptor instanceof ParamixelTestMethodDescriptor) {
                    summary.incrementTestMethodSkipped();
                }
                break;
            }
            default: {
                // ignore
            }
        }

        EngineExecutionListener engineExecutionListener = engineListeners.get(testDescriptor.getClass());
        if (engineExecutionListener != null) {
            engineExecutionListener.executionFinished(testDescriptor, testExecutionResult);
            return;
        }

        String threadName = Thread.currentThread().getName();
        printer.accept(INFO + " " + threadName + " | FINISH | " + testDescriptor.getUniqueId() + ", result: "
                + testExecutionResult.getStatus());
    }
}
