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

package org.paramixel.core.discovery;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.ConfigurationException;
import org.paramixel.core.Paramixel;
import org.paramixel.core.ResolverException;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

/**
 * Discovers {@link Action} factories from the classpath.
 *
 * <p>When discovered actions are combined with {@link Composition#PARALLEL}, the
 * resulting {@link Parallel} root uses the parallelism value from configuration.
 * The configuration key {@link Configuration#RUNNER_PARALLELISM}
 * ({@code paramixel.parallelism}) is resolved in this order:</p>
 *
 * <ol>
 *   <li>Value in the configuration map provided to the {@code resolveActions} overload</li>
 *   <li>{@link Configuration#defaultProperties()} (classpath file, then JVM system properties)</li>
 *   <li>Built-in default: {@code Runtime.getRuntime().availableProcessors()}</li>
 * </ol>
 *
 * <p>Overloads that do not accept a configuration map use
 * {@link Configuration#defaultProperties()} as the configuration source.</p>
 *
 * <h3>Factory Method Execution</h3>
 * <p>Discovery is not purely a scanning operation. When an {@link Paramixel.ActionFactory}
 * method is found, it is <strong>invoked via reflection</strong> to produce the action
 * instance. This means:</p>
 * <ul>
 *   <li>Factory method code executes during discovery, not at a later stage</li>
 *   <li>Side effects in factory methods (e.g., resource allocation, logging) will
 *       occur when {@code resolveActions} is called</li>
 *   <li>Exceptions thrown by factory methods are wrapped in {@link ResolverException}</li>
 * </ul>
 *
 * @see Configuration#RUNNER_PARALLELISM
 * @see Configuration#defaultProperties()
 */
public final class Resolver {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private static final Predicate<String> ALL = packageName -> true;

    private Resolver() {}

    /**
     * Determines how discovered actions are combined.
     */
    public enum Composition {
        /** Combine discovered actions in declaration order. */
        SEQUENTIAL,
        /** Combine discovered actions so they may execute concurrently. */
        PARALLEL
    }

    /**
     * Resolves all discovered action factories on the classpath.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @return the resolved root action, if any action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions() {
        return resolveActions(ALL, Composition.PARALLEL);
    }

    /**
     * Resolves all discovered action factories on the classpath using the supplied composition mode.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Composition composition) {
        return resolveActions(ALL, composition);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(String packageRegex) {
        return resolveActions(packageName -> packageName.matches(packageRegex), Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(String packageRegex, Composition composition) {
        return resolveActions(packageName -> packageName.matches(packageRegex), composition);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate) {
        return resolveActions(packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate, Composition composition) {
        int parallelism = resolveParallelism(null);
        return scan(new ClassGraph(), packagePredicate, composition, parallelism);
    }

    /**
     * Resolves discovered action factories using the supplied class loader.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @param classLoader the class loader used for discovery
     * @return the resolved root action, if any action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader) {
        return resolveActions(classLoader, ALL, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories using the supplied class loader and composition mode.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Composition composition) {
        return resolveActions(classLoader, ALL, composition);
    }

    /**
     * Resolves discovered action factories using the supplied class loader and package predicate.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate) {
        return resolveActions(classLoader, packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories using the supplied class loader, package predicate, and composition mode.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader, Predicate<String> packagePredicate, Composition composition) {
        int parallelism = resolveParallelism(null);
        return scan(new ClassGraph().overrideClassLoaders(classLoader), packagePredicate, composition, parallelism);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector.
     *
     * <p>Uses {@link Composition#PARALLEL} and configuration from
     * {@link Configuration#defaultProperties()}.</p>
     *
     * @param selector the classpath selector used to filter discovered classes
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Selector selector) {
        return resolveActions(selector, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector and composition mode.
     *
     * <p>Uses configuration from {@link Configuration#defaultProperties()}.</p>
     *
     * @param selector the classpath selector used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Selector selector, Composition composition) {
        return resolveActions(selector.getRegex(), composition);
    }

    /**
     * Resolves all discovered action factories on the classpath with the supplied configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Map<String, String> configuration) {
        return resolveActions(ALL, Composition.PARALLEL, configuration);
    }

    /**
     * Resolves all discovered action factories on the classpath with the supplied composition mode and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Composition composition, Map<String, String> configuration) {
        return resolveActions(ALL, composition, configuration);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression,
     * with the supplied configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(String packageRegex, Map<String, String> configuration) {
        return resolveActions(packageName -> packageName.matches(packageRegex), Composition.PARALLEL, configuration);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression,
     * with the supplied composition mode and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            String packageRegex, Composition composition, Map<String, String> configuration) {
        return resolveActions(packageName -> packageName.matches(packageRegex), composition, configuration);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate,
     * with the supplied configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            Predicate<String> packagePredicate, Map<String, String> configuration) {
        return resolveActions(packagePredicate, Composition.PARALLEL, configuration);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate,
     * with the supplied composition mode and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            Predicate<String> packagePredicate, Composition composition, Map<String, String> configuration) {
        int parallelism = resolveParallelism(configuration);
        return scan(new ClassGraph(), packagePredicate, composition, parallelism);
    }

    /**
     * Resolves discovered action factories using the supplied class loader and configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Map<String, String> configuration) {
        return resolveActions(classLoader, ALL, Composition.PARALLEL, configuration);
    }

    /**
     * Resolves discovered action factories using the supplied class loader, composition mode, and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader, Composition composition, Map<String, String> configuration) {
        return resolveActions(classLoader, ALL, composition, configuration);
    }

    /**
     * Resolves discovered action factories using the supplied class loader, package predicate, and configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader, Predicate<String> packagePredicate, Map<String, String> configuration) {
        return resolveActions(classLoader, packagePredicate, Composition.PARALLEL, configuration);
    }

    /**
     * Resolves discovered action factories using the supplied class loader, package predicate,
     * composition mode, and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader,
            Predicate<String> packagePredicate,
            Composition composition,
            Map<String, String> configuration) {
        int parallelism = resolveParallelism(configuration);
        return scan(new ClassGraph().overrideClassLoaders(classLoader), packagePredicate, composition, parallelism);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector, with the supplied configuration.
     *
     * <p>Uses {@link Composition#PARALLEL}. When {@code paramixel.parallelism} is present in
     * the configuration map, that value is used; otherwise
     * {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param selector the classpath selector used to filter discovered classes
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(Selector selector, Map<String, String> configuration) {
        return resolveActions(selector, Composition.PARALLEL, configuration);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector, composition mode,
     * and configuration.
     *
     * <p>When {@code paramixel.parallelism} is present in the configuration map, that value is used;
     * otherwise {@link Configuration#defaultProperties()} supplies the default.</p>
     *
     * @param selector the classpath selector used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @param configuration the configuration controlling parallelism; not mutated by this method
     * @return the resolved root action, if any matching action factories are found
     * @throws ConfigurationException if {@code paramixel.parallelism} is present but invalid
     * @throws ResolverException if a discovered factory method throws an exception during invocation
     */
    public static Optional<Action> resolveActions(
            Selector selector, Composition composition, Map<String, String> configuration) {
        return resolveActions(selector.getRegex(), composition, configuration);
    }

