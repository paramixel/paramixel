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

package org.paramixel.api.internal;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.exception.SkipException;
import org.paramixel.api.internal.listener.support.Constants;
import org.paramixel.api.selector.Selector;

/**
 * Scans the classpath for {@link org.paramixel.api.Paramixel.Factory} methods, validates them,
 * invokes them to produce {@link Spec} or {@link Action} instances, resolves specs to actions,
 * and collapses the results into a single root {@link org.paramixel.api.action.Parallel} action.
 *
 * <p>Discovery applies package, class, and tag filters from the {@link Selector}. Invalid factory methods
 * throw {@link org.paramixel.api.exception.ResolverException} at resolution time; blank tag values are
 * collected as validation-failure actions rather than failing the entire scan. Factory methods that return
 * {@code null} produce skipped actions rather than failing discovery.
 */
public final class ClasspathResolver {

    private static final String ROOT_NAME = Constants.ROOT_NAME;
    private static final String FACTORY_ANNOTATION = Paramixel.Factory.class.getName();

    private final Configuration configuration;
    private final Selector selector;

    /**
     * Creates a resolver with the supplied configuration and selector.
     *
     * @param configuration the configuration used for discovered root parallelism
     * @param selector the selector used for discovery filtering; must not be {@code null}
     * @throws NullPointerException if {@code configuration} or {@code selector} is {@code null}
     */
    public ClasspathResolver(final Configuration configuration, final Selector selector) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.selector = Objects.requireNonNull(selector, "selector must not be null");
    }

    /**
     * Resolves discovered actions matching this resolver's selector.
     *
     * @return the resolved root action, or an empty {@link Optional} when no factories match
     */
    public Optional<Action<?>> resolveActions() {
        final var parallelism = resolveParallelism();
        return scan(new ClassGraph(), parallelism);
    }

    private int resolveParallelism() {
        return configuration
                .getInteger(Configuration.RUNNER_PARALLELISM)
                .orElse(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Performs a classpath scan and returns the root action for all discovered and validated factories.
     *
     * @param classGraph the ClassGraph instance to configure and scan
     * @param parallelism the parallelism of the root parallel action
     * @return the root action, or an empty {@link Optional} when no factories match
     */
    Optional<Action<?>> scan(final ClassGraph classGraph, final int parallelism) {
        var candidates = new ArrayList<ActionCandidate>();
        var failures = new ArrayList<DiscoveryValidationFailure>();
        try (ScanResult scanResult = classGraph
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .scan()) {
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(FACTORY_ANNOTATION)) {
                if (!acceptsClass(classInfo)) {
                    continue;
                }
                var clazz = classInfo.loadClass();
                resolveFactoryMethod(clazz, failures).ifPresent(candidates::add);
            }
        }

        final var actions = candidates.stream()
                .sorted(Comparator.comparingInt(ActionCandidate::priority)
                        .reversed()
                        .thenComparing(ActionCandidate::packageName)
                        .thenComparing(ActionCandidate::className)
                        .thenComparing(ActionCandidate::methodName))
                .map(this::resolveActionFromMethod)
                .toList();

        var allActions = Stream.concat(actions.stream(), discoveryFailureActions(failures).stream())
                .toList();
        return collapse(allActions, parallelism);
    }

    private boolean acceptsClass(final ClassInfo classInfo) {
        if (!selector.matchesPackage(classInfo.getPackageName())) {
            return false;
        }
        return selector.matchesClass(classInfo.getName());
    }

    private boolean acceptsAnyTag(final List<String> tags) {
        if (tags.isEmpty()) {
            return selector.matchesTag("");
        }
        return tags.stream().anyMatch(selector::matchesTag);
    }

    private ValidatedTags validateAndExtractTags(
            final Method method, final String location, final List<DiscoveryValidationFailure> failures) {
        var tags = new ArrayList<String>();
        boolean hasInvalidTag = false;
        for (var tag : method.getAnnotationsByType(Paramixel.Tag.class)) {
            final var value = tag.value();
            if (value.isBlank()) {
                failures.add(new DiscoveryValidationFailure(location, "tag value must not be blank"));
                hasInvalidTag = true;
            } else {
                tags.add(value);
            }
        }
        return new ValidatedTags(tags, hasInvalidTag);
    }

    private Optional<ActionCandidate> resolveFactoryMethod(
            final Class<?> clazz, final List<DiscoveryValidationFailure> failures) {
        var seenSignatures = new HashSet<MethodKey>();
        var candidates = new ArrayList<ValidatedFactoryMethod>();
        var current = Objects.requireNonNull(clazz, "clazz must not be null");
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                MethodKey key = new MethodKey(method.getName(), method.getParameterTypes());
                if (!seenSignatures.add(key)) {
                    continue;
                }
                if (!method.isAnnotationPresent(Paramixel.Factory.class)) {
                    continue;
                }
                if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                    continue;
                }
                final var location = locationOf(method);
                final var tags = validateAndExtractTags(method, location, failures);
                if (tags.hasInvalidTag()) {
                    continue;
                }
                if (!acceptsAnyTag(tags.tags())) {
                    continue;
                }
                validateFactoryMethod(method, location);
                candidates.add(new ValidatedFactoryMethod(method, location));
            }
            current = current.getSuperclass();
        }
        if (candidates.size() > 1) {
            String methodList =
                    candidates.stream().map(ValidatedFactoryMethod::location).collect(Collectors.joining(", "));
            throw new ResolverException("Class " + clazz.getName()
                    + " has more than one @Paramixel.Factory method in its hierarchy: " + methodList);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final var candidate = candidates.get(0);
        final var method = candidate.method();
        return Optional.of(new ActionCandidate(
                priorityOf(method),
                method.getDeclaringClass().getPackageName(),
                method.getDeclaringClass().getName(),
                method.getName(),
                method,
                candidate.location()));
    }

    private int priorityOf(final Method method) {
        Paramixel.Priority priority = method.getDeclaringClass().getAnnotation(Paramixel.Priority.class);
        return priority == null ? 0 : priority.value();
    }

    private List<? extends Action<?>> discoveryFailureActions(final List<DiscoveryValidationFailure> failures) {
        if (failures.isEmpty()) {
            return List.of();
        }
        return failures.stream()
                .sorted(Comparator.comparing(DiscoveryValidationFailure::location)
                        .thenComparing(DiscoveryValidationFailure::message))
                .collect(Collectors.groupingBy(DiscoveryValidationFailure::location))
                .entrySet()
                .stream()
                .map(entry -> {
                    final var location = entry.getKey();
                    final var messages = entry.getValue().stream()
                            .map(DiscoveryValidationFailure::message)
                            .toList();
                    return Step.of("Discovery validation failure: " + location, context -> {
                        throw new ResolverException(
                                "Invalid @Paramixel.Tag on " + location + ": " + String.join(", ", messages));
                    });
                })
                .toList();
    }

    private Optional<Action<?>> collapse(final List<? extends Action<?>> actions, final int parallelism) {
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        var spec = Parallel.of(ROOT_NAME).parallelism(parallelism);
        for (Action<?> action : actions) {
            spec.child(action);
        }
        return Optional.of(spec.resolve());
    }

    private String locationOf(final Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private void validateFactoryMethod(final Method method, final String location) {
        final var modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new ResolverException(
                    "Invalid @Paramixel.Factory method on " + location + ": method must be public static");
        }
        if (method.getParameterCount() != 0) {
            throw new ResolverException(
                    "Invalid @Paramixel.Factory method on " + location + ": method must have no parameters");
        }
        if (!Spec.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException(
                    "Invalid @Paramixel.Factory method on " + location + ": return type must be Spec or Action");
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private Action<?> resolveActionFromMethod(final ActionCandidate candidate) {
        final var method = candidate.method();
        final var location = candidate.location();
        try {
            Object result;
            try {
                result = method.invoke(null);
            } catch (IllegalAccessException e) {
                method.setAccessible(true);
                try {
                    result = method.invoke(null);
                } catch (IllegalAccessException ex) {
                    throw new LinkageError("Cannot access @Paramixel.Factory method " + method, ex);
                }
            }
            if (result == null) {
                return Step.of("Skipped factory: " + location, context -> {
                    throw new SkipException("factory returned null: " + location);
                });
            }
            return ((Spec<?>) result).resolve();
        } catch (InvocationTargetException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ResolverException("Failed to invoke @Paramixel.Factory method on " + location, cause);
        }
    }

    private record MethodKey(String name, Class<?>[] parameterTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey other)) return false;
            return name.equals(other.name) && Arrays.equals(parameterTypes, other.parameterTypes);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + Arrays.hashCode(parameterTypes);
        }
    }

    private record ActionCandidate(
            int priority, String packageName, String className, String methodName, Method method, String location) {}

    private record ValidatedTags(List<String> tags, boolean hasInvalidTag) {}

    private record ValidatedFactoryMethod(Method method, String location) {}

    private record DiscoveryValidationFailure(String location, String message) {}
}
