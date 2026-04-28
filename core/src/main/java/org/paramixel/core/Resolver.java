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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

/**
 * Discovers {@link Action} factories from the classpath.
 */
public class Resolver {

    private static final Predicate<String> ALL = packageName -> true;

    private Resolver() {
        // Intentionally empty
    }

    /**
     * Controls how multiple discovered actions are composed.
     */
    public enum Composition {
        /**
         * Composes actions to execute one at a time in discovery order.
         */
        SEQUENTIAL,
        /**
         * Composes actions to execute concurrently.
         */
        PARALLEL
    }

    /**
     * Resolves action factories from all packages using parallel composition.
     *
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions() {
        return resolveActions(ALL, Composition.PARALLEL);
    }

    /**
     * Resolves action factories from all packages using a composition strategy.
     *
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(Composition composition) {
        return resolveActions(ALL, composition);
    }

    /**
     * Resolves action factories whose package names match a regular expression.
     *
     * @param packageRegex The package-name regular expression; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(String packageRegex) {
        return resolveActions(packageName -> packageName.matches(packageRegex), Composition.PARALLEL);
    }

    /**
     * Resolves action factories matching a package pattern with a composition
     * strategy.
     *
     * @param packageRegex The package-name regular expression; must not be null.
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(String packageRegex, Composition composition) {
        return resolveActions(packageName -> packageName.matches(packageRegex), composition);
    }

    /**
     * Resolves action factories whose package names satisfy a predicate.
     *
     * @param packagePredicate The package-name predicate; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate) {
        return resolveActions(packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves action factories selected by predicate with a composition
     * strategy.
     *
     * @param packagePredicate The package-name predicate; must not be null.
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate, Composition composition) {
        return scan(new ClassGraph(), packagePredicate, composition);
    }

    private static Optional<Action> scan(
            ClassGraph classGraph, Predicate<String> packagePredicate, Composition composition) {
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
                                .computeIfAbsent(clazz.getPackageName(), k -> new ArrayList<>())
                                .add(action));
            }
        }

        List<Action> actions = actionsByPackage.values().stream()
                .peek(list -> list.sort(Comparator.comparing(Action::name)))
                .flatMap(List::stream)
                .toList();

        return collapse(actions, "plan", composition);
    }

    /**
     * Resolves action factory methods declared by one class.
     *
     * @param clazz The class to inspect; must not be null.
     * @return An {@link Optional} containing the resolved class action, or empty
     *     when the class declares no enabled factories.
     * @throws ResolverException If an action factory method has an invalid
     *     signature or cannot be invoked.
     */
    public static Optional<Action> resolveActionsFromClass(Class<?> clazz) {
        List<Action> actions = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Paramixel.ActionFactory.class)) {
                continue;
            }

            if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                continue;
            }

            actions.add(resolveActionFromMethod(clazz, method));
        }

        return collapse(actions, clazz.getSimpleName(), Composition.SEQUENTIAL);
    }

    private static Optional<Action> collapse(List<Action> actions, String branchName, Composition composition) {
        return switch (actions.size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(actions.get(0));
            default ->
                Optional.of(
                        switch (composition) {
                            case SEQUENTIAL -> Sequential.of(branchName, actions);
                            case PARALLEL ->
                                Parallel.of(branchName, Runtime.getRuntime().availableProcessors(), actions);
                        });
        };
    }

    private static Action resolveActionFromMethod(Class<?> clazz, Method method) {
        int modifiers = method.getModifiers();

        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new ResolverException("Invalid @Paramixel.ActionFactory method on "
                    + clazz.getName()
                    + "#"
                    + method.getName()
                    + ": method must be public static");
        }

        if (method.getParameterCount() != 0) {
            throw new ResolverException("Invalid @Paramixel.ActionFactory method on "
                    + clazz.getName()
                    + "#"
                    + method.getName()
                    + ": method must have no parameters");
        }

        if (!Action.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException("Invalid @Paramixel.ActionFactory method on "
                    + clazz.getName()
                    + "#"
                    + method.getName()
                    + ": return type must be Action");
        }

        try {
            Object result = method.invoke(null);

            if (result == null) {
                throw new ResolverException("Invalid @Paramixel.ActionFactory method on "
                        + clazz.getName()
                        + "#"
                        + method.getName()
                        + ": method returned null");
            }

            return (Action) result;
        } catch (ReflectiveOperationException e) {
            throw new ResolverException(
                    "Failed to invoke @Paramixel.ActionFactory method on " + clazz.getName() + "#" + method.getName(),
                    e);
        }
    }

    /**
     * Resolves action factories matching a selector using parallel composition.
     *
     * @param selector The selector to match; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(Selector selector) {
        return resolveActions(selector.regex(), Composition.PARALLEL);
    }

    /**
     * Resolves action factories matching a selector with a composition strategy.
     *
     * @param selector The selector to match; must not be null.
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(Selector selector, Composition composition) {
        return resolveActions(selector.regex(), composition);
    }

    /**
     * Resolves action factories using a class loader and parallel composition.
     *
     * @param classLoader The class loader to scan; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader) {
        return resolveActions(classLoader, ALL, Composition.PARALLEL);
    }

    /**
     * Resolves action factories using a class loader and composition strategy.
     *
     * @param classLoader The class loader to scan; must not be null.
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Composition composition) {
        return resolveActions(classLoader, ALL, composition);
    }

    /**
     * Resolves selected action factories using a class loader and parallel
     * composition.
     *
     * @param classLoader The class loader to scan; must not be null.
     * @param packagePredicate The package-name predicate; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate) {
        return resolveActions(classLoader, packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves selected action factories using a class loader and composition
     * strategy.
     *
     * @param classLoader The class loader to scan; must not be null.
     * @param packagePredicate The package-name predicate; must not be null.
     * @param composition The composition strategy; must not be null.
     * @return An {@link Optional} containing the resolved root action, or empty
     *     when no factories are found.
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader, Predicate<String> packagePredicate, Composition composition) {
        return scan(new ClassGraph().overrideClassLoaders(classLoader), packagePredicate, composition);
    }
}
