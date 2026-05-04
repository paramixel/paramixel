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

package org.paramixel.core;

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.ConfigurationException;
import org.paramixel.core.exception.ResolverException;
import org.paramixel.core.support.Arguments;

/**
 * Discovers {@link Action} instances from {@code @Paramixel.ActionFactory} methods.
 *
 * <p>The resolver scans the active classpath, filters candidate classes using optional {@link Selector} criteria and
 * configuration properties, invokes eligible action factory methods, and collapses multiple discovered actions into a
 * single root action using the framework's default parallel composition.
 */
public final class Resolver {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private Resolver() {}

    /**
     * Resolves actions from the full classpath using {@link Configuration#defaultProperties()}.
     *
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     */
    public static Optional<Action> resolveActions() {
        return resolveActionsInternal(Configuration.defaultProperties(), null);
    }

    /**
     * Resolves actions matching the supplied selector using {@link Configuration#defaultProperties()}.
     *
     * @param selector the selector describing which classes and tags should match
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code selector} is {@code null}
     */
    public static Optional<Action> resolveActions(Selector selector) {
        return resolveActionsInternal(
                Configuration.defaultProperties(), Objects.requireNonNull(selector, "selector must not be null"));
    }

    /**
     * Resolves actions from the full classpath using the supplied configuration.
     *
     * @param configuration the configuration properties to use during discovery
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public static Optional<Action> resolveActions(Map<String, String> configuration) {
        return resolveActionsInternal(Objects.requireNonNull(configuration, "configuration must not be null"), null);
    }

    /**
     * Resolves actions matching the supplied selector using the supplied configuration.
     *
     * @param configuration the configuration properties to use during discovery
     * @param selector the selector describing which classes and tags should match
     * @return the resolved root action, or an empty {@link Optional} when no actions are discovered
     * @throws NullPointerException if {@code configuration} or {@code selector} is {@code null}
     */
    public static Optional<Action> resolveActions(Map<String, String> configuration, Selector selector) {
        return resolveActionsInternal(
                Objects.requireNonNull(configuration, "configuration must not be null"),
                Objects.requireNonNull(selector, "selector must not be null"));
    }

    private static Optional<Action> resolveActionsInternal(Map<String, String> configuration, Selector selector) {
        Map<String, String> effectiveConfiguration = Map.copyOf(configuration);
        int parallelism = resolveParallelism(effectiveConfiguration);
        Pattern configuredPackagePattern =
                resolveConfiguredPattern(effectiveConfiguration, Configuration.PACKAGE_MATCH);
        Pattern configuredClassPattern = resolveConfiguredPattern(effectiveConfiguration, Configuration.CLASS_MATCH);
        Pattern configuredTagPattern = resolveConfiguredPattern(effectiveConfiguration, Configuration.TAG_MATCH);
        return scan(
                new ClassGraph(),
                selector,
                configuredPackagePattern,
                configuredClassPattern,
                configuredTagPattern,
                parallelism);
    }

    static Optional<Action> resolveActionFromClass(Class<?> clazz) {
        return resolveActionFromClass(clazz, null, null);
    }

    static Optional<Action> resolveActionFromClass(
            Class<?> clazz, Pattern selectorTagPattern, Pattern configurationTagPattern) {
        Set<MethodKey> seenSignatures = new HashSet<>();
        List<Method> candidates = new ArrayList<>();
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

    static int resolveParallelism(Map<String, String> configuration) {
        Map<String, String> effectiveConfiguration =
                configuration != null ? configuration : Configuration.defaultProperties();
        String configuredParallelism = effectiveConfiguration.get(Configuration.RUNNER_PARALLELISM);
        if (configuredParallelism == null) {
            Map<String, String> defaults = Configuration.defaultProperties();
            configuredParallelism = defaults.getOrDefault(
                    Configuration.RUNNER_PARALLELISM,
                    String.valueOf(Runtime.getRuntime().availableProcessors()));
        }
        final int parallelism;
        try {
            parallelism = Integer.parseInt(configuredParallelism);
        } catch (NumberFormatException e) {
            throw ConfigurationException.of(
                    "Invalid configuration for '" + Configuration.RUNNER_PARALLELISM + "': expected integer but was '"
                            + configuredParallelism + "'",
                    e);
        }
        if (parallelism <= 0) {
            throw ConfigurationException.of("Invalid configuration for '" + Configuration.RUNNER_PARALLELISM
                    + "': expected positive integer but was '" + configuredParallelism + "'");
        }
        return parallelism;
    }

    private static Pattern resolveConfiguredPattern(Map<String, String> configuration, String key) {
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

    private static Optional<Action> scan(
            ClassGraph classGraph,
            Selector selector,
            Pattern configuredPackagePattern,
            Pattern configuredClassPattern,
            Pattern configuredTagPattern,
            int parallelism) {
        TreeMap<String, List<Action>> actionsByPackage = new TreeMap<>();
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

    private static boolean matchesLocationFilters(
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

    private static boolean matchesTagFilters(
            List<String> tags, Pattern selectorTagPattern, Pattern configurationTagPattern) {
        if (selectorTagPattern != null && !matchesAnyTag(tags, selectorTagPattern)) {
            return false;
        }
        return configurationTagPattern == null || matchesAnyTag(tags, configurationTagPattern);
    }

    private static boolean matchesAnyTag(List<String> tags, Pattern pattern) {
        if (pattern == null) {
            return true;
        }
        return tags.stream().anyMatch(tag -> pattern.matcher(tag).find());
    }

    private static List<String> extractTags(Method method) {
        List<String> tags = new ArrayList<>();
        for (Paramixel.Tag tag : method.getAnnotationsByType(Paramixel.Tag.class)) {
            String value = tag.value();
            Arguments.requireNonBlank(
                    value,
                    "Invalid @Paramixel.Tag on " + method.getDeclaringClass().getName() + "#" + method.getName()
                            + ": tag value must not be blank");
            tags.add(value);
        }
        return tags;
    }

    private static Optional<Action> collapse(List<Action> actions, int parallelism) {
        return switch (actions.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(actions.get(0));
            default -> Optional.of(Parallel.of(HIDDEN_ROOT, parallelism, actions));
        };
    }

    private static Action resolveActionFromMethod(Method method) {
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
            Object result = method.invoke(null);
            if (result == null) {
                throw ResolverException.of(
                        "Invalid @Paramixel.ActionFactory method on " + location + ": method returned null");
            }
            return (Action) result;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
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
