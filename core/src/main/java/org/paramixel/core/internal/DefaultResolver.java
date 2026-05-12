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

package org.paramixel.core.internal;

import io.github.classgraph.ClassGraph;
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
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Selector;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.ResolverException;
import org.paramixel.core.internal.listener.Constants;
import org.paramixel.core.support.Arguments;

/**
 * Discovers Paramixel action factories and resolves them into executable action trees.
 */
public final class DefaultResolver {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    private final DefaultConfiguration configuration;

    /**
     * Creates a resolver with the supplied configuration.
     *
     * @param configuration the configuration used for discovery filters and discovered root parallelism
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public DefaultResolver(DefaultConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Resolves discovered actions matching the supplied selector.
     *
     * @param selector the selector used to filter discovered action factories
     * @return the resolved root action, or an empty {@link Optional} when no factories match
     */
    public Optional<Action> resolveActions(Selector selector) {
        int parallelism = configuration.resolveParallelism();
        Pattern configuredPackagePattern = resolveConfiguredPattern(Configuration.PACKAGE_MATCH);
        Pattern configuredClassPattern = resolveConfiguredPattern(Configuration.CLASS_MATCH);
        Pattern configuredTagPattern = resolveConfiguredPattern(Configuration.TAG_MATCH);
        return scan(
                new ClassGraph(),
                selector,
                configuredPackagePattern,
                configuredClassPattern,
                configuredTagPattern,
                parallelism);
    }

    /**
     * Resolves an action factory declared by a class or its ancestors.
     *
     * @param clazz the class to inspect for an action factory
     * @return the resolved action, or an empty {@link Optional} when no factory is present
     */
    public Optional<Action> resolveActionFromClass(Class<?> clazz) {
        return resolveActionFromClass(clazz, null, null);
    }

    /**
     * Resolves an action factory declared by a class or its ancestors with tag filters.
     *
     * @param clazz the class to inspect for an action factory
     * @param selectorTagPattern the selector tag filter, or {@code null} when absent
     * @param configurationTagPattern the configuration tag filter, or {@code null} when absent
     * @return the resolved action, or an empty {@link Optional} when no factory matches
     */
    public Optional<Action> resolveActionFromClass(
            Class<?> clazz, Pattern selectorTagPattern, Pattern configurationTagPattern) {
        var seenSignatures = new HashSet<MethodKey>();
        var candidates = new ArrayList<Method>();
        Class<?> current = Objects.requireNonNull(clazz, "clazz must not be null");
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                MethodKey key = new MethodKey(method.getName(), method.getParameterTypes());
                if (!seenSignatures.add(key)) {
                    continue;
                }
                if (!method.isAnnotationPresent(Paramixel.ActionFactory.class)) {
                    continue;
                }
                if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                    continue;
                }
                List<String> tags = extractTags(method);
                if (!matchesTagFilters(tags, selectorTagPattern, configurationTagPattern)) {
                    continue;
                }
                candidates.add(method);
            }
            current = current.getSuperclass();
        }
        if (candidates.size() > 1) {
            String methodList = candidates.stream()
                    .map(m -> m.getDeclaringClass().getName() + "#" + m.getName())
                    .collect(Collectors.joining(", "));
            throw ResolverException.of("Class " + clazz.getName()
                    + " has more than one @Paramixel.ActionFactory method in its hierarchy: " + methodList);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resolveActionFromMethod(candidates.get(0)));
    }

    private Pattern resolveConfiguredPattern(String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            throw ConfigurationException.of(
                    "Invalid configuration for '" + key + "': invalid regular expression '" + value + "'", e);
        }
    }

    private Optional<Action> scan(
            ClassGraph classGraph,
            Selector selector,
            Pattern configuredPackagePattern,
            Pattern configuredClassPattern,
            Pattern configuredTagPattern,
            int parallelism) {
        var actionsByPackage = new TreeMap<String, List<Action>>();
        try (ScanResult scanResult = classGraph
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {
            for (Class<?> clazz : scanResult
                    .getClassesWithMethodAnnotation(Paramixel.ActionFactory.class.getName())
                    .loadClasses()) {
                if (!matchesLocationFilters(clazz, selector, configuredPackagePattern, configuredClassPattern)) {
                    continue;
                }
                Pattern selectorTagPattern = selector != null ? selector.getTagPattern() : null;
                resolveActionFromClass(clazz, selectorTagPattern, configuredTagPattern)
                        .ifPresent(action -> actionsByPackage
                                .computeIfAbsent(clazz.getPackageName(), ignored -> new ArrayList<>())
                                .add(action));
            }
        }

        List<Action> actions = actionsByPackage.values().stream()
                .peek(list -> list.sort(Comparator.comparing(Action::getName)))
                .flatMap(List::stream)
                .toList();
        return collapse(actions, parallelism);
    }

    private boolean matchesLocationFilters(
            Class<?> clazz, Selector selector, Pattern configuredPackagePattern, Pattern configuredClassPattern) {
        if (selector != null && !selector.matchesLocation(clazz)) {
            return false;
        }
        if (configuredPackagePattern != null
                && !configuredPackagePattern.matcher(clazz.getPackageName()).find()) {
            return false;
        }
        return configuredClassPattern == null
                || configuredClassPattern.matcher(clazz.getName()).find();
    }

    private boolean matchesTagFilters(List<String> tags, Pattern selectorTagPattern, Pattern configurationTagPattern) {
        if (selectorTagPattern != null && !matchesAnyTag(tags, selectorTagPattern)) {
            return false;
        }
        return configurationTagPattern == null || matchesAnyTag(tags, configurationTagPattern);
    }

    private boolean matchesAnyTag(List<String> tags, Pattern pattern) {
        if (pattern == null) {
            return true;
        }
        return tags.stream().anyMatch(tag -> pattern.matcher(tag).find());
    }

    private List<String> extractTags(Method method) {
        var tags = new ArrayList<String>();
        for (var tag : method.getAnnotationsByType(Paramixel.Tag.class)) {
            String value = tag.value();
            Arguments.requireNonBlank(
                    value,
                    "Invalid @Paramixel.Tag on " + method.getDeclaringClass().getName() + "#" + method.getName()
                            + ": tag value must not be blank");
            tags.add(value);
        }
        return tags;
    }

    private Optional<Action> collapse(List<Action> actions, int parallelism) {
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        var builder = Parallel.builder(ROOT_NAME).parallelism(parallelism);
        for (Action action : actions) {
            builder.child(action);
        }
        return Optional.of(builder.build());
    }

    private Action resolveActionFromMethod(Method method) {
        String location = method.getDeclaringClass().getName() + "#" + method.getName();
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw ResolverException.of(
                    "Invalid @Paramixel.ActionFactory method on " + location + ": method must be public static");
        }
        if (method.getParameterCount() != 0) {
            throw ResolverException.of(
                    "Invalid @Paramixel.ActionFactory method on " + location + ": method must have no parameters");
        }
        if (!Action.class.isAssignableFrom(method.getReturnType())) {
            throw ResolverException.of(
                    "Invalid @Paramixel.ActionFactory method on " + location + ": return type must be Action");
        }
        try {
            method.setAccessible(true);
            Object result = method.invoke(null);
            if (result == null) {
                throw ResolverException.of(
                        "Invalid @Paramixel.ActionFactory method on " + location + ": method returned null");
            }
            return (Action) result;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Error error) {
                throw error;
            }
            throw ResolverException.of("Failed to invoke @Paramixel.ActionFactory method on " + location, cause);
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
}
