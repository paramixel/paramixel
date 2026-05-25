/*
 * Copyright (c) 2026-present Douglas Hoard
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

package org.paramixel.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.support.Arguments;

/**
 * Resolves {@link Paramixel.Id @Paramixel.Id} annotated methods on a concrete type into
 * named {@link Step} actions.
 *
 * <p>An {@code AnnotationResolver} is created for a specific type via {@link #create(Class)}.
 * Instance methods are resolved by {@link #byId(String)} and static methods by
 * {@link #staticById(String)}. The identifier serves as both the method lookup key and
 * the action display name.
 *
 * <p>Resolved methods are discovered and cached per class in thread-safe maps. Instance and
 * static methods are resolved independently; the same ID may appear on both a static and an
 * instance method within the same class.
 *
 * @param <T> the concrete type whose annotated methods are resolved
 */
public final class AnnotationResolver<T> {

    private static final ConcurrentMap<Class<?>, Map<String, Method>> CACHE = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>, Map<String, Method>> STATIC_CACHE = new ConcurrentHashMap<>();

    private final Class<T> type;

    private AnnotationResolver(final Class<T> type) {
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a new resolver for the given concrete type.
     *
     * @param type the concrete type whose annotated methods to resolve; must not be {@code null}
     * @param <T> the concrete type
     * @return a new resolver; never {@code null}
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public static <T> AnnotationResolver<T> create(final Class<T> type) {
        return new AnnotationResolver<>(type);
    }

    /**
     * Resolves an instance method annotated with {@link Paramixel.Id @Paramixel.Id} by
     * identifier and returns a named step action that invokes it.
     *
     * <p>The identifier is used both to discover the annotated method and as the action display
     * name. Resolution occurs eagerly at invocation time.
     *
     * @param id the method identifier; must not be {@code null} or blank
     * @return a step action that invokes the resolved method on the instance; never {@code null}
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank, no matching method is found,
     *     multiple methods share the same identifier, or the annotated method has an
     *     unsupported signature
     */
    public Action<T> byId(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        Arguments.requireNonBlank(id, "id must not be blank");

        Map<String, Method> methods = CACHE.computeIfAbsent(type, AnnotationResolver::discover);
        Method method = methods.get(id);
        if (method == null) {
            throw new IllegalArgumentException(
                    "no method annotated with @Paramixel.Id(\"" + id + "\") was found on " + type.getName());
        }

        return Step.of(id, instance -> {
            try {
                method.invoke(instance);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause);
            }
        });
    }

