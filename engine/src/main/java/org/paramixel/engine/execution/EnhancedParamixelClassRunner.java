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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.FastIdUtil;

/**
 * Enhanced class runner that uses the queue-based execution model.
 *
 * <p>This runner replaces the blocking pattern in
 * {@code executeArgumentsInlineFirst()} with non-blocking queue submission.
 *
 * <p><b>Thread safety</b>
 * <p>This class is not thread-safe and should be used per class execution.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class EnhancedParamixelClassRunner {

    /**
     * Logger used for lifecycle and execution diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(EnhancedParamixelClassRunner.class.getName());

    /**
     * Shared execution runtime used for task submission and permits.
     */
    private final EnhancedParamixelExecutionRuntime runtime;

    /**
     * Immutable engine context shared across all executed classes.
     */
    private final ConcreteEngineContext engineContext;

    /**
     * Listener notified for descriptor start/finish events.
     */
    private final EngineExecutionListener listener;

    /**
     * Map of test class to class context.
     */
    private final Map<Class<?>, ClassContext> classContexts;

    /**
     * Map of test class to instantiated test object.
     */
    private final Map<Class<?>, Object> testInstances;

    /**
     * Invocation runner for executing test methods.
     */
    private final EnhancedParamixelInvocationRunner invocationRunner;

    /**
     * Creates a new instance.
     *
     * @param runtime the runtime
     * @param engineContext the engineContext
     * @param listener the listener
     * @param classContexts the classContexts
     * @param testInstances the testInstances
     */
    public EnhancedParamixelClassRunner(
            final EnhancedParamixelExecutionRuntime runtime,
            final ConcreteEngineContext engineContext,
            final EngineExecutionListener listener,
            final Map<Class<?>, ClassContext> classContexts,
            final Map<Class<?>, Object> testInstances) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.engineContext = Objects.requireNonNull(engineContext, "engineContext must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.classContexts = Objects.requireNonNull(classContexts, "classContexts must not be null");
        this.testInstances = Objects.requireNonNull(testInstances, "testInstances must not be null");
        this.invocationRunner = new EnhancedParamixelInvocationRunner(
                runtime, listener, null, null); // Context will be set per argument
    }

    /**
     * Executes a single test class descriptor using the enhanced queue-based model.
     *
     * <p>This method reports {@link EngineExecutionListener#executionStarted(TestDescriptor)}
     * and {@link EngineExecutionListener#executionFinished(TestDescriptor, TestExecutionResult)} for
     * the class descriptor.
     *
     * @param classDescriptor the class descriptor to execute; never {@code null}
     */
    public void runTestClass(final ParamixelTestClassDescriptor classDescriptor) {
        final Class<?> testClass = classDescriptor.getTestClass();

        // Notify started on the class thread so the thread ID is correct in output
        listener.executionStarted(classDescriptor);

        final ConcreteClassContext classContext = new ConcreteClassContext(testClass, engineContext, null);
        classContexts.put(testClass, classContext);

        Object testInstance = null;
        ConcreteClassContext classContextWithInstance = null;
        boolean initializeSucceeded;
        TestExecutionResult result = TestExecutionResult.successful();

        try {
            final List<ParamixelTestArgumentDescriptor> argumentDescriptors =
                    getSortedArgumentDescriptors(classDescriptor);
            final Object[] arguments = argumentsFromDescriptors(argumentDescriptors);

            testInstance = instantiateTestClass(testClass);
            testInstances.put(testClass, testInstance);

            classContextWithInstance = new ConcreteClassContext(testClass, engineContext, testInstance);
            classContexts.put(testClass, classContextWithInstance);

            initializeSucceeded = runInitialize(testClass, classContextWithInstance, testInstance);

            if (initializeSucceeded) {
                executeArgumentsQueued(
                        classDescriptor,
                        testClass,
                        classContextWithInstance,
                        testInstance,
                        argumentDescriptors,
                        arguments);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error executing test class: " + testClass.getName(), t);
            final ConcreteClassContext contextToUse =
                    classContextWithInstance != null ? classContextWithInstance : classContext;
            contextToUse.recordFailure(t);
            result = TestExecutionResult.failed(t);
        } finally {
            final ConcreteClassContext contextToUse =
                    classContextWithInstance != null ? classContextWithInstance : classContext;

            if (testInstance != null) {
                runFinalize(testClass, contextToUse, testInstance);
                closeAutoCloseable(testInstance, "test instance", testClass.getName(), contextToUse);
            }

            final Throwable firstFailure = contextToUse.getFirstFailure();
            if (firstFailure != null) {
                LOGGER.log(Level.WARNING, "Test class failed: " + testClass.getName(), firstFailure);
                result = TestExecutionResult.failed(firstFailure);
            }

            listener.executionFinished(classDescriptor, result);
        }
    }

    /**
     * Executes arguments using the enhanced queue-based model.
     *
     * <p>This method submits all arguments immediately via the coordinator
     * without blocking on individual argument execution.
     *
     * @param classDescriptor the classDescriptor
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @param argumentDescriptors the argumentDescriptors
     * @param arguments the arguments
     */
    private void executeArgumentsQueued(
            final ParamixelTestClassDescriptor classDescriptor,
            final Class<?> testClass,
            final ConcreteClassContext classContext,
            final Object testInstance,
            final List<ParamixelTestArgumentDescriptor> argumentDescriptors,
            final Object[] arguments) {

        if (arguments.length == 0) {
            return;
        }

        // Register class with completion tracker
        runtime.coordinator().registerClass(testClass, arguments.length);

        // Submit all arguments to the coordinator
        for (int argumentIndex = 0; argumentIndex < arguments.length; argumentIndex++) {
            final ParamixelTestArgumentDescriptor argumentDescriptor = argumentDescriptors.get(argumentIndex);
            startArgumentDescriptor(argumentDescriptors, argumentIndex);

            final ExecutionTask.ArgumentExecutionTask argumentTask = createArgumentExecutionTask(
                    argumentDescriptor, classDescriptor, testClass, testInstance, classContext, argumentIndex);

            if (!runtime.submitArgument(testClass, argumentTask)) {
                LOGGER.warning("Argument submission failed for class " + testClass.getName() + " argument index "
                        + argumentIndex);
                // If submission fails, execute inline as fallback
                executeArgumentInline(
                        argumentDescriptor, classDescriptor, testClass, testInstance, classContext, argumentIndex);
            }
        }

        // Wait for all arguments to complete
        try {
            final Throwable firstFailure = runtime.coordinator().waitForClassCompletion(testClass);
            if (firstFailure != null) {
                classContext.recordFailure(firstFailure);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            classContext.recordFailure(e);
        }

        runtime.coordinator().unregisterClass(testClass);
    }

    /**
     * Creates an argument execution task.
     */
    private ExecutionTask.ArgumentExecutionTask createArgumentExecutionTask(
            final ParamixelTestArgumentDescriptor argumentDescriptor,
            final ParamixelTestClassDescriptor classDescriptor,
            final Class<?> testClass,
            final Object testInstance,
            final ConcreteClassContext classContext,
            final int argumentIndex) {

        return new ExecutionTask.ArgumentExecutionTask(
                argumentDescriptor, classDescriptor, testInstance, classContext, listener) {

            @Override
            public void execute() throws Exception {
                final Object argument = argumentDescriptor.getArgument();
                final int localArgumentIndex = argumentIndex;

                listener.executionStarted(argumentDescriptor);

                Throwable executionFailure = null;

                try {
                    // Execute @Paramixel.BeforeAll
                    final Throwable beforeAllFailure =
                            runBeforeAll(testClass, classContext, testInstance, argument, localArgumentIndex);
                    if (beforeAllFailure == null) {
                        // Execute test method invocations
                        final TestExecutionResult invocationResult =
                                invocationRunner.runInvocations(classDescriptor, argument, localArgumentIndex);
                        if (invocationResult.getStatus() == TestExecutionResult.Status.FAILED) {
                            executionFailure = invocationResult.getThrowable().orElse(null);
                        }
                    } else {
                        executionFailure = beforeAllFailure;
                    }

                } catch (Throwable t) {
                    executionFailure = t;
                    classContext.recordFailure(t);
                } finally {
                    // Always execute @Paramixel.AfterAll
                    runAfterAll(testClass, classContext, testInstance, argument, localArgumentIndex);

                    // Cleanup argument resources
                    closeAutoCloseable(argument, "argument", testClass.getName(), classContext);

                    // Report result to completion tracker
                    runtime.coordinator().markArgumentCompleted(testClass, localArgumentIndex, executionFailure);

                    final TestExecutionResult result = executionFailure == null
                            ? TestExecutionResult.successful()
                            : TestExecutionResult.failed(executionFailure);
                    listener.executionFinished(argumentDescriptor, result);
                }
            }
        };
    }

    /**
     * Executes an argument inline as fallback when queue submission fails.
     */
    private void executeArgumentInline(
            final ParamixelTestArgumentDescriptor argumentDescriptor,
            final ParamixelTestClassDescriptor classDescriptor,
            final Class<?> testClass,
            final Object testInstance,
            final ConcreteClassContext classContext,
            final int argumentIndex) {

        final Object argument = argumentDescriptor.getArgument();
        final String argumentThreadName = Thread.currentThread().getName() + "/" + FastIdUtil.getId(6);

        EnhancedParamixelExecutionRuntime.runWithThreadName(argumentThreadName, () -> {
            try {
                // Execute using traditional pattern
                final TestExecutionResult result = executeArgumentBody(
                        classDescriptor,
                        testClass,
                        classContext,
                        testInstance,
                        argument,
                        argumentIndex,
                        argumentThreadName);
                finishArgumentDescriptor(argumentDescriptor, result);
            } catch (Throwable t) {
                classContext.recordFailure(t);
                finishArgumentDescriptor(argumentDescriptor, TestExecutionResult.failed(t));
            }
        });
    }

    // === Methods migrated from ParamixelClassRunner ===

    private List<ParamixelTestArgumentDescriptor> getSortedArgumentDescriptors(
            final ParamixelTestClassDescriptor classDescriptor) {
        final List<ParamixelTestArgumentDescriptor> result = new ArrayList<>();
        for (TestDescriptor child : classDescriptor.getChildren()) {
            if (child instanceof ParamixelTestArgumentDescriptor) {
                result.add((ParamixelTestArgumentDescriptor) child);
            }
        }
        result.sort(java.util.Comparator.comparingInt(ParamixelTestArgumentDescriptor::getArgumentIndex));
        return result;
    }

    private Object[] argumentsFromDescriptors(final List<ParamixelTestArgumentDescriptor> argumentDescriptors) {
        if (argumentDescriptors.isEmpty()) {
            return new Object[] {null};
        }
        final Object[] arguments = new Object[argumentDescriptors.size()];
        for (int i = 0; i < argumentDescriptors.size(); i++) {
            arguments[i] = argumentDescriptors.get(i).getArgument();
        }
        return arguments;
    }

    private Object instantiateTestClass(final Class<?> testClass) throws Exception {
        try {
            final var constructor = testClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to instantiate test class: " + testClass.getName(), e);
            throw e;
        }
    }

    private boolean runInitialize(
            final Class<?> testClass, final ConcreteClassContext classContext, final Object testInstance) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.Initialize.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeInitialize(method, testInstance, classContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.Initialize failed", t);
                classContext.recordFailure(t);
                return false;
            }
        }
        return true;
    }

    private void runFinalize(
            final Class<?> testClass, final ConcreteClassContext classContext, final Object testInstance) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.Finalize.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeFinalize(method, testInstance, classContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.Finalize failed", t);
                classContext.recordFailure(t);
            }
        }
    }

    private Throwable runBeforeAll(
            final Class<?> testClass,
            final ConcreteClassContext classContext,
            final Object testInstance,
            final Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.BeforeAll.class);

        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeBeforeAll(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.BeforeAll failed", t);
                classContext.recordFailure(t);
                return t;
            }
        }

        return null;
    }

    private Throwable runAfterAll(
            final Class<?> testClass,
            final ConcreteClassContext classContext,
            final Object testInstance,
            final Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.AfterAll.class);
        Throwable firstFailure = null;

        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeAfterAll(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.AfterAll failed", t);
                classContext.recordFailure(t);
                if (firstFailure == null) {
                    firstFailure = t;
                }
            }
        }

        classContext.removeArgumentContext(argumentIndex);
        return firstFailure;
    }

    private List<Method> getLifecycleMethods(
            final Class<?> testClass, final Class<? extends Annotation> annotationType) {
        // Implementation matches ParamixelClassRunner.getLifecycleMethods()
        return ParamixelClassRunner.getLifecycleMethodsStatic(testClass, annotationType);
    }

    private void closeAutoCloseable(
            final Object resource,
            final String resourceName,
            final String testClassName,
            final ConcreteClassContext classContext) {
        if (!(resource instanceof AutoCloseable)) {
            return;
        }

        try {
            ((AutoCloseable) resource).close();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to close " + resourceName + " for class " + testClassName, t);
            classContext.recordFailure(t);
        }
    }

    private TestExecutionResult executeArgumentBody(
            final ParamixelTestClassDescriptor classDescriptor,
            final Class<?> testClass,
            final ConcreteClassContext classContext,
            final Object testInstance,
            final Object argument,
            final int argumentIndex,
            final String argumentThreadName) {

        final AtomicReferenceHolder<TestExecutionResult> resultHolder =
                new AtomicReferenceHolder<>(TestExecutionResult.successful());

        EnhancedParamixelExecutionRuntime.runWithThreadName(argumentThreadName, () -> {
            TestExecutionResult argumentResult;
            Throwable argumentFailure;

            final Throwable beforeAllFailure =
                    runBeforeAll(testClass, classContext, testInstance, argument, argumentIndex);
            if (beforeAllFailure == null) {
                final EnhancedParamixelInvocationRunner invocationRunner =
                        new EnhancedParamixelInvocationRunner(runtime, listener, classContext, testInstance);

                argumentResult = invocationRunner.runInvocations(classDescriptor, argument, argumentIndex);
                argumentFailure = argumentResult.getThrowable().orElse(null);
            } else {
                argumentResult = TestExecutionResult.failed(beforeAllFailure);
                argumentFailure = beforeAllFailure;
            }

            final Throwable afterAllFailure =
                    runAfterAll(testClass, classContext, testInstance, argument, argumentIndex);
            if (afterAllFailure != null && argumentFailure == null) {
                argumentFailure = afterAllFailure;
                argumentResult = TestExecutionResult.failed(afterAllFailure);
            }

            final Throwable closeFailure = null;
            closeAutoCloseable(argument, "argument", testClass.getName(), classContext);
            if (closeFailure != null && argumentFailure == null) {
                argumentFailure = closeFailure;
                argumentResult = TestExecutionResult.failed(closeFailure);
            }

            if (argumentFailure != null) {
                classContext.recordFailure(argumentFailure);
            }

            resultHolder.set(argumentResult);
        });

        return resultHolder.get();
    }

    private void startArgumentDescriptor(final List<ParamixelTestArgumentDescriptor> argumentDescriptors, final int i) {
        if (argumentDescriptors.isEmpty()) {
            return;
        }
        final ParamixelTestArgumentDescriptor descriptor = argumentDescriptors.get(i);
        listener.executionStarted(descriptor);
    }

    private void finishArgumentDescriptor(
            final ParamixelTestArgumentDescriptor argumentDescriptor, final TestExecutionResult result) {
        listener.executionFinished(argumentDescriptor, result);
    }

    /**
     * Mutable holder used to transfer a value out of a lambda (migrated from ParamixelClassRunner).
     */
    private static final class AtomicReferenceHolder<T> {
        private volatile T value;

        AtomicReferenceHolder(final T initial) {
            this.value = initial;
        }

        void set(final T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }
}
