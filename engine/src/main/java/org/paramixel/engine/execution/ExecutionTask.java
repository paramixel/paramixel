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

import java.util.Objects;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.api.ClassContext;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;

/**
 * Base class for execution tasks in the queue-based execution model.
 *
 * <p>This hierarchy represents different types of work units that can be
 * scheduled for execution by the {@code ArgumentExecutionCoordinator}.
 *
 * <p><b>Thread safety</b>
 * <p>This class is not thread-safe. Task instances should be confined to
 * a single worker thread during execution.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public abstract class ExecutionTask {

    /**
     * The priority level of this task.
     */
    private final TaskPriority priority;

    /**
     * Creates a new execution task with the specified priority.
     *
     * @param priority the task priority; never {@code null}
     */
    protected ExecutionTask(final TaskPriority priority) {
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
    }

    /**
     * Returns the priority of this task.
     *
     * @return the task priority; never {@code null}
     */
    public TaskPriority getPriority() {
        return priority;
    }

    /**
     * Executes this task.
     *
     * @throws Exception if execution fails
     */
    public abstract void execute() throws Exception;

    /**
     * Enumeration of task priority levels.
     */
    public enum TaskPriority {
        /**
         * High priority: argument execution tasks for running classes.
         */
        HIGH,

        /**
         * Normal priority: class execution tasks.
         */
        NORMAL,

        /**
         * Low priority: cleanup and finalization tasks.
         */
        LOW;
    }

    /**
     * Task representing the execution of a test class.
     *
     * <p>This task handles class instantiation, {@code @Paramixel.Initialize},
     * argument submission, and {@code @Paramixel.Finalize} execution.
     */
    public static class ClassExecutionTask extends ExecutionTask {

        /**
         * The test class descriptor being executed.
         */
        private final ParamixelTestClassDescriptor classDescriptor;

        /**
         * The engine context for this execution.
         */
        private final ConcreteEngineContext engineContext;

        /**
         * Listener for JUnit Platform events.
         */
        private final EngineExecutionListener listener;

        /**
         * Coordinator used for submitting argument tasks.
         */
        private final ArgumentExecutionCoordinator coordinator;

        /**
         * Creates a new class execution task.
         *
         * @param classDescriptor the class descriptor; never {@code null}
         * @param engineContext the engine context; never {@code null}
         * @param listener the execution listener; never {@code null}
         * @param coordinator the argument coordinator; never {@code null}
         */
        public ClassExecutionTask(
                final ParamixelTestClassDescriptor classDescriptor,
                final ConcreteEngineContext engineContext,
                final EngineExecutionListener listener,
                final ArgumentExecutionCoordinator coordinator) {
            super(TaskPriority.NORMAL);
            this.classDescriptor = Objects.requireNonNull(classDescriptor, "classDescriptor must not be null");
            this.engineContext = Objects.requireNonNull(engineContext, "engineContext must not be null");
            this.listener = Objects.requireNonNull(listener, "listener must not be null");
            this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        }

        @Override
        public void execute() throws Exception {
            final Class<?> testClass = classDescriptor.getTestClass();
            listener.executionStarted(classDescriptor);

            final ConcreteClassContext classContext = new ConcreteClassContext(testClass, engineContext, null);

            Object testInstance = null;
            Throwable executionFailure = null;

            try {
                // Instantiate the test class
                testInstance = instantiateTestClass(testClass);

                // Update context with instance
                final ConcreteClassContext classContextWithInstance =
                        new ConcreteClassContext(testClass, engineContext, testInstance);

                // Execute @Paramixel.Initialize
                if (!executeInitialize(testClass, classContextWithInstance, testInstance)) {
                    return; // Initialize failed, abort class execution
                }

                // Submit all arguments for execution
                submitArguments(classDescriptor, classContextWithInstance, testInstance);

            } catch (Throwable t) {
                executionFailure = t;
            } finally {
                // Execute @Paramixel.Finalize and cleanup
                executeFinalize(testClass, classContext, testInstance);
                cleanupResources(testClass, testInstance);

                // Report final result
                final TestExecutionResult result = executionFailure == null
                        ? TestExecutionResult.successful()
                        : TestExecutionResult.failed(executionFailure);
                listener.executionFinished(classDescriptor, result);
            }
        }

        private Object instantiateTestClass(final Class<?> testClass) throws Exception {
            final var constructor = testClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        private boolean executeInitialize(
                final Class<?> testClass, final ConcreteClassContext classContext, final Object testInstance) {
            // Implementation will integrate with ParamixelClassRunner logic
            return true;
        }

        private void submitArguments(
                final ParamixelTestClassDescriptor classDescriptor,
                final ConcreteClassContext classContext,
                final Object testInstance) {
            // Submit all arguments to coordinator
            // Implementation will integrate with ParamixelClassRunner logic
        }

        private void executeFinalize(
                final Class<?> testClass, final ClassContext classContext, final Object testInstance) {
            // Implementation will integrate with ParamixelClassRunner logic
        }

        private void cleanupResources(final Class<?> testClass, final Object testInstance) {
            if (testInstance instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) testInstance).close();
                } catch (Exception e) {
                    // Log but don't fail
                }
            }
        }
    }

    /**
     * Task representing the execution of a single argument.
     *
     * <p>This task handles {@code @Paramixel.BeforeAll}, test method execution,
     * {@code @Paramixel.AfterAll}, and argument resource cleanup.
     */
    public static class ArgumentExecutionTask extends ExecutionTask {

        /**
         * The argument descriptor being executed.
         */
        private final ParamixelTestArgumentDescriptor argumentDescriptor;

        /**
         * The owning class descriptor.
         */
        private final ParamixelTestClassDescriptor classDescriptor;

        /**
         * The instantiated test instance.
         */
        private final Object testInstance;

        /**
         * The class context.
         */
        private final ConcreteClassContext classContext;

        /**
         * Listener for JUnit Platform events.
         */
        private final EngineExecutionListener listener;

        /**
         * Creates a new argument execution task.
         *
         * @param argumentDescriptor the argument descriptor; never {@code null}
         * @param classDescriptor the class descriptor; never {@code null}
         * @param testInstance the test instance; never {@code null}
         * @param classContext the class context; never {@code null}
         * @param listener the execution listener; never {@code null}
         */
        public ArgumentExecutionTask(
                final ParamixelTestArgumentDescriptor argumentDescriptor,
                final ParamixelTestClassDescriptor classDescriptor,
                final Object testInstance,
                final ConcreteClassContext classContext,
                final EngineExecutionListener listener) {
            super(TaskPriority.HIGH);
            this.argumentDescriptor = Objects.requireNonNull(argumentDescriptor, "argumentDescriptor must not be null");
            this.classDescriptor = Objects.requireNonNull(classDescriptor, "classDescriptor must not be null");
            this.testInstance = Objects.requireNonNull(testInstance, "testInstance must not be null");
            this.classContext = Objects.requireNonNull(classContext, "classContext must not be null");
            this.listener = Objects.requireNonNull(listener, "listener must not be null");
        }

        @Override
        public void execute() throws Exception {
            final Object argument = argumentDescriptor.getArgument();
            final int argumentIndex = argumentDescriptor.getArgumentIndex();

            listener.executionStarted(argumentDescriptor);

            Throwable executionFailure = null;

            try {
                // Execute @Paramixel.BeforeAll
                if (!executeBeforeAll(argument, argumentIndex)) {
                    executionFailure = classContext.getFirstFailure();
                }

                // Execute test methods if BeforeAll succeeded
                if (executionFailure == null) {
                    executionFailure = executeTestMethodInvocations(argument, argumentIndex);
                }

            } catch (Throwable t) {
                executionFailure = t;
                classContext.recordFailure(t);
            } finally {
                // Always execute @Paramixel.AfterAll
                executeAfterAll(argument, argumentIndex);

                // Cleanup argument resources
                cleanupArgumentResources(argument);

                // Report result
                final TestExecutionResult result = executionFailure == null
                        ? TestExecutionResult.successful()
                        : TestExecutionResult.failed(executionFailure);
                listener.executionFinished(argumentDescriptor, result);
            }
        }

        private boolean executeBeforeAll(final Object argument, final int argumentIndex) {
            // Implementation will integrate with ParamixelClassRunner logic
            return true;
        }

        private Throwable executeTestMethodInvocations(final Object argument, final int argumentIndex) {
            // Implementation will integrate with ParamixelInvocationRunner logic
            return null;
        }

        private void executeAfterAll(final Object argument, final int argumentIndex) {
            // Implementation will integrate with ParamixelClassRunner logic
        }

        private void cleanupArgumentResources(final Object argument) {
            if (argument instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) argument).close();
                } catch (Exception e) {
                    classContext.recordFailure(e);
                }
            }
        }
    }
}