    /**
     * Resolves an instance method annotated with {@link Paramixel.Id @Paramixel.Id} by
     * identifier and returns a named step action with a custom kind that invokes it.
     *
     * <p>The identifier is used to discover the annotated method and as the action display
     * name. The kind overrides the default {@code "Step"} kind in the returned action.
     * Resolution occurs eagerly at invocation time.
     *
     * @param id the method identifier; must not be {@code null} or blank
     * @param kind the action kind; must not be {@code null} or blank
     * @return a step action that invokes the resolved method on the instance; never {@code null}
     * @throws NullPointerException if {@code id} or {@code kind} is {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code kind} is blank, no matching
     *     method is found, multiple methods share the same identifier, or the annotated
     *     method has an unsupported signature
     */
    public Action<T> byId(final String id, final String kind) {
        Objects.requireNonNull(id, "id must not be null");
        Arguments.requireNonBlank(id, "id must not be blank");
        Objects.requireNonNull(kind, "kind must not be null");
        Arguments.requireNonBlank(kind, "kind must not be blank");

        Map<String, Method> methods = CACHE.computeIfAbsent(type, AnnotationResolver::discover);
        Method method = methods.get(id);
        if (method == null) {
            throw new IllegalArgumentException(
                    "no method annotated with @Paramixel.Id(\"" + id + "\") was found on " + type.getName());
        }

        return Step.of(id, kind, instance -> {
            try {
                method.invoke(instance);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause);
            }
        });
    }

    /**
     * Resolves a static method annotated with {@link Paramixel.Id @Paramixel.Id} by
     * identifier and returns a named step action that invokes it.
     *
     * <p>The identifier is used both to discover the annotated static method and as the action
     * display name. Resolution occurs eagerly at invocation time.
     *
     * @param id the method identifier; must not be {@code null} or blank
     * @return a step action that invokes the resolved static method; never {@code null}
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank, no matching method is found,
     *     multiple methods share the same identifier, or the annotated method has an
     *     unsupported signature
     */
    public Action<?> staticById(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        Arguments.requireNonBlank(id, "id must not be blank");

        Map<String, Method> methods = STATIC_CACHE.computeIfAbsent(type, AnnotationResolver::discoverStatic);
        Method method = methods.get(id);
        if (method == null) {
            throw new IllegalArgumentException(
                    "no static method annotated with @Paramixel.Id(\"" + id + "\") was found on " + type.getName());
        }

        ThrowingRunnable runnable = () -> {
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause);
            }
        };

        return Step.of(id, ctx -> runnable.run());
    }

    /**
     * Resolves a static method annotated with {@link Paramixel.Id @Paramixel.Id} by
     * identifier and returns a named step action with a custom kind that invokes it.
     *
     * <p>The identifier is used to discover the annotated static method and as the action
     * display name. The kind overrides the default {@code "Step"} kind in the returned
     * action. Resolution occurs eagerly at invocation time.
     *
     * @param id the method identifier; must not be {@code null} or blank
     * @param kind the action kind; must not be {@code null} or blank
     * @return a step action that invokes the resolved static method; never {@code null}
     * @throws NullPointerException if {@code id} or {@code kind} is {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code kind} is blank, no matching
     *     method is found, multiple methods share the same identifier, or the annotated
     *     method has an unsupported signature
     */
    public Action<?> staticById(final String id, final String kind) {
        Objects.requireNonNull(id, "id must not be null");
        Arguments.requireNonBlank(id, "id must not be blank");
        Objects.requireNonNull(kind, "kind must not be null");
        Arguments.requireNonBlank(kind, "kind must not be blank");

        Map<String, Method> methods = STATIC_CACHE.computeIfAbsent(type, AnnotationResolver::discoverStatic);
        Method method = methods.get(id);
        if (method == null) {
            throw new IllegalArgumentException(
                    "no static method annotated with @Paramixel.Id(\"" + id + "\") was found on " + type.getName());
        }

        ThrowingRunnable runnable = () -> {
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause);
            }
        };

        return Step.of(id, kind, ctx -> runnable.run());
    }

    /**
     * Invalidates the resolver cache for the given type, allowing reflection to be re-run.
     *
     * @param type the type whose cache entry should be cleared; must not be {@code null}
     */
    public static void clearCache(final Class<?> type) {
        CACHE.remove(type);
        STATIC_CACHE.remove(type);
    }

    /**
     * Invalidates all resolver cache entries.
     */
    public static void clearAllCache() {
        CACHE.clear();
        STATIC_CACHE.clear();
    }

    private static Map<String, Method> discover(final Class<?> type) {
        var result = new HashMap<String, Method>();
        var duplicateTracker = new HashMap<String, String>();

        for (Method method : type.getMethods()) {
            Paramixel.Id idAnnotation = method.getAnnotation(Paramixel.Id.class);
            if (idAnnotation == null) {
                continue;
            }

            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            String id = idAnnotation.value();

            validateMethodSignature(method, id);

            method.trySetAccessible();

            String existing = duplicateTracker.putIfAbsent(id, method.toGenericString());
            if (existing != null) {
                throw new IllegalArgumentException("multiple methods annotated with @Paramixel.Id(\""
                        + id + "\") were found on " + type.getName()
                        + ": " + existing + ", " + method.toGenericString());
            }

            result.put(id, method);
        }

        return Map.copyOf(result);
    }

    private static Map<String, Method> discoverStatic(final Class<?> type) {
        var result = new HashMap<String, Method>();
        var duplicateTracker = new HashMap<String, String>();

        for (Method method : type.getMethods()) {
            Paramixel.Id idAnnotation = method.getAnnotation(Paramixel.Id.class);
            if (idAnnotation == null) {
                continue;
            }

            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            String id = idAnnotation.value();

            validateStaticMethodSignature(method, id);

            method.trySetAccessible();

            String existing = duplicateTracker.putIfAbsent(id, method.toGenericString());
            if (existing != null) {
                throw new IllegalArgumentException("multiple methods annotated with @Paramixel.Id(\""
                        + id + "\") were found on " + type.getName()
                        + ": " + existing + ", " + method.toGenericString());
            }

            result.put(id, method);
        }

        return Map.copyOf(result);
    }

    private static void validateMethodSignature(final Method method, final String id) {
        if (method.getParameterCount() > 0) {
            throw new IllegalArgumentException("method annotated with @Paramixel.Id(\""
                    + id + "\") has unsupported signature: " + method.toGenericString()
                    + "; expected an instance no-argument void method");
        }
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("method annotated with @Paramixel.Id(\""
                    + id + "\") has unsupported signature: " + method.toGenericString()
                    + "; expected an instance no-argument void method");
        }
    }

    private static void validateStaticMethodSignature(final Method method, final String id) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("method annotated with @Paramixel.Id(\""
                    + id + "\") has unsupported signature: " + method.toGenericString()
                    + "; expected a static no-argument void method");
        }
        if (method.getParameterCount() > 0) {
            throw new IllegalArgumentException("method annotated with @Paramixel.Id(\""
                    + id + "\") has unsupported signature: " + method.toGenericString()
                    + "; expected a static no-argument void method");
        }
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("method annotated with @Paramixel.Id(\""
                    + id + "\") has unsupported signature: " + method.toGenericString()
                    + "; expected a static no-argument void method");
        }
    }
}
