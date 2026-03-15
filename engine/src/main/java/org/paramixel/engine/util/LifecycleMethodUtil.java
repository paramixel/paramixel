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

package org.paramixel.engine.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.paramixel.api.Paramixel;

/**
 * Utility for discovering and ordering lifecycle methods.
 *
 * <p>This utility collects lifecycle methods from test classes,
 * handles inheritance, and sorts by @Paramixel.Order annotation.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class LifecycleMethodUtil {

    private LifecycleMethodUtil() {}

    /**
     * Gets lifecycle methods of the specified annotation type.
     *
     * @param testClass the test class; must not be null
     * @param annotationType the annotation type; must not be null
     * @return an immutable list of methods sorted by @Order then name
     */
    public static List<Method> getLifecycleMethods(
            final Class<?> testClass, final Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(testClass, "testClass must not be null");
        Objects.requireNonNull(annotationType, "annotationType must not be null");

        final List<Method> methods = Arrays.stream(testClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(annotationType))
                .filter(m -> !m.isAnnotationPresent(Paramixel.Disabled.class))
                .sorted(Comparator.comparingInt(LifecycleMethodUtil::getOrder).thenComparing(Method::getName))
                .toList();

        return Collections.unmodifiableList(methods);
    }

    /**
     * Gets test methods sorted by @Paramixel.Order then method name.
     *
     * @param testClass the test class; must not be null
     * @return an immutable list of test methods
     */
    public static List<Method> getTestMethods(final Class<?> testClass) {
        Objects.requireNonNull(testClass, "testClass must not be null");

        final List<Method> methods = Arrays.stream(testClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Paramixel.Test.class))
                .filter(m -> !m.isAnnotationPresent(Paramixel.Disabled.class))
                .sorted(Comparator.comparingInt(LifecycleMethodUtil::getOrder).thenComparing(Method::getName))
                .toList();

        return Collections.unmodifiableList(methods);
    }

    private static int getOrder(final Method method) {
        final Paramixel.Order order = method.getAnnotation(Paramixel.Order.class);
        return order != null ? order.value() : Integer.MAX_VALUE;
    }
}
