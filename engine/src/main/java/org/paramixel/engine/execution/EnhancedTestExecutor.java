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

package org.paramixel.engine.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.platform.engine.EngineExecutionListener;
import org.paramixel.api.ClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;

/**
 * Enhanced test executor using the new queue-based architecture.
 *
 * <p>This executor replaces the original execution pattern with
 * a producer/consumer model for better parallelism.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EnhancedTestExecutor {

    /**
     * Executes test classes using the enhanced execution runtime.
     *
     * @param classDescriptors list of test class descriptors
     * @param engineContext the engine context
     * @param engineExecutionListener the execution listener
     * @return the first test failure encountered, or {@code null} if all succeeded
     */
    public Throwable executeWithEnhancedRuntime(
            final List<ParamixelTestClassDescriptor> classDescriptors,
            final ConcreteEngineContext engineContext,
            final EngineExecutionListener engineExecutionListener) {

        final Map<Class<?>, ClassContext> classContexts = new HashMap<>();
        final Map<Class<?>, Object> testInstances = new HashMap<>();
        Throwable firstTestFailure = null;

        try (EnhancedParamixelExecutionRuntime runtime = EnhancedParamixelExecutionRuntime.createDefault()) {

            final ArgumentExecutionCoordinator coordinator = runtime.coordinator();

            // Submit all classes for execution
            for (ParamixelTestClassDescriptor classDescriptor : classDescriptors) {

                // Create execution task
                final ExecutionTask.ClassExecutionTask classTask = new ExecutionTask.ClassExecutionTask(
                        classDescriptor, engineContext, engineExecutionListener, coordinator);

                if (!runtime.submitClass(classTask)) {
                    // Queue full - handle gracefully
                    throw new RuntimeException(
                            "Unable to submit class to full queue: " + classDescriptor.getDisplayName());
                }
            }

            // Wait for all classes to complete
            for (ParamixelTestClassDescriptor classDescriptor : classDescriptors) {
                final Class<?> testClass = classDescriptor.getTestClass();
                final Throwable classFailure = coordinator.waitForClassCompletion(testClass);

                if (classFailure != null && firstTestFailure == null) {
                    firstTestFailure = classFailure;
                }

                // Clean up class resources
                coordinator.unregisterClass(testClass);
            }

        } catch (Exception e) {
            if (firstTestFailure == null) {
                firstTestFailure = e;
            }
        }

        // Check for failures recorded in class contexts
        for (ClassContext classContext : classContexts.values()) {
            if (classContext instanceof org.paramixel.engine.api.ConcreteClassContext) {
                final Throwable failure =
                        ((org.paramixel.engine.api.ConcreteClassContext) classContext).getFirstFailure();
                if (failure != null && firstTestFailure == null) {
                    firstTestFailure = failure;
                }
            }
        }

        return firstTestFailure;
    }
}
