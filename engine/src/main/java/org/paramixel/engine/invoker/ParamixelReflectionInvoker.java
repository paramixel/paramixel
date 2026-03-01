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

package org.paramixel.engine.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;

/**
 * Utility class for invoking methods via reflection.
 */
public final class ParamixelReflectionInvoker {

    private static final ConcurrentHashMap<Method, Boolean> ACCESSIBLE_CACHE = new ConcurrentHashMap<>();

    /**
     * Prevents instantiation of this utility class.
     */
    private ParamixelReflectionInvoker() {}

    /**
     * Invokes an initialize method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param classContext the class context argument
     * @throws Throwable if invocation fails
     */
    public static void invokeInitialize(
            final @NonNull Method method, final @NonNull Object instance, final @NonNull ClassContext classContext)
            throws Throwable {
        invokeMethod(method, instance, classContext);
    }

    /**
     * Invokes a before-all method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param argumentContext the argument context argument
     * @throws Throwable if invocation fails
     */
    public static void invokeBeforeAll(
            final @NonNull Method method,
            final @NonNull Object instance,
            final @NonNull ArgumentContext argumentContext)
            throws Throwable {
        invokeMethod(method, instance, argumentContext);
    }

    /**
     * Invokes a before-each method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param argumentContext the argument context argument
     * @throws Throwable if invocation fails
     */
    public static void invokeBeforeEach(
            final @NonNull Method method,
            final @NonNull Object instance,
            final @NonNull ArgumentContext argumentContext)
            throws Throwable {
        invokeMethod(method, instance, argumentContext);
    }

    /**
     * Invokes a test method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param argumentContext the argument context argument
     * @throws Throwable if invocation fails
     */
    public static void invokeTestMethod(
            final @NonNull Method method,
            final @NonNull Object instance,
            final @NonNull ArgumentContext argumentContext)
            throws Throwable {
        invokeMethod(method, instance, argumentContext);
    }

    /**
     * Invokes an after-each method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param argumentContext the argument context argument
     */
    public static void invokeAfterEach(
            final @NonNull Method method,
            final @NonNull Object instance,
            final @NonNull ArgumentContext argumentContext)
            throws Throwable {
        invokeMethod(method, instance, argumentContext);
    }

    /**
     * Invokes an after-all method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param argumentContext the argument context argument
     */
    public static void invokeAfterAll(
            final @NonNull Method method,
            @NonNull final Object instance,
            @NonNull final ArgumentContext argumentContext)
            throws Throwable {
        invokeMethod(method, instance, argumentContext);
    }

    /**
     * Invokes a finalize method.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param classContext the class context argument
     */
    public static void invokeFinalize(
            final @NonNull Method method, final @NonNull Object instance, final @NonNull ClassContext classContext)
            throws Throwable {
        invokeMethod(method, instance, classContext);
    }

    /**
     * Invokes a method with the given parameter.
     *
     * @param method the method to invoke
     * @param instance the target instance
     * @param parameter the parameter to pass
     * @throws Throwable if invocation fails
     */
    private static void invokeMethod(
            final @NonNull Method method, final @NonNull Object instance, final @NonNull Object parameter)
            throws Throwable {
        try {
            if (!ACCESSIBLE_CACHE.containsKey(method)) {
                method.setAccessible(true);
                ACCESSIBLE_CACHE.put(method, Boolean.TRUE);
            }
            method.invoke(instance, parameter);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke method: " + method, e);
        }
    }
}
