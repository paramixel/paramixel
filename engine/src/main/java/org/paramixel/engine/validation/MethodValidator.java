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

package org.paramixel.engine.validation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Validates Paramixel test classes and lifecycle methods for signature compliance.
 *
 * <p>This validator enforces framework-level contracts such as visibility, return type,
 * and parameter types for methods annotated with {@link Paramixel.Test},
 * {@link Paramixel.BeforeEach}, {@link Paramixel.AfterEach}, {@link Paramixel.BeforeAll},
 * {@link Paramixel.AfterAll}, {@link Paramixel.Initialize}, and {@link Paramixel.Finalize}.</p>
 *
 * <p>The validator is intentionally conservative: it reports any violations as
 * {@link ValidationFailure} instances and does not perform invocation or execution.
 * Callers are expected to decide whether to skip or fail test discovery based on
 * the returned failures.</p>
 *
 * @author Douglas Hoard
 */
public class MethodValidator {

    /**
     * Validates a test class and its annotated methods.
     *
     * <p>This method scans all public methods on the given class and validates
     * signatures for each Paramixel lifecycle annotation encountered.</p>
     *
     * @param testClass the test class to validate
     * @return a list of validation failures; empty when the class is valid
     */
    public static List<ValidationFailure> validateTestClass(final @NonNull Class<?> testClass) {
        List<ValidationFailure> failures = new ArrayList<>();

        // Validate test methods
        for (Method method : testClass.getMethods()) {
            final boolean isTestMethod = method.isAnnotationPresent(Paramixel.Test.class);

            if (method.isAnnotationPresent(Paramixel.Test.class)) {
                failures.addAll(validateTestMethod(method));
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
                if (!isTestMethod) {
                    failures.add(new ValidationFailure(
                            "Order annotation is only allowed on @Test methods: " + method.getName()));
                }
                final int orderValue =
                        method.getAnnotation(Paramixel.Order.class).value();
                if (orderValue <= 0) {
                    failures.add(
                            new ValidationFailure("Order value must be greater than 0 for method " + method.getName()));
                }
            }
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Test} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateTestMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("Test method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure(
                    "Test method " + method.getName() + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("Test method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("Test method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.BeforeEach} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateBeforeEachMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("BeforeEach method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure(
                    "BeforeEach method " + method.getName() + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("BeforeEach method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("BeforeEach method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.AfterEach} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateAfterEachMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("AfterEach method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure(
                    "AfterEach method " + method.getName() + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("AfterEach method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("AfterEach method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.BeforeAll} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateBeforeAllMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("BeforeAll method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure(
                    "BeforeAll method " + method.getName() + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("BeforeAll method " + method.getName() + " must return void"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.AfterAll} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateAfterAllMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("AfterAll method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ArgumentContext.class)) {
            failures.add(new ValidationFailure(
                    "AfterAll method " + method.getName() + " must accept exactly one ArgumentContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("AfterAll method " + method.getName() + " must return void"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Initialize} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateInitializeMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("Initialize method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ClassContext.class)) {
            failures.add(new ValidationFailure(
                    "Initialize method " + method.getName() + " must accept exactly one ClassContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("Initialize method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("Initialize method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Validates an {@link Paramixel.Finalize} method.
     *
     * @param method the method to validate
     * @return validation failures for the method
     */
    private static List<ValidationFailure> validateFinalizeMethod(final @NonNull Method method) {
        List<ValidationFailure> failures = new ArrayList<>();

        if (!Modifier.isPublic(method.getModifiers())) {
            failures.add(new ValidationFailure("Finalize method " + method.getName() + " must be public"));
        }

        if (method.getParameterCount() != 1 || !method.getParameterTypes()[0].equals(ClassContext.class)) {
            failures.add(new ValidationFailure(
                    "Finalize method " + method.getName() + " must accept exactly one ClassContext parameter"));
        }

        if (!method.getReturnType().equals(void.class)) {
            failures.add(new ValidationFailure("Finalize method " + method.getName() + " must return void"));
        }

        if (Modifier.isStatic(method.getModifiers())) {
            failures.add(new ValidationFailure("Finalize method " + method.getName() + " must not be static"));
        }

        return failures;
    }

    /**
     * Represents a validation failure with a descriptive message.
     */
    public static class ValidationFailure {

        /** Human-readable validation failure message; immutable. */
        private final String message;

        /**
         * Creates a new validation failure.
         *
         * @param message the failure description
         */
        public ValidationFailure(final @NonNull String message) {
            this.message = message;
        }

        /**
         * Returns the failure description.
         *
         * @return the failure message
         */
        public String getMessage() {
            return message;
        }
    }
}
