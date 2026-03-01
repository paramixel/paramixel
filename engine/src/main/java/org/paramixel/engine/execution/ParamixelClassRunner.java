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

package org.paramixel.engine.execution;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
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
import org.paramixel.engine.util.FastId;

/** Executes a single Paramixel test class using the shared runtime. */
public final class ParamixelClassRunner {

    private static final Logger LOGGER = Logger.getLogger(ParamixelClassRunner.class.getName());

    private static final Map<LifecycleCacheKey, List<Method>> LIFECYCLE_METHOD_CACHE = new ConcurrentHashMap<>();

    private final ParamixelExecutionRuntime runtime;
    private final ConcreteEngineContext engineContext;
    private final EngineExecutionListener listener;
    private final Map<Class<?>, ClassContext> classContexts;
    private final Map<Class<?>, Object> testInstances;

    public ParamixelClassRunner(
            final ParamixelExecutionRuntime runtime,
            final ConcreteEngineContext engineContext,
            final EngineExecutionListener listener,
            final Map<Class<?>, ClassContext> classContexts,
            final Map<Class<?>, Object> testInstances) {
        this.runtime = runtime;
        this.engineContext = engineContext;
        this.listener = listener;
        this.classContexts = classContexts;
        this.testInstances = testInstances;
    }

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
            final int argumentParallelism = getArgumentParallelism(testClass);

            testInstance = instantiateTestClass(testClass);
            testInstances.put(testClass, testInstance);

            classContextWithInstance = new ConcreteClassContext(testClass, engineContext, testInstance);
            classContexts.put(testClass, classContextWithInstance);

            initializeSucceeded = runInitialize(testClass, classContextWithInstance, testInstance);

