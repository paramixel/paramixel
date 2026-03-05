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

package org.paramixel.engine.validation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Validates Paramixel test classes and lifecycle methods for signature compliance.
 *
 * <p>This validator enforces framework-level contracts such as visibility, return type,
 * and parameter types for methods annotated with {@link Paramixel.Test},
 * {@link Paramixel.ArgumentsCollector},
 * {@link Paramixel.BeforeEach}, {@link Paramixel.AfterEach}, {@link Paramixel.BeforeAll},
 * {@link Paramixel.AfterAll}, {@link Paramixel.Initialize}, and {@link Paramixel.Finalize}.</p>
 *
 * <p>The validator is intentionally conservative: it reports any violations as
 * {@link ValidationFailure} instances and does not perform invocation or execution.
 * Callers are expected to decide whether to skip or fail test discovery based on
 * the returned failures.</p>
 *
 * @author Douglas Hoard <doug.hoard@gmail.com>
 * @since 0.0.1
 */
public final class MethodValidator {

    /**
     * Creates a new instance.
     *
     * @since 0.0.1
     */
    private MethodValidator() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Validates a test class and its annotated methods.
     *
     * <p>This method scans all public methods on the given class and validates
     * signatures for each Paramixel lifecycle annotation encountered.</p>
     *
     * @param testClass the test class to validate
     * @return a list of validation failures; empty when the class is valid
     * @since 0.0.1
     */
    public static List<ValidationFailure> validateTestClass(final @NonNull Class<?> testClass) {
        final List<ValidationFailure> failures = new ArrayList<>();
        final List<Class<?>> classHierarchy = buildClassHierarchy(testClass);

        for (Class<?> current : classHierarchy) {
            for (Method method : current.getDeclaredMethods()) {
                final boolean isTestMethod = method.isAnnotationPresent(Paramixel.Test.class);
                final boolean isLifecycleMethod = method.isAnnotationPresent(Paramixel.Initialize.class)
                        || method.isAnnotationPresent(Paramixel.BeforeAll.class)
                        || method.isAnnotationPresent(Paramixel.BeforeEach.class)
                        || method.isAnnotationPresent(Paramixel.AfterEach.class)
                        || method.isAnnotationPresent(Paramixel.AfterAll.class)
                        || method.isAnnotationPresent(Paramixel.Finalize.class);

                if (isTestMethod) {
                    failures.addAll(validateTestMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.ArgumentsCollector.class)) {
                    failures.addAll(validateArgumentsCollectorMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.BeforeEach.class)) {
                    failures.addAll(validateBeforeEachMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.AfterEach.class)) {
                    failures.addAll(validateAfterEachMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.BeforeAll.class)) {
                    failures.addAll(validateBeforeAllMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.AfterAll.class)) {
                    failures.addAll(validateAfterAllMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.Initialize.class)) {
                    failures.addAll(validateInitializeMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.Finalize.class)) {
                    failures.addAll(validateFinalizeMethod(method));
                }
                if (method.isAnnotationPresent(Paramixel.Order.class)) {
                    failures.addAll(validateOrderAnnotationUsage(method, isTestMethod, isLifecycleMethod));
                }
            }
        }

        return failures;
    }

    /**
     * Performs buildClassHierarchy.
     *
     * @param testClass the testClass
     * @return the result
     * @since 0.0.1
     */
    private static List<Class<?>> buildClassHierarchy(final @NonNull Class<?> testClass) {
        final List<Class<?>> result = new ArrayList<>();
        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            result.add(current);
        }
        return result;
    }

    /**
     * Performs validateArgumentsCollectorMethod.
     *
     * @param method the method
     * @return the result
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateArgumentsCollectorMethod(final @NonNull Method method) {
        final List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.ArgumentsCollector method must be public: "
                    + method.getDeclaringClass().getName() + "#" + method.getName()));
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.ArgumentsCollector method must be static: "
                    + method.getDeclaringClass().getName() + "#" + method.getName()));
        }

        final boolean isCollectorDriven = method.getParameterCount() == 1
                && method.getParameterTypes()[0].equals(ArgumentsCollector.class)
                && method.getReturnType().equals(void.class);
        if (!isCollectorDriven) {
            failures.add(new ValidationFailure("Invalid @Paramixel.ArgumentsCollector method signature: "
                    + method.getDeclaringClass().getName() + "#" + method.getName()
                    + " (expected: public static void methodName(ArgumentsCollector))"));
        }

        return failures;
    }

    /**
     * Performs validateOrderAnnotationUsage.
     *
     * @param method the method
     * @param isTestMethod the isTestMethod
     * @param isLifecycleMethod the isLifecycleMethod
     * @return the result
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateOrderAnnotationUsage(
            final @NonNull Method method, final boolean isTestMethod, final boolean isLifecycleMethod) {
        final List<ValidationFailure> failures = new ArrayList<>();
        final Paramixel.Order order = method.getAnnotation(Paramixel.Order.class);
        if (order == null) {
            return failures;
        }

        if (!isTestMethod && !isLifecycleMethod) {
            failures.add(new ValidationFailure(
                    "@Paramixel.Order annotation is only allowed on @Paramixel.Test and lifecycle hook methods: "
                            + method.getDeclaringClass().getName() + "#" + method.getName()));
        }

        if (order.value() <= 0) {
            failures.add(new ValidationFailure("@Paramixel.Order value must be greater than 0 for method "
                    + method.getDeclaringClass().getName() + "#" + method.getName()));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Test} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateTestMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.Test method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.Test method " + method.getName()
                    + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("@Paramixel.Test method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.Test method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.BeforeEach} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateBeforeEachMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.BeforeEach method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.BeforeEach method " + method.getName()
                    + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(
                    new ValidationFailure("@Paramixel.BeforeEach method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(
                    new ValidationFailure("@Paramixel.BeforeEach method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.AfterEach} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateAfterEachMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.AfterEach method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.AfterEach method " + method.getName()
                    + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(
                    new ValidationFailure("@Paramixel.AfterEach method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(
                    new ValidationFailure("@Paramixel.AfterEach method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.BeforeAll} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateBeforeAllMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.BeforeAll method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.BeforeAll method " + method.getName()
                    + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(
                    new ValidationFailure("@Paramixel.BeforeAll method " + method.getName() + " must return void"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.AfterAll} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateAfterAllMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.AfterAll method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.AfterAll method " + method.getName()
                    + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("@Paramixel.AfterAll method " + method.getName() + " must return void"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Initialize} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateInitializeMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.Initialize method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ClassContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.Initialize method " + method.getName()
                    + " must accept exactly one ClassContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(
                    new ValidationFailure("@Paramixel.Initialize method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(
                    new ValidationFailure("@Paramixel.Initialize method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Finalize} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     * @since 0.0.1
     */
    private static List<ValidationFailure> validateFinalizeMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("@Paramixel.Finalize method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ClassContext.class)) {
            failures.add(new ValidationFailure("@Paramixel.Finalize method " + method.getName()
                    + " must accept exactly one ClassContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("@Paramixel.Finalize method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(
                    new ValidationFailure("@Paramixel.Finalize method " + method.getName() + " must not be static"));
        }

        return failures;
    }
}
