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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.FastId;

/** Executes test method invocations for a single argument. */
public final class ParamixelInvocationRunner {

    private static final Logger LOGGER = Logger.getLogger(ParamixelInvocationRunner.class.getName());

    private static final ConcurrentHashMap<LifecycleCacheKey, List<Method>> LIFECYCLE_METHOD_CACHE =
            new ConcurrentHashMap<>();

    private final ParamixelExecutionRuntime runtime;
    private final EngineExecutionListener listener;
    private final ConcreteClassContext classContext;
    private final Object testInstance;

    public ParamixelInvocationRunner(
            final ParamixelExecutionRuntime runtime,
            final EngineExecutionListener listener,
            final ConcreteClassContext classContext,
            final Object testInstance) {
        this.runtime = runtime;
        this.listener = listener;
        this.classContext = classContext;
        this.testInstance = testInstance;
    }

    public TestExecutionResult runInvocations(
            final ParamixelTestClassDescriptor classDescriptor, final Object argument, final int argumentIndex) {
        final Class<?> testClass = classDescriptor.getTestClass();

        final List<ParamixelTestMethodDescriptor> methodDescriptors = classDescriptor.getChildren().stream()
                .filter(d -> d instanceof ParamixelTestArgumentDescriptor)
                .map(d -> (ParamixelTestArgumentDescriptor) d)
                .filter(d -> d.getArgumentIndex() == argumentIndex)
                .flatMap(d -> d.getChildren().stream())
                .filter(d -> d instanceof ParamixelTestMethodDescriptor)
                .map(d -> (ParamixelTestMethodDescriptor) d)
                .toList();

        final int parallelism = getParallelism(testClass);
        final boolean hasOrderedTests = methodDescriptors.stream()
                .anyMatch(descriptor -> descriptor.getTestMethod().isAnnotationPresent(Paramixel.Order.class));

        if (parallelism <= 1 || hasOrderedTests || methodDescriptors.size() <= 1) {
            Throwable firstFailure = null;
            for (ParamixelTestMethodDescriptor methodDescriptor : methodDescriptors) {
                final Method testMethod = methodDescriptor.getTestMethod();
                final TestExecutionResult result =
                        executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
                if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure == null) {
                    firstFailure = result.getThrowable().orElse(null);
                }
            }
            return firstFailure == null ? TestExecutionResult.successful() : TestExecutionResult.failed(firstFailure);
        }

        final AtomicReference<Throwable> firstFailure = new AtomicReference<>(null);

        final int maxAsyncInvocations = Math.max(0, parallelism - 1);
        int asyncStarted = 0;
        final List<Future<TestExecutionResult>> futures = new ArrayList<>();

        // Schedule remaining invocations first (never blocking), then execute the first inline.
        for (int i = 1; i < methodDescriptors.size(); i++) {
            final ParamixelTestMethodDescriptor methodDescriptor = methodDescriptors.get(i);
            final Method testMethod = methodDescriptor.getTestMethod();

            if (asyncStarted < maxAsyncInvocations) {
                final Optional<ParamixelConcurrencyLimiter.ArgumentPermit> permitOpt =
                        runtime.limiter().tryAcquireArgumentExecution();
                if (permitOpt.isPresent()) {
                    asyncStarted++;
                    final ParamixelConcurrencyLimiter.ArgumentPermit permit = permitOpt.get();
                    futures.add(runtime.submitNamed(FastId.getId(6), () -> {
                        try (permit) {
                            return executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
                        }
                    }));
                    continue;
                }
            }

            final TestExecutionResult result = executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
            if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
                firstFailure.set(result.getThrowable().orElse(null));
            }
        }

        final ParamixelTestMethodDescriptor firstDescriptor = methodDescriptors.get(0);
        final TestExecutionResult firstResult =
                executeInvocation(firstDescriptor, firstDescriptor.getTestMethod(), argument, argumentIndex);
        if (firstResult.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
            firstFailure.set(firstResult.getThrowable().orElse(null));
        }

        for (Future<TestExecutionResult> future : futures) {
            try {
                final TestExecutionResult result = future.get();
                if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
                    firstFailure.set(result.getThrowable().orElse(null));
                }
            } catch (Exception e) {
                if (firstFailure.get() == null) {
                    firstFailure.set(e);
                }
            }
        }

        return firstFailure.get() == null
                ? TestExecutionResult.successful()
                : TestExecutionResult.failed(firstFailure.get());
    }

    private TestExecutionResult executeInvocation(
            final ParamixelTestMethodDescriptor methodDescriptor,
            final Method testMethod,
            final Object argument,
            final int argumentIndex) {
        final Class<?> testClass = classContext.getTestClass();
        listener.executionStarted(methodDescriptor);

        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);

        Throwable invocationFailure = null;
        boolean invocationFailed = false;

        final Throwable beforeEachFailure = runBeforeEach(testClass, argumentContext);
        final boolean beforeEachSucceeded = beforeEachFailure == null;

        TestExecutionResult result = TestExecutionResult.successful();
        try {
            if (beforeEachSucceeded) {
                ParamixelReflectionInvoker.invokeTestMethod(testMethod, testInstance, argumentContext);
                classContext.incrementSuccessCount();
            } else {
                invocationFailure = beforeEachFailure;
                invocationFailed = true;
                classContext.incrementFailureCount();
                result = TestExecutionResult.failed(beforeEachFailure);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Test invocation failed: " + methodDescriptor.getUniqueId(), t);
            classContext.recordFailure(t);
            invocationFailure = t;
            if (!invocationFailed) {
                classContext.incrementFailureCount();
                invocationFailed = true;
            }
            result = TestExecutionResult.failed(t);
        } finally {
            classContext.incrementInvocationCount();

            final Throwable afterEachFailure = runAfterEach(testClass, argumentContext);
            if (afterEachFailure != null && invocationFailure == null) {
                invocationFailure = afterEachFailure;
                if (!invocationFailed) {
                    classContext.incrementFailureCount();
                }
                result = TestExecutionResult.failed(afterEachFailure);
            }

            listener.executionFinished(methodDescriptor, result);
        }

        if (invocationFailure != null) {
            classContext.recordFailure(invocationFailure);
        }

        return result;
    }

    private Throwable runBeforeEach(final Class<?> testClass, final ArgumentContext argumentContext) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.BeforeEach.class, true);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeBeforeEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "BeforeEach failed", t);
                classContext.recordFailure(t);
                return t;
            }
        }
        return null;
    }

    private Throwable runAfterEach(final Class<?> testClass, final ArgumentContext argumentContext) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.AfterEach.class, false);
        Throwable firstFailure = null;
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeAfterEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "AfterEach failed", t);
                classContext.recordFailure(t);
                if (firstFailure == null) {
                    firstFailure = t;
                }
            }
        }
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

    private int getParallelism(final Class<?> testClass) {
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
            final LifecycleCacheKey that = (LifecycleCacheKey) o;
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
}
