/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

/**
 * Delegating execution listener that routes events to descriptor-specific listeners.
 */
public class ParamixelEngineExecutionListener extends AbstractEngineExecutionListener {

    /**
     * Mapping of descriptor type to specialized execution listener.
     */
    private static final Map<Class<?>, EngineExecutionListener> engineListeners = new HashMap<>();

    static {
        engineListeners.put(ParamixelEngineDescriptor.class, new ParamixelEngineDescriptorEngineExecutionListener());
        engineListeners.put(
                ParamixelTestClassDescriptor.class, new ParamixelTestClassDescriptorEngineExecutionListener());
        engineListeners.put(
                ParamixelTestArgumentDescriptor.class, new ParamixelTestArgumentDescriptorEngineExecutionListener());
        engineListeners.put(
                ParamixelTestMethodDescriptor.class, new ParamixelTestMethodDescriptorEngineExecutionListener());
    }

    /**
     * Gets the parent class name from the descriptor hierarchy.
     *
     * @param testDescriptor the descriptor to find parent for
     * @return the parent class name, or null if not found
     */
    private String getParentClassName(final TestDescriptor testDescriptor) {
        TestDescriptor current = testDescriptor;
        while (current != null) {
            if (current instanceof ParamixelTestClassDescriptor) {
                return current.getDisplayName();
            }
            current = current.getParent().orElse(null);
        }
        return null;
    }

    /**
     * Notifies listeners that execution has started for a descriptor.
     *
     * @param testDescriptor the descriptor that started execution
     */
    public void executionStarted(final @NonNull TestDescriptor testDescriptor) {
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
        System.out.println(INFO + " " + threadName + " | START | " + testDescriptor.getUniqueId());
    }

    /**
     * Notifies listeners that a descriptor was skipped.
     *
     * @param testDescriptor the descriptor that was skipped
     * @param reason the skip reason
     */
    public void executionSkipped(final @NonNull TestDescriptor testDescriptor, @NonNull final String reason) {
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
        System.out.println(
                "[INFO] " + threadName + " | SKIPPED | " + testDescriptor.getUniqueId() + ", reason: " + reason);
    }

    /**
     * Notifies listeners that execution has finished for a descriptor.
     *
     * @param testDescriptor the descriptor that finished execution
     * @param testExecutionResult the execution result
     */
    public void executionFinished(
            final @NonNull TestDescriptor testDescriptor, final @NonNull TestExecutionResult testExecutionResult) {
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
        System.out.println(INFO + " " + threadName + " | FINISH | " + testDescriptor.getUniqueId() + ", result: "
                + testExecutionResult.getStatus());
    }
}
