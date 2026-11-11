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

package nonapi.org.paramixel.discovery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResult;
import nonapi.org.paramixel.compiler.PlanCompiler;
import nonapi.org.paramixel.reporting.Constants;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.SequentialBuilder;
import org.paramixel.api.Task;
import org.paramixel.api.TaskBuilder;
import org.paramixel.api.Tasks;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Discovers {@link Paramixel.Factory}, {@link Paramixel.BeforeAll}, and {@link Paramixel.AfterAll} methods on the
 * classpath and composes them into one definition.
 *
 * <p>Discovery validates every matched method — {@code public static}, no parameters, returning {@link Task} or a
 * {@link TaskBuilder} — before any discovered test work runs, so an invalid factory fails the run with
 * {@link ResolverException} instead of executing a partial suite.
 *
 * <p>A factory that returns {@code null} is an explicit skip: the factory contributes no definition and discovery
 * continues. That is the one defined meaning of {@code null} from a discovered method; a {@code null} element produced
 * by {@code each} mapping remains a definition error.
 *
 * <p>The composed definition is:
 *
 * <pre>
 * Paramixel (independent sequential root)
 *   run (dependent sequential)
 *     BeforeAll hooks (when any)
 *     actions (parallel, pool "discovery")   &lt;- discovered factories
 *   AfterAll hooks (when any)
 * </pre>
 *
 * <p>The independent root guarantees that {@code AfterAll} hooks run after a started run even when a factory or a
 * {@code BeforeAll} hook failed; the dependent {@code run} group guarantees that a failing {@code BeforeAll} hook skips
 * the factories. The {@code actions} group carries the run-level fail-fast scope and uses the {@code discovery} pool,
 * whose capacity defaults to {@code paramixel.parallelism}.
 */
public final class FactoryScanner {

    /** Name of the pool that bounds concurrent discovered factories. */
    public static final String DISCOVERY_POOL = "discovery";

    private static final String FACTORY_ANNOTATION = Paramixel.Factory.class.getName();
    private static final String BEFORE_ALL_ANNOTATION = Paramixel.BeforeAll.class.getName();
    private static final String AFTER_ALL_ANNOTATION = Paramixel.AfterAll.class.getName();

    private final Configuration configuration;
    private final ConcreteSelector selector;
    private final boolean shuffled;
    private final long shuffleSeed;