            if (initializeSucceeded) {
                executeArgumentsInlineFirst(
                        classDescriptor,
                        testClass,
                        classContextWithInstance,
                        testInstance,
                        argumentDescriptors,
                        arguments,
                        argumentParallelism);
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

    private void executeArgumentsInlineFirst(
            final ParamixelTestClassDescriptor classDescriptor,
            final Class<?> testClass,
            final ConcreteClassContext classContext,
            final Object testInstance,
            final List<ParamixelTestArgumentDescriptor> argumentDescriptors,
            final Object[] arguments,
            final int argumentParallelism) {

        if (arguments.length == 0) {
            return;
        }

        final String classThreadName = Thread.currentThread().getName();
        final int maxAsyncArguments = Math.max(0, argumentParallelism - 1);
        int asyncStarted = 0;
        final List<Future<?>> futures = new ArrayList<>();

        // Always execute arg0 inline to guarantee progress.
        startArgumentDescriptor(argumentDescriptors, 0);
        final String arg0ThreadName = classThreadName + "/" + FastId.getId(6);
        final TestExecutionResult arg0Result = executeArgumentBody(
                classDescriptor, testClass, classContext, testInstance, arguments[0], 0, arg0ThreadName);
        finishArgumentDescriptor(argumentDescriptors, 0, arg0Result);

        for (int argumentIndex = 1; argumentIndex < arguments.length; argumentIndex++) {
            startArgumentDescriptor(argumentDescriptors, argumentIndex);

            final int argumentIndexCopy = argumentIndex;
            final Object argument = arguments[argumentIndexCopy];
            final String argumentThreadName = classThreadName + "/" + FastId.getId(6);

            if (argumentParallelism <= 1 || asyncStarted >= maxAsyncArguments) {
                final TestExecutionResult result = executeArgumentBody(
                        classDescriptor,
                        testClass,
                        classContext,
                        testInstance,
                        argument,
                        argumentIndexCopy,
                        argumentThreadName);
                finishArgumentDescriptor(argumentDescriptors, argumentIndexCopy, result);
                continue;
            }

            final Optional<ParamixelConcurrencyLimiter.ArgumentPermit> permitOpt =
                    runtime.limiter().tryAcquireArgumentExecution();
            if (permitOpt.isEmpty()) {
                final TestExecutionResult result = executeArgumentBody(
                        classDescriptor,
                        testClass,
                        classContext,
                        testInstance,
                        argument,
                        argumentIndexCopy,
                        argumentThreadName);
                finishArgumentDescriptor(argumentDescriptors, argumentIndexCopy, result);
                continue;
            }

            asyncStarted++;
            final ParamixelConcurrencyLimiter.ArgumentPermit permit = permitOpt.get();
            futures.add(runtime.submitNamed(argumentThreadName, () -> {
                try (permit) {
                    final TestExecutionResult result = executeArgumentBody(
                            classDescriptor,
                            testClass,
                            classContext,
                            testInstance,
                            argument,
                            argumentIndexCopy,
                            argumentThreadName);
                    finishArgumentDescriptor(argumentDescriptors, argumentIndexCopy, result);
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                classContext.recordFailure(e);
            }
        }
    }

    private void startArgumentDescriptor(final List<ParamixelTestArgumentDescriptor> argumentDescriptors, final int i) {
        if (argumentDescriptors.isEmpty()) {
            return;
        }
        final ParamixelTestArgumentDescriptor descriptor = argumentDescriptors.get(i);
        listener.executionStarted(descriptor);
    }

    private void finishArgumentDescriptor(
            final List<ParamixelTestArgumentDescriptor> argumentDescriptors,
            final int i,
            final TestExecutionResult result) {
        if (argumentDescriptors.isEmpty()) {
            return;
        }
        final ParamixelTestArgumentDescriptor descriptor = argumentDescriptors.get(i);
        listener.executionFinished(descriptor, result);
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

        ParamixelExecutionRuntime.runWithThreadName(argumentThreadName, () -> {
            TestExecutionResult argumentResult = TestExecutionResult.successful();
            Throwable argumentFailure = null;

            final Throwable beforeAllFailure =
                    runBeforeAll(testClass, classContext, testInstance, argument, argumentIndex);
            if (beforeAllFailure == null) {
                final ParamixelInvocationRunner invocationRunner =
                        new ParamixelInvocationRunner(runtime, listener, classContext, testInstance);

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

            final Throwable closeFailure = closeAutoCloseable(argument, "argument", testClass.getName(), classContext);
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

    private Object instantiateTestClass(final Class<?> testClass) throws Throwable {
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
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.Initialize.class, true);
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
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.Finalize.class, false);
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
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance,
            final @NonNull Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.BeforeAll.class, true);

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
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance,
            final @NonNull Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.AfterAll.class, false);
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
            final Class<?> testClass, final Class<? extends Annotation> annotationType, final boolean beforeOrder) {
        final LifecycleCacheKey cacheKey = new LifecycleCacheKey(testClass, annotationType, beforeOrder);
        return LIFECYCLE_METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            final List<Class<?>> classHierarchy = new ArrayList<>();
            for (Class<?> current = testClass;
                    current != null && current != Object.class;
                    current = current.getSuperclass()) {
                classHierarchy.add(current);
            }

            if (beforeOrder) {
                Collections.reverse(classHierarchy);
            }

            final List<Method> methods = new ArrayList<>();
            for (Class<?> current : classHierarchy) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(annotationType)) {
                        methods.add(method);
                    }
                }
            }

            return Collections.unmodifiableList(methods);
        });
    }

    private Throwable closeAutoCloseable(
            final Object resource,
            final String resourceName,
            final String testClassName,
            final ConcreteClassContext classContext) {
        if (!(resource instanceof AutoCloseable)) {
            return null;
        }

        try {
            ((AutoCloseable) resource).close();
            return null;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to close " + resourceName + " for class " + testClassName, t);
            classContext.recordFailure(t);
            return t;
        }
    }

    private List<ParamixelTestArgumentDescriptor> getSortedArgumentDescriptors(
            final ParamixelTestClassDescriptor classDescriptor) {
        final List<ParamixelTestArgumentDescriptor> result = new ArrayList<>();
        for (TestDescriptor child : classDescriptor.getChildren()) {
            if (child instanceof ParamixelTestArgumentDescriptor) {
                result.add((ParamixelTestArgumentDescriptor) child);
            }
        }
        result.sort(Comparator.comparingInt(ParamixelTestArgumentDescriptor::getArgumentIndex));
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

    private int getArgumentParallelism(final @NonNull Class<?> testClass) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Paramixel.ArgumentSupplier.class)) {
                final Paramixel.ArgumentSupplier supplier = method.getAnnotation(Paramixel.ArgumentSupplier.class);
                return supplier.parallelism();
            }
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private static final class LifecycleCacheKey {
        private final Class<?> testClass;
        private final Class<? extends Annotation> annotationType;
        private final boolean beforeOrder;

        private LifecycleCacheKey(
                final Class<?> testClass, final Class<? extends Annotation> annotationType, final boolean beforeOrder) {
            this.testClass = testClass;
            this.annotationType = annotationType;
            this.beforeOrder = beforeOrder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LifecycleCacheKey)) {
                return false;
            }
            LifecycleCacheKey that = (LifecycleCacheKey) o;
            return beforeOrder == that.beforeOrder
                    && testClass.equals(that.testClass)
                    && annotationType.equals(that.annotationType);
        }

        @Override
        public int hashCode() {
            int result = testClass.hashCode();
            result = 31 * result + annotationType.hashCode();
            result = 31 * result + (beforeOrder ? 1 : 0);
            return result;
        }
    }

    private static final class AtomicReferenceHolder<T> {
        private volatile T value;

        private AtomicReferenceHolder(final T initial) {
            this.value = initial;
        }

        private void set(final T value) {
            this.value = value;
        }

        private T get() {
            return value;
        }
    }
}