    /**
     * Resolves the outermost action factory in the class hierarchy of the supplied class.
     *
     * <p>Walks the superclass chain from the supplied class up to (but not including)
     * {@code java.lang.Object}, identifying methods annotated with {@link Paramixel.ActionFactory}.
     * Only the outermost (most-derived) method for any given signature is considered;
     * when a child class declares a method with the same name and parameter types as a
     * parent method, the child's version shadows the parent's, even if the child's version
     * is not annotated with {@code @ActionFactory}.</p>
     *
     * <p>Methods annotated with {@link Paramixel.Disabled} are excluded from consideration.</p>
     *
     * <p>If more than one {@code @ActionFactory} method is found across the hierarchy,
     * this method throws {@link ResolverException}. Each class hierarchy must have at most
     * one eligible factory method.</p>
     *
     * <p><strong>Side Effects:</strong> This method invokes the discovered factory method
     * via reflection. Any side effects in the factory method body will occur during this call.
     * Exceptions thrown by the factory method are wrapped in {@link ResolverException}.</p>
     *
     * @param clazz the class whose hierarchy should be searched for a factory method
     * @return the resolved action, if exactly one eligible factory method is found
     * @throws ResolverException if more than one {@code @ActionFactory} method is found
     *     in the class hierarchy, if the factory method is invalid, or if the factory
     *     method throws an exception during invocation
     */
    public static Optional<Action> resolveActionsFromClass(Class<?> clazz) {
        Set<MethodKey> seenSignatures = new HashSet<>();
        List<Method> candidates = new ArrayList<>();
        Class<?> current = clazz;
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

    /**
     * Resolves the parallelism value from configuration.
     *
     * <p>Looks up {@link Configuration#RUNNER_PARALLELISM} in the supplied configuration map.
     * If the key is absent, falls back to {@link Configuration#defaultProperties()}.
     * If still absent, uses {@code Runtime.getRuntime().availableProcessors()}.</p>
     *
     * @param configuration the configuration map, or {@code null} to use
     *     {@link Configuration#defaultProperties()} directly
     * @return the resolved parallelism value
     * @throws ConfigurationException if the parallelism value is not a valid positive integer
     */
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

    private static Optional<Action> scan(
            ClassGraph classGraph, Predicate<String> packagePredicate, Composition composition, int parallelism) {
        TreeMap<String, List<Action>> actionsByPackage = new TreeMap<>();
        try (ScanResult scanResult = classGraph
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {
            for (Class<?> clazz : scanResult
                    .getClassesWithMethodAnnotation(Paramixel.ActionFactory.class.getName())
                    .loadClasses()) {
                if (!packagePredicate.test(clazz.getPackageName())) {
                    continue;
                }
                resolveActionsFromClass(clazz)
                        .ifPresent(action -> actionsByPackage
                                .computeIfAbsent(clazz.getPackageName(), ignored -> new ArrayList<>())
                                .add(action));
            }
        }

        List<Action> actions = actionsByPackage.values().stream()
                .peek(list -> list.sort(Comparator.comparing(Action::getName)))
                .flatMap(List::stream)
                .toList();
        return collapse(actions, HIDDEN_ROOT, composition, parallelism);
    }

    private static Optional<Action> collapse(
            List<Action> actions, String branchName, Composition composition, int parallelism) {
        return switch (actions.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(actions.get(0));
            default ->
                Optional.of(
                        switch (composition) {
                            case SEQUENTIAL -> Sequential.of(branchName, actions);
                            case PARALLEL -> Parallel.of(branchName, parallelism, actions);
                        });
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
            Throwable cause =
                    e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
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
