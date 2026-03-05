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
import org.paramixel.engine.util.FastIdUtil;

/**
 * Executes one {@link ParamixelTestClassDescriptor} using a shared runtime.
 *
 * <p>This runner performs class-level responsibilities:
 * <ul>
 *   <li>Creates and manages the {@link ConcreteClassContext} lifecycle</li>
 *   <li>Instantiates the test class and executes {@link Paramixel.Initialize} and
 *       {@link Paramixel.Finalize} methods</li>
 *   <li>Coordinates argument-level execution including {@link Paramixel.BeforeAll} and
 *       {@link Paramixel.AfterAll} hooks</li>
 *   <li>Delegates method invocation to {@link ParamixelInvocationRunner}</li>
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This class is not inherently thread-safe because it mutates the provided
 * {@code classContexts} and {@code testInstances} maps. When the engine executes multiple test
 * classes concurrently, the caller must provide thread-safe map implementations.
 *
 * @author Douglas Hoard <doug.hoard@gmail.com>
 * @since 0.0.1
 */
public final class ParamixelClassRunner {

    /**
     * Logger used for lifecycle and execution diagnostics.
     *
     * @since 0.0.1
     */
    private static final Logger LOGGER = Logger.getLogger(ParamixelClassRunner.class.getName());

    /**
     * Cache of lifecycle methods by (class, annotation, order).
     *
     * <p>This cache is static to avoid repeated reflection scanning across executions.
     * It is thread-safe via {@link ConcurrentHashMap}.
     *
     * @since 0.0.1
     */
    private static final Map<LifecycleCacheKey, List<Method>> LIFECYCLE_METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * Shared execution runtime used for thread submission and permits.
     *
     * @since 0.0.1
     */
    private final ParamixelExecutionRuntime runtime;

    /**
     * Immutable engine context shared across all executed classes.
     *
     * @since 0.0.1
     */
    private final ConcreteEngineContext engineContext;

    /**
     * Listener notified for descriptor start/finish events.
     *
     * @since 0.0.1
     */
    private final EngineExecutionListener listener;

    /**
     * Map of test class to class context.
     *
     * <p>This map is mutable and is populated during execution.
     *
     * @since 0.0.1
     */
    private final Map<Class<?>, ClassContext> classContexts;

    /**
     * Map of test class to instantiated test object.
     *
     * <p>This map is mutable and is populated during execution.
     *
     * @since 0.0.1
     */
    private final Map<Class<?>, Object> testInstances;

    /**
     * Creates a new instance.
     *
     * @param runtime the runtime
     * @param engineContext the engineContext
     * @param listener the listener
     * @param classContexts the classContexts
     * @param testInstances the testInstances
     * @since 0.0.1
     */
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

    /**
     * Executes a single test class descriptor and reports the final result.
     *
     * <p>This method always reports {@link EngineExecutionListener#executionStarted(TestDescriptor)}
     * and {@link EngineExecutionListener#executionFinished(TestDescriptor, TestExecutionResult)} for
     * the class descriptor.
     *
     * <p><b>Failure handling</b>
     * <ul>
     *   <li>Records the first failure on the {@link ConcreteClassContext}.</li>
     *   <li>Ensures {@link Paramixel.Finalize} executes when an instance exists.</li>
     *   <li>Attempts to close {@link AutoCloseable} arguments and test instances.</li>
     * </ul>
     *
     * @param classDescriptor the class descriptor to execute; never {@code null}
     * @since 0.0.1
     */
    public void runTestClass(final ParamixelTestClassDescriptor classDescriptor) {
        final Class<?> testClass = classDescriptor.getTestClass();

        /**
         * Performs listener.executionStarted.
         *
         * @since 0.0.1
         */
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
            final int argumentParallelism = classDescriptor.getArgumentParallelism();

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
            /**
             * Provides this type.
             *
             * @since 0.0.1
             */
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
                /**
                 * Provides failed.
                 *
                 * @since 0.0.1
                 */
                LOGGER.log(Level.WARNING, "Test class failed: " + testClass.getName(), firstFailure);
                result = TestExecutionResult.failed(firstFailure);
            }

