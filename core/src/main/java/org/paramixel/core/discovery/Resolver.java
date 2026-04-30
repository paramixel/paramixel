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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.ResolverException;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

/**
 * Discovers {@link Action} factories from the classpath.
 */
public final class Resolver {

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
     * @return the resolved root action, if any action factories are found
     */
    public static Optional<Action> resolveActions() {
        return resolveActions(ALL, Composition.PARALLEL);
    }

    /**
     * Resolves all discovered action factories on the classpath using the supplied composition mode.
     *
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any action factories are found
     */
    public static Optional<Action> resolveActions(Composition composition) {
        return resolveActions(ALL, composition);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression.
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(String packageRegex) {
        return resolveActions(packageName -> packageName.matches(packageRegex), Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories whose package names match the supplied regular expression.
     *
     * @param packageRegex the package-name regular expression used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(String packageRegex, Composition composition) {
        return resolveActions(packageName -> packageName.matches(packageRegex), composition);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate.
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate) {
        return resolveActions(packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories whose package names satisfy the supplied predicate.
     *
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(Predicate<String> packagePredicate, Composition composition) {
        return scan(new ClassGraph(), packagePredicate, composition);
    }

    /**
     * Resolves discovered action factories using the supplied class loader.
     *
     * @param classLoader the class loader used for discovery
     * @return the resolved root action, if any action factories are found
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader) {
        return resolveActions(classLoader, ALL, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories using the supplied class loader and composition mode.
     *
     * @param classLoader the class loader used for discovery
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any action factories are found
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Composition composition) {
        return resolveActions(classLoader, ALL, composition);
    }

    /**
     * Resolves discovered action factories using the supplied class loader and package predicate.
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(ClassLoader classLoader, Predicate<String> packagePredicate) {
        return resolveActions(classLoader, packagePredicate, Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories using the supplied class loader, package predicate, and composition mode.
     *
     * @param classLoader the class loader used for discovery
     * @param packagePredicate the package filter applied to discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(
            ClassLoader classLoader, Predicate<String> packagePredicate, Composition composition) {
        return scan(new ClassGraph().overrideClassLoaders(classLoader), packagePredicate, composition);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector.
     *
     * @param selector the classpath selector used to filter discovered classes
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(Selector selector) {
        return resolveActions(selector.getRegex(), Composition.PARALLEL);
    }

    /**
     * Resolves discovered action factories selected by the supplied selector and composition mode.
     *
     * @param selector the classpath selector used to filter discovered classes
     * @param composition how discovered actions should be combined
     * @return the resolved root action, if any matching action factories are found
     */
    public static Optional<Action> resolveActions(Selector selector, Composition composition) {
        return resolveActions(selector.getRegex(), composition);
    }

    /**
     * Resolves action factories declared directly on the supplied class.
     *
     * @param clazz the class whose declared factory methods should be resolved
     * @return the resolved action, if any eligible factory methods are found
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
                                .computeIfAbsent(clazz.getPackageName(), ignored -> new ArrayList<>())
                                .add(action));
            }
        }

        List<Action> actions = actionsByPackage.values().stream()
                .peek(list -> list.sort(Comparator.comparing(Action::getName)))
                .flatMap(List::stream)
                .toList();
        return collapse(actions, "<run>", composition);
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
            throw ResolverException.of("Invalid @Paramixel.ActionFactory method on " + clazz.getName() + "#"
                    + method.getName() + ": method must be public static");
        }
        if (method.getParameterCount() != 0) {
            throw ResolverException.of("Invalid @Paramixel.ActionFactory method on " + clazz.getName() + "#"
                    + method.getName() + ": method must have no parameters");
        }
        if (!Action.class.isAssignableFrom(method.getReturnType())) {
            throw ResolverException.of("Invalid @Paramixel.ActionFactory method on " + clazz.getName() + "#"
                    + method.getName() + ": return type must be Action");
        }
        try {
            Object result = method.invoke(null);
            if (result == null) {
                throw ResolverException.of("Invalid @Paramixel.ActionFactory method on " + clazz.getName() + "#"
                        + method.getName() + ": method returned null");
            }
            return (Action) result;
        } catch (ReflectiveOperationException e) {
            Throwable cause =
                    e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            throw ResolverException.of(
                    "Failed to invoke @Paramixel.ActionFactory method on " + clazz.getName() + "#" + method.getName(),
                    cause);
        }
    }
}