    /**
     * Creates a scanner.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param selector the selection filter; must not be {@code null}
     * @param shuffled whether to shuffle discovered factories
     * @param shuffleSeed the shuffle seed when {@code shuffled} is {@code true}
     * @throws NullPointerException if an argument is {@code null}
     */
    public FactoryScanner(
            final Configuration configuration,
            final Selector selector,
            final boolean shuffled,
            final long shuffleSeed) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.selector = ConcreteSelector.require(selector);
        this.shuffled = shuffled;
        this.shuffleSeed = shuffleSeed;
    }

    /**
     * Scans the classpath and composes the discovered definition.
     *
     * @return the discovered root definition, or an empty optional when no factory matched
     * @throws ResolverException if a discovered method is invalid or cannot be invoked
     */
    public Optional<Task> discover() {
        final var factories = new ArrayList<FactoryCandidate>();
        final var beforeHooks = new ArrayList<HookCandidate>();
        final var afterHooks = new ArrayList<HookCandidate>();
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .scan()) {
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(FACTORY_ANNOTATION)) {
                final var clazz = classInfo.loadClass();
                resolveFactoryMethod(clazz).ifPresent(factories::add);
            }
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(BEFORE_ALL_ANNOTATION)) {
                collectHooks(classInfo.loadClass(), Paramixel.BeforeAll.class, beforeHooks);
            }
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(AFTER_ALL_ANNOTATION)) {
                collectHooks(classInfo.loadClass(), Paramixel.AfterAll.class, afterHooks);
            }
        }
        if (factories.isEmpty()) {
            return Optional.empty();
        }
        beforeHooks.sort(hookOrder());
        afterHooks.sort(hookOrder().reversed());
        factories.sort(factoryOrder());

        final SequentialBuilder<Void> root =
                Tasks.sequential(Constants.ROOT_NAME).independent();
        root.tags(PlanCompiler.STRUCTURAL_TAG);
        final SequentialBuilder<Void> run = Tasks.sequential("run");
        run.tags(PlanCompiler.STRUCTURAL_TAG);
        final var beforeGroup = buildHookGroup("BeforeAll hooks", beforeHooks);
        beforeGroup.ifPresent(run::child);
        final var actions = Tasks.parallel("actions", Pool.named(DISCOVERY_POOL));
        actions.tags(PlanCompiler.FAIL_FAST_SCOPE_TAG, PlanCompiler.STRUCTURAL_TAG);
        if (shuffled) {
            actions.shuffle(shuffleSeed);
        }
        for (FactoryCandidate candidate : factories) {
            invoke(candidate.method(), "@Paramixel.Factory").ifPresent(actions::child);
        }
        run.child(actions);
        root.child(run);
        buildHookGroup("AfterAll hooks", afterHooks).ifPresent(root::child);
        return Optional.of(root.build());
    }

    /**
     * Returns the effective configuration.
     *
     * @return the configuration; never {@code null}
     */
    public Configuration configuration() {
        return configuration;
    }

    private Optional<Task> buildHookGroup(final String name, final List<HookCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final SequentialBuilder<Void> group = Tasks.sequential(name);
        for (HookCandidate candidate : candidates) {
            invoke(candidate.method(), name).ifPresent(group::child);
        }
        return Optional.of(group.build());
    }

    private void collectHooks(
            final Class<?> clazz,
            final Class<? extends java.lang.annotation.Annotation> annotation,
            final List<HookCandidate> target) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotation) || method.isAnnotationPresent(Paramixel.Disabled.class)) {
                continue;
            }
            validateMethod(method, "@" + annotation.getSimpleName());
            target.add(new HookCandidate(
                    priorityOf(method),
                    method.getDeclaringClass().getPackageName(),
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    method));
        }
    }

    private Optional<FactoryCandidate> resolveFactoryMethod(final Class<?> clazz) {
        final var seen = new HashSet<MethodKey>();
        final var candidates = new ArrayList<Method>();
        var current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                final var key = new MethodKey(method.getName(), method.getParameterTypes());
                if (!seen.add(key) || !method.isAnnotationPresent(Paramixel.Factory.class)) {
                    continue;
                }
                if (method.isAnnotationPresent(Paramixel.BeforeAll.class)
                        || method.isAnnotationPresent(Paramixel.AfterAll.class)) {
                    throw new ResolverException(
                            "Method " + locationOf(method) + " cannot be both @Paramixel.Factory and a run hook");
                }
                if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                    continue;
                }
                final var tags = tagsOf(method);
                if (!selector.test(current.getPackageName(), current.getName(), tags)) {
                    continue;
                }
                validateMethod(method, "@Paramixel.Factory");
                candidates.add(method);
            }
            current = current.getSuperclass();
        }
        if (candidates.size() > 1) {
            throw new ResolverException("Class " + clazz.getName() + " has more than one @Paramixel.Factory method: "
                    + candidates.stream()
                            .map(FactoryScanner::locationOf)
                            .sorted()
                            .toList());
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final var method = candidates.get(0);
        return Optional.of(new FactoryCandidate(
                priorityOf(method),
                method.getDeclaringClass().getPackageName(),
                method.getDeclaringClass().getName(),
                method.getName(),
                method));
    }

    private static List<String> tagsOf(final Method method) {
        final var tags = new ArrayList<String>();
        for (Paramixel.Tag tag : method.getAnnotationsByType(Paramixel.Tag.class)) {
            if (tag.value().isBlank()) {
                throw new ResolverException("Invalid @Paramixel.Tag on " + locationOf(method) + ": value is blank");
            }
            tags.add(tag.value());
        }
        return tags;
    }

    private static int priorityOf(final Method method) {
        final var annotation = method.getDeclaringClass().getAnnotation(Paramixel.Priority.class);
        return annotation == null ? 0 : annotation.value();
    }

    private static Comparator<FactoryCandidate> factoryOrder() {
        return Comparator.comparingInt(FactoryCandidate::priority)
                .reversed()
                .thenComparing(FactoryCandidate::packageName)
                .thenComparing(FactoryCandidate::className)
                .thenComparing(FactoryCandidate::methodName);
    }

    private static Comparator<HookCandidate> hookOrder() {
        return Comparator.comparingInt(HookCandidate::priority)
                .reversed()
                .thenComparing(HookCandidate::packageName)
                .thenComparing(HookCandidate::className)
                .thenComparing(HookCandidate::methodName);
    }

    private static void validateMethod(final Method method, final String label) {
        final var location = locationOf(method);
        final var modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new ResolverException("Invalid " + label + " method on " + location + ": must be public static");
        }
        if (method.getParameterCount() != 0) {
            throw new ResolverException("Invalid " + label + " method on " + location + ": must have no parameters");
        }
        if (!Task.class.isAssignableFrom(method.getReturnType())
                && !TaskBuilder.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException(
                    "Invalid " + label + " method on " + location + ": return type must be Task or TaskBuilder");
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static Optional<Task> invoke(final Method method, final String label) {
        final var location = locationOf(method);
        try {
            Object result;
            try {
                result = method.invoke(null);
            } catch (IllegalAccessException e) {
                method.setAccessible(true);
                try {
                    result = method.invoke(null);
                } catch (IllegalAccessException inner) {
                    throw new LinkageError("Cannot access " + label + " method " + location, inner);
                }
            }
            if (result == null) {
                return Optional.empty();
            }
            if (result instanceof Task task) {
                return Optional.of(task);
            }
            if (result instanceof TaskBuilder<?, ?> builder) {
                final var task = builder.build();
                if (task == null) {
                    throw new ResolverException(
                            "Invalid " + label + " method on " + location + ": build() returned null");
                }
                return Optional.of(task);
            }
            throw new ResolverException(
                    "Invalid " + label + " method on " + location + ": return type must be Task or TaskBuilder");
        } catch (InvocationTargetException e) {
            final var cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ResolverException("Failed to invoke " + label + " method on " + location, cause);
        }
    }

    private static String locationOf(final Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private record MethodKey(String name, Class<?>[] parameterTypes) {

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MethodKey key)) {
                return false;
            }
            return name.equals(key.name) && java.util.Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + java.util.Arrays.hashCode(parameterTypes);
        }
    }

    private record FactoryCandidate(
            int priority, String packageName, String className, String methodName, Method method) {}

    private record HookCandidate(
            int priority, String packageName, String className, String methodName, Method method) {}
}
