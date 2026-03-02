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
 * Invokes Paramixel lifecycle and test methods via reflection.
 *
 * <p>This utility centralizes reflective invocation so the execution pipeline can:
 * <ul>
 *   <li>apply consistent accessibility behavior</li>
 *   <li>unwrap {@link InvocationTargetException} to surface user exceptions</li>
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe. It uses a concurrent cache to avoid repeated
 * {@link Method#setAccessible(boolean)} calls.
 *
 */
public final class ParamixelReflectionInvoker {

    /**
     * Cache tracking which methods have been made accessible.
     *
     * <p>The values are unused and exist only to indicate presence.
     */
    private static final ConcurrentHashMap<Method, Boolean> ACCESSIBLE_CACHE = new ConcurrentHashMap<>();

    /**
     * Prevents instantiation of this utility class.
     */
    private ParamixelReflectionInvoker() {}

    /**
     * Invokes a zero-argument static method and returns its result.
     *
     * @param method the static method to invoke; never {@code null}
     * @return the returned value (may be {@code null})
     * @throws Throwable when the invoked method throws, or when reflection fails
     */
    public static Object invokeStatic(final @NonNull Method method) throws Throwable {
        return invokeStaticMethod(method);
    }

    /**
     * Invokes a single-argument static method and returns its result.
     *
     * @param method the static method to invoke; never {@code null}
     * @param parameter the single parameter to pass; never {@code null}
     * @return the returned value (may be {@code null})
     * @throws Throwable when the invoked method throws, or when reflection fails
     */
    public static Object invokeStatic(final @NonNull Method method, final @NonNull Object parameter) throws Throwable {
        return invokeStaticMethod(method, parameter);
    }

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
     * @throws Throwable if invocation fails
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
     * @throws Throwable if invocation fails
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
     * @throws Throwable if invocation fails
     */
    public static void invokeFinalize(
            final @NonNull Method method, final @NonNull Object instance, final @NonNull ClassContext classContext)
            throws Throwable {
        invokeMethod(method, instance, classContext);
    }

    /**
     * Invokes a single-argument method and unwraps the target exception.
     *
     * <p>This method sets the target method accessible once and memoizes that state in
     * {@link #ACCESSIBLE_CACHE}.
     *
     * @param method the reflective method to invoke; never {@code null}
     * @param instance the target instance; never {@code null}
     * @param parameter the single parameter to pass; never {@code null}
     * @throws Throwable when the invoked method throws, or when reflection fails
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

    private static Object invokeStaticMethod(final @NonNull Method method, final Object... parameters)
            throws Throwable {
        try {
            if (!ACCESSIBLE_CACHE.containsKey(method)) {
                method.setAccessible(true);
                ACCESSIBLE_CACHE.put(method, Boolean.TRUE);
            }
            return method.invoke(null, parameters);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to invoke static method: " + method, e);
        }
    }
}