            listener.executionFinished(classDescriptor, result);
        }
    }

    /**
     * Performs executeArgumentsInlineFirst.
     *
     * @param classDescriptor the classDescriptor
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @param argumentDescriptors the argumentDescriptors
     * @param arguments the arguments
     * @param argumentParallelism the argumentParallelism
     * @since 0.0.1
     */
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
        final String arg0ThreadName = classThreadName + "/" + FastIdUtil.getId(6);
        final TestExecutionResult arg0Result = executeArgumentBody(
                classDescriptor, testClass, classContext, testInstance, arguments[0], 0, arg0ThreadName);
        finishArgumentDescriptor(argumentDescriptors, 0, arg0Result);

        for (int argumentIndex = 1; argumentIndex < arguments.length; argumentIndex++) {
            startArgumentDescriptor(argumentDescriptors, argumentIndex);

            final int argumentIndexCopy = argumentIndex;
            final Object argument = arguments[argumentIndexCopy];
            final String argumentThreadName = classThreadName + "/" + FastIdUtil.getId(6);

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

    /**
     * Reports {@code executionStarted} for an argument descriptor.
     *
     * <p>This method is a no-op when the class has no argument descriptors.
     *
     * @param argumentDescriptors the argument descriptors in index order; never {@code null}
     * @param i the argument index into {@code argumentDescriptors}
     * @since 0.0.1
     */
    private void startArgumentDescriptor(final List<ParamixelTestArgumentDescriptor> argumentDescriptors, final int i) {
        if (argumentDescriptors.isEmpty()) {
            return;
        }
        final ParamixelTestArgumentDescriptor descriptor = argumentDescriptors.get(i);
        listener.executionStarted(descriptor);
    }

    /**
     * Performs finishArgumentDescriptor.
     *
     * @param argumentDescriptors the argumentDescriptors
     * @param i the i
     * @param result the result
     * @since 0.0.1
     */
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

    /**
     * Performs executeArgumentBody.
     *
     * @param classDescriptor the classDescriptor
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @param argument the argument
     * @param argumentIndex the argumentIndex
     * @param argumentThreadName the argumentThreadName
     * @return the result
     * @since 0.0.1
     */
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
            TestExecutionResult argumentResult;
            Throwable argumentFailure;

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

    /**
     * Instantiates the test class using its no-arg constructor.
     *
     * <p>This method uses reflection and sets the constructor accessible.
     *
     * @param testClass the class to instantiate; never {@code null}
     * @return a new test instance; never {@code null}
     * @throws Throwable if instantiation fails for any reason
     * @since 0.0.1
     */
    private Object instantiateTestClass(final Class<?> testClass) throws Throwable {
        try {
            final var constructor = testClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            /**
             * Provides this type.
             *
             * @since 0.0.1
             */
            LOGGER.log(Level.SEVERE, "Failed to instantiate test class: " + testClass.getName(), e);
            throw e;
        }
    }

    /**
     * Performs runInitialize.
     *
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @return the result
     * @since 0.0.1
     */
    private boolean runInitialize(
            final Class<?> testClass, final ConcreteClassContext classContext, final Object testInstance) {
        /**
         * Provides this type.
         *
         * @since 0.0.1
         */
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

    /**
     * Performs runFinalize.
     *
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @since 0.0.1
     */
    private void runFinalize(
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance) {
        /**
         * Provides this type.
         *
         * @since 0.0.1
         */
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

    /**
     * Performs runBeforeAll.
     *
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @param argument the argument
     * @param argumentIndex the argumentIndex
     * @return the result
     * @since 0.0.1
     */
    private Throwable runBeforeAll(
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance,
            final @NonNull Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        /**
         * Provides this type.
         *
         * @since 0.0.1
         */
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

    /**
     * Performs runAfterAll.
     *
     * @param testClass the testClass
     * @param classContext the classContext
     * @param testInstance the testInstance
     * @param argument the argument
     * @param argumentIndex the argumentIndex
     * @return the result
     * @since 0.0.1
     */
    private Throwable runAfterAll(
            final @NonNull Class<?> testClass,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull Object testInstance,
            final @NonNull Object argument,
            final int argumentIndex) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);
        /**
         * Provides this type.
         *
         * @since 0.0.1
         */
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

    /**
     * Performs getLifecycleMethods.
     *
     * @param testClass the testClass
     * @param annotationType the annotationType
     * @return the result
     * @since 0.0.1
     */
    private List<Method> getLifecycleMethods(
            final Class<?> testClass, final Class<? extends Annotation> annotationType) {
        final LifecycleCacheKey cacheKey = new LifecycleCacheKey(testClass, annotationType);
        return LIFECYCLE_METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            final Map<String, Method> bySignature = new ConcurrentHashMap<>();
            for (Class<?> current = testClass;
                    /**
                     * Provides this type.
                     *
                     * @since 0.0.1
                     */
                    current != null && current != Object.class;
                    current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(annotationType)) {
                        continue;
                    }
                    /**
                     * Provides this type.
                     *
                     * @since 0.0.1
                     */
                    if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                        continue;
                    }

                    final String signatureKey = signatureKey(method);

                    // Most-specific (subclass) declaration wins.
                    bySignature.putIfAbsent(signatureKey, method);
                }
            }

            final List<Method> methods = new ArrayList<>(bySignature.values());
            methods.sort(
                    Comparator.comparingInt(ParamixelClassRunner::getOrderValue).thenComparing(Method::getName));
            return Collections.unmodifiableList(methods);
        });
    }

    /**
     * Performs getOrderValue.
     *
     * @param method the method
     * @return the result
     * @since 0.0.1
     */
    private static int getOrderValue(final @NonNull Method method) {
        /**
         * Provides this type.
         *
         * @since 0.0.1
         */
        final Paramixel.Order order = method.getAnnotation(Paramixel.Order.class);
        if (order == null) {
            return Integer.MAX_VALUE;
        }
        return order.value();
    }

    /**
     * Performs signatureKey.
     *
     * @param method the method
     * @return the result
     * @since 0.0.1
     */
    private static String signatureKey(final @NonNull Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append(method.getName());
        builder.append('(');
        final Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Performs closeAutoCloseable.
     *
     * @param resource the resource
     * @param resourceName the resourceName
     * @param testClassName the testClassName
     * @param classContext the classContext
     * @return the result
     * @since 0.0.1
     */
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
            /**
             * Provides this type.
             *
             * @since 0.0.1
             */
            LOGGER.log(Level.WARNING, "Failed to close " + resourceName + " for class " + testClassName, t);
            classContext.recordFailure(t);
            return t;
        }
    }

    /**
     * Performs getSortedArgumentDescriptors.
     *
     * @param classDescriptor the classDescriptor
     * @return the result
     * @since 0.0.1
     */
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

    /**
     * Extracts argument values from argument descriptors.
     *
     * <p>When no argument descriptors exist, this method returns a single-element array containing
     * {@code null}. This sentinel enables execution of non-parameterized test classes.
     *
     * @param argumentDescriptors argument descriptors in index order; never {@code null}
     * @return an argument array; never {@code null}
     * @since 0.0.1
     */
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

    /**
     * Cache key for {@link #LIFECYCLE_METHOD_CACHE}.
     *
     * <p>This type is private because it is an internal memoization detail.
     *
     * @author Douglas Hoard <doug.hoard@gmail.com>
     * @since 0.0.1
     */
    private static final class LifecycleCacheKey {

        /**
         * Root test class for the lifecycle scan; immutable.
         *
         * @since 0.0.1
         */
        private final Class<?> testClass;

        /**
         * Lifecycle annotation type used for matching; immutable.
         *
         * @since 0.0.1
         */
        private final Class<? extends Annotation> annotationType;

        /**
         * Creates a cache key.
         *
         * @param testClass the test class; never {@code null}
         * @param annotationType the lifecycle annotation type; never {@code null}
         * @return the result
         * @since 0.0.1
         */
        private LifecycleCacheKey(final Class<?> testClass, final Class<? extends Annotation> annotationType) {
            this.testClass = testClass;
            this.annotationType = annotationType;
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
            return testClass.equals(that.testClass) && annotationType.equals(that.annotationType);
        }

        @Override
        public int hashCode() {
            int result = testClass.hashCode();
            result = 31 * result + annotationType.hashCode();
            return result;
        }
    }

    /**
     * Mutable holder used to transfer a value out of a lambda.
     *
     * <p>This type is private because it is a small execution helper for
     * {@link #executeArgumentBody(ParamixelTestClassDescriptor, Class, ConcreteClassContext, Object, Object, int, String)}.
     *
     * @param <T> the held value type
     * @author Douglas Hoard <doug.hoard@gmail.com>
     * @since 0.0.1
     */
    private static final class AtomicReferenceHolder<T> {

        /**
         * The held value; mutable and published via {@code volatile}.
         *
         * @since 0.0.1
         */
        private volatile T value;

        /**
         * Creates a holder with an initial value.
         *
         * @param initial the initial value
         * @return the result
         * @since 0.0.1
         */
        private AtomicReferenceHolder(final T initial) {
            this.value = initial;
        }

        /**
         * Sets the held value.
         *
         * @param value the new value
         * @since 0.0.1
         */
        private void set(final T value) {
            this.value = value;
        }

        /**
         * Returns the current held value.
         *
         * @return the current value
         * @since 0.0.1
         */
        private T get() {
            return value;
        }
    }
}
