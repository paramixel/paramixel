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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.Paramixel;

/**
 * Validates Paramixel annotation usage constraints that are not purely signature-based.
 *
 * <p>Examples:
 * <ul>
 *   <li>at most one {@link Paramixel.ArgumentsCollector} method per class</li>
 *   <li>{@link Paramixel.DisplayName} values must be non-blank when present</li>
 *   <li>no method may declare multiple Paramixel lifecycle/test annotations</li>
 * </ul>
 *
 */
public final class AnnotationUsageValidator {

    private static final List<Class<? extends Annotation>> MUTUALLY_EXCLUSIVE_METHOD_ANNOTATIONS = List.of(
            Paramixel.Test.class,
            Paramixel.ArgumentsCollector.class,
            Paramixel.Initialize.class,
            Paramixel.Finalize.class,
            Paramixel.BeforeAll.class,
            Paramixel.AfterAll.class,
            Paramixel.BeforeEach.class,
            Paramixel.AfterEach.class);

    private AnnotationUsageValidator() {}

    /**
     * Validates annotation usage for a test class.
     *
     * @param testClass the test class to validate
     * @return validation failures; empty when valid
     */
    public static List<ValidationFailure> validateTestClass(final @NonNull Class<?> testClass) {
        final List<ValidationFailure> failures = new ArrayList<>();

        validateDisplayName(testClass, failures);
        validateArgumentsCollectorUsage(testClass, failures);
        validateMethodAnnotationUsage(testClass, failures);
        validateTagsUsage(testClass, failures);

        return failures;
    }

    private static void validateTagsUsage(final @NonNull Class<?> testClass, final List<ValidationFailure> failures) {
        // Check if class has @Tags annotation
        final Paramixel.Tags tags = testClass.getAnnotation(Paramixel.Tags.class);
        if (tags != null) {
            // Validate that the class is annotated with @TestClass
            if (!testClass.isAnnotationPresent(Paramixel.TestClass.class)) {
                failures.add(new ValidationFailure(
                        "@Paramixel.Tags can only be used on classes annotated with @Paramixel.TestClass: "
                                + testClass.getName()));
            }

            // Validate that tags value is not empty
            if (tags.value() == null || tags.value().length == 0) {
                failures.add(new ValidationFailure(
                        "@Paramixel.Tags must have at least one tag value on class " + testClass.getName()));
            } else {
                // Validate that each tag is non-empty
                for (int i = 0; i < tags.value().length; i++) {
                    if (tags.value()[i] == null || tags.value()[i].trim().isEmpty()) {
                        failures.add(new ValidationFailure("@Paramixel.Tags tag at index " + i
                                + " must be non-empty on class " + testClass.getName()));
                    }
                }
            }
        }

        // Check for multiple @Tags annotations (only one allowed per class hierarchy)
        // This is actually enforced by the compiler - you can't declare the same annotation twice
        // But we check the class hierarchy to ensure only one exists across the hierarchy
        int tagsCount = 0;
        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            if (current.isAnnotationPresent(Paramixel.Tags.class)) {
                tagsCount++;
            }
        }
        if (tagsCount > 1) {
            failures.add(new ValidationFailure(
                    "At most one @Paramixel.Tags annotation is allowed per class hierarchy; found " + tagsCount + " on "
                            + testClass.getName()));
        }
    }

    private static void validateDisplayName(final @NonNull Class<?> testClass, final List<ValidationFailure> failures) {
        final Paramixel.DisplayName classDisplayName = testClass.getAnnotation(Paramixel.DisplayName.class);
        if (classDisplayName != null && classDisplayName.value().trim().isEmpty()) {
            failures.add(new ValidationFailure("@DisplayName value must be non-blank on class " + testClass.getName()));
        }

        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                final Paramixel.DisplayName methodDisplayName = method.getAnnotation(Paramixel.DisplayName.class);
                if (methodDisplayName != null
                        && methodDisplayName.value().trim().isEmpty()) {
                    failures.add(new ValidationFailure("@DisplayName value must be non-blank on method "
                            + current.getName() + "#" + method.getName()));
                }
            }
        }
    }

    private static void validateArgumentsCollectorUsage(
            final @NonNull Class<?> testClass, final List<ValidationFailure> failures) {
        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            final long declaredCount = Arrays.stream(current.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(Paramixel.ArgumentsCollector.class))
                    .count();
            if (declaredCount > 1) {
                failures.add(new ValidationFailure(
                        "At most one @Paramixel.ArgumentsCollector method is allowed per class; found " + declaredCount
                                + " on " + current.getName()));
            }
        }
    }

    private static void validateMethodAnnotationUsage(
            final @NonNull Class<?> testClass, final List<ValidationFailure> failures) {
        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                validateMutualExclusion(current, method, failures);
            }
        }
    }

    private static void validateMutualExclusion(
            final Class<?> declaringClass, final Method method, final List<ValidationFailure> failures) {
        final Set<Class<? extends Annotation>> present = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotationType : MUTUALLY_EXCLUSIVE_METHOD_ANNOTATIONS) {
            if (method.isAnnotationPresent(annotationType)) {
                present.add(annotationType);
            }
        }

        if (present.size() <= 1) {
            return;
        }

        final String presentNames = present.stream().map(Class::getSimpleName).collect(Collectors.joining(", "));
        failures.add(new ValidationFailure("Method " + declaringClass.getName() + "#" + method.getName()
                + " declares multiple Paramixel annotations: " + presentNames));
    }
}
