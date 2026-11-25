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

import java.lang.annotation.Annotation;
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
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResult;
import nonapi.org.paramixel.reporting.Constants;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.paramixel.api.Configuration;
import org.paramixel.api.Parallel;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Sequential;
import org.paramixel.api.Task;
import org.paramixel.api.TaskBuilder;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Discovers {@link Paramixel.TaskFactory}, {@link Paramixel.BeforeAll}, and {@link Paramixel.AfterAll} methods on
 * the classpath and composes them into one definition.
 *
 * <p>Discovery validates every matched method — {@code public static}, no parameters or exactly one
 * {@link Configuration} parameter, returning {@link Task} or a {@link TaskBuilder} — before any discovered test work
 * runs, so an invalid factory fails the run with {@link ResolverException} instead of executing a partial suite.
 *
 * <p>A factory may declare a single {@link Configuration} parameter to receive the run's effective configuration, the
 * same instance {@link org.paramixel.api.Context#configuration()} exposes, so that configuration can determine the shape
 * of the definition rather than only the behavior of its tasks. The parameter is optional, and the return type is
 * independent of it, so every combination of {@link Task} or {@link TaskBuilder} with or without the parameter is
 * accepted. The configuration passed to a factory has already been validated, so a factory never has to re-check it.
 * Run hooks are declared without parameters.
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
 *     tasks (parallel, pool "discovery")   &lt;- discovered factories
 *   AfterAll hooks (when any)
 * </pre>
 *
 * <p>The independent root guarantees that {@code AfterAll} hooks run after a started run even when a factory or a
 * {@code BeforeAll} hook failed; the dependent {@code run} group guarantees that a failing {@code BeforeAll} hook skips
 * the factories. The {@code tasks} group carries the run-level fail-fast scope and uses the {@code discovery} pool,
 * whose capacity defaults to {@code paramixel.parallelism}.
 */
public final class FactoryScanner {

    /** Name of the pool that bounds concurrent discovered factories. */
    public static final String DISCOVERY_POOL = "discovery";

    private static final String FACTORY_ANNOTATION = Paramixel.TaskFactory.class.getName();
    private static final String BEFORE_ALL_ANNOTATION = Paramixel.BeforeAll.class.getName();
    private static final String AFTER_ALL_ANNOTATION = Paramixel.AfterAll.class.getName();

    private final Configuration configuration;
    private final ConcreteSelector selector;
    private final boolean shuffled;
    private final long shuffleSeed;
    private final BooleanSupplier cancellationRequested;
    private final List<String> skipped = new ArrayList<>();

    /**
     * Creates a scanner.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param selector the selection filter; must not be {@code null}
     * @param shuffled whether to shuffle discovered factories
     * @param shuffleSeed the shuffle seed when {@code shuffled} is {@code true}
     * @param cancellationRequested whether discovery should stop invoking methods
     * @throws NullPointerException if an argument is {@code null}
     */
    public FactoryScanner(
            final Configuration configuration,
            final Selector selector,
            final boolean shuffled,
            final long shuffleSeed,
            final BooleanSupplier cancellationRequested) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.selector = ConcreteSelector.require(selector);
        this.shuffled = shuffled;
        this.shuffleSeed = shuffleSeed;
        this.cancellationRequested = Objects.requireNonNull(cancellationRequested, "cancellationRequested is null");
    }

    /**
     * Scans the classpath and composes the discovered definition.
     *
     * @return the discovered definition and its framework groups, or empty when no factory contributed a task
     * @throws ResolverException if a discovered method is invalid or cannot be invoked
     */
    public Optional<DiscoveredTasks> discover() {
        skipped.clear();
        if (cancellationRequested.getAsBoolean()) {
            return Optional.empty();
        }
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
            if (cancellationRequested.getAsBoolean()) {
                return Optional.empty();
            }
            // The scan also returns subclasses that inherit an annotated method, and resolving a subclass walks up to
            // the declaring class, so the same static factory can resolve more than once; it is invoked only once.
            final var resolvedFactories = new HashSet<Method>();
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(FACTORY_ANNOTATION)) {
                final var clazz = classInfo.loadClass();
                resolveFactoryMethod(clazz)
                        .filter(candidate -> resolvedFactories.add(candidate.method()))
                        .ifPresent(factories::add);
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

        final var root = Sequential.builder(Constants.ROOT_NAME).independent();
        final var run = Sequential.builder("run");
        final var beforeGroup = buildHookGroup("BeforeAll hooks", beforeHooks, true);
        beforeGroup.ifPresent(run::step);
        final var tasks = Parallel.builder("tasks", Pool.named(DISCOVERY_POOL));
        if (shuffled) {
            tasks.shuffle(shuffleSeed);
        }
        final var factoryTags = new ArrayList<List<String>>();
        for (FactoryCandidate candidate : factories) {
            if (cancellationRequested.getAsBoolean()) {
                return Optional.empty();
            }
            invoke(candidate.method(), "@Paramixel.TaskFactory").ifPresent(task -> {
                tasks.step(task);
                factoryTags.add(candidate.tags());
            });
        }
        if (cancellationRequested.getAsBoolean()) {
            return Optional.empty();
        }
        final var taskGroup = tasks.build();
        if (taskGroup.children().isEmpty()) {
            return Optional.empty();
        }
        run.step(taskGroup);
        final var runGroup = run.build();
        root.step(runGroup);
        buildHookGroup("AfterAll hooks", afterHooks, false).ifPresent(root::step);
        return Optional.of(new DiscoveredTasks(root.build(), runGroup, taskGroup, List.copyOf(factoryTags)));
    }

    /**
     * Returns why discovered methods contributed nothing during the last {@link #discover()}.
     *
     * <p>A method that returns {@code null} is an explicit skip; each entry names the method so the skip is recorded
     * rather than silent.
     *
     * @return the skip reasons in invocation order; never {@code null}
     */
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    /**
     * Returns the effective configuration.
     *
     * @return the configuration; never {@code null}
     */
    public Configuration configuration() {
        return configuration;
    }

    private Optional<Task> buildHookGroup(
            final String name, final List<HookCandidate> candidates, final boolean dependent) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final var group = Sequential.builder(name);
        if (!dependent) {
            group.independent();
        }
        for (HookCandidate candidate : candidates) {
            if (cancellationRequested.getAsBoolean()) {
                break;
            }
            invoke(candidate.method(), name).ifPresent(group::step);
        }
        return Optional.of(group.build());
    }

    private void collectHooks(
            final Class<?> clazz, final Class<? extends Annotation> annotation, final List<HookCandidate> target) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotation)
                    || method.isAnnotationPresent(Paramixel.Disabled.class)
                    || method.isAnnotationPresent(Paramixel.TaskFactory.class)) {
                continue;
            }
            validateMethod(method, "@" + annotation.getSimpleName(), false);
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
        final var candidates = new ArrayList<FactoryCandidate>();
        var current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                final var key = new MethodKey(method.getName(), method.getParameterTypes());
                if (!seen.add(key)
                        || !method.isAnnotationPresent(Paramixel.TaskFactory.class)
                        || method.isAnnotationPresent(Paramixel.Disabled.class)) {
                    continue;
                }
                if (!selector.acceptsClass(current.getPackageName(), current.getName())) {
                    continue;
                }
                final var tags = tagsOf(method);
                if (!selector.test(current.getPackageName(), current.getName(), tags)) {
                    continue;
                }
                if (method.isAnnotationPresent(Paramixel.BeforeAll.class)
                        || method.isAnnotationPresent(Paramixel.AfterAll.class)) {
                    throw new ResolverException(
                            "Method " + locationOf(method) + " cannot be both @Paramixel.TaskFactory and a run hook");
                }
                validateMethod(method, "@Paramixel.TaskFactory", true);
                candidates.add(new FactoryCandidate(
                        priorityOf(method),
                        current.getPackageName(),
                        current.getName(),
                        method.getName(),
                        method,
                        tags));
            }
            current = current.getSuperclass();
        }
        if (candidates.size() > 1) {
            throw new ResolverException(
                    "Class " + clazz.getName() + " has more than one @Paramixel.TaskFactory method: "
                            + candidates.stream()
                                    .map(FactoryCandidate::method)
                                    .map(FactoryScanner::locationOf)
                                    .sorted()
                                    .toList()
                            + "; declare one factory per class and repeat the same parameter shape to override an"
                            + " inherited factory");
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private static List<String> tagsOf(final Method method) {
        final var tags = new ArrayList<String>();
        for (Paramixel.Tag tag : method.getAnnotationsByType(Paramixel.Tag.class)) {
            if (tag.value().isBlank()) {
                throw new ResolverException("Invalid @Paramixel.Tag on " + locationOf(method) + ": value is blank");
            }
            tags.add(tag.value());
        }
        return List.copyOf(tags);
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

    /**
     * Rejects a discovered definition anchored to a class other than the declaring class of the method that built it.
     *
     * <p>The anchor is what authorizes a hook's declaring class, so a factory that anchors its tree elsewhere would
     * wire hooks from a class the discovery contract never inspected. A definition that uses no identifiers has no
     * anchor and is not affected.
     *
     * @param task the definition the method returned; must not be {@code null}
     * @param method the discovered method; must not be {@code null}
     * @throws ResolverException if the anchor names a different class
     */
    private static void validateAnchor(final Task task, final Method method) {
        final var anchor = task.anchor().orElse(null);
        final var declaring = method.getDeclaringClass();
        if (anchor != null && anchor != declaring) {
            throw new ResolverException("Definition from " + locationOf(method) + " is anchored to " + anchor.getName()
                    + "; a discovered method must anchor its definition to its declaring class "
                    + declaring.getName());
        }
    }

    /**
     * Validates the shape of a discovered method.
     *
     * @param method the discovered method; must not be {@code null}
     * @param label the annotation name used in diagnostics; must not be {@code null}
     * @param configurationParameter whether the method may declare the one supported configuration parameter
     * @throws ResolverException if the method shape is invalid
     */
    private static void validateMethod(final Method method, final String label, final boolean configurationParameter) {
        final var location = locationOf(method);
        final var modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new ResolverException("Invalid " + label + " method on " + location + ": must be public static");
        }
        validateParameters(method, label, configurationParameter);
        if (!Task.class.isAssignableFrom(method.getReturnType())
                && !TaskBuilder.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException(
                    "Invalid " + label + " method on " + location + ": return type must be Task or TaskBuilder");
        }
    }

    /**
     * Validates the parameter list of a discovered method.
     *
     * <p>A factory accepts no parameters or exactly one {@link Configuration} parameter; every other discovered method
     * accepts no parameters. The configuration type must match exactly, because the framework supplies its own
     * configuration instance and cannot produce a subtype or a supertype on the caller's behalf.
     *
     * @param method the discovered method; must not be {@code null}
     * @param label the annotation name used in diagnostics; must not be {@code null}
     * @param configurationParameter whether the method may declare the one supported configuration parameter
     * @throws ResolverException if the parameter list is invalid
     */
    private static void validateParameters(
            final Method method, final String label, final boolean configurationParameter) {
        final var location = locationOf(method);
        final var count = method.getParameterCount();
        if (count == 0) {
            return;
        }
        if (configurationParameter && count == 1 && method.getParameterTypes()[0] == Configuration.class) {
            return;
        }
        if (!configurationParameter) {
            throw new ResolverException("Invalid " + label + " method on " + location + ": must have no parameters");
        }
        throw new ResolverException(
                "Invalid " + label + " method on " + location + ": must take no parameters or exactly" + " one "
                        + Configuration.class.getName() + " parameter but took " + describeParameters(method));
    }

    /**
     * Describes the declared parameter types of a method for diagnostics.
     *
     * @param method the method; must not be {@code null}
     * @return the comma-separated parameter type names; never {@code null}
     */
    private static String describeParameters(final Method method) {
        return Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", "));
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private Optional<Task> invoke(final Method method, final String label) {
        final var location = locationOf(method);
        final var arguments = argumentsFor(method);
        try {
            Object result;
            try {
                result = method.invoke(null, arguments);
            } catch (IllegalAccessException e) {
                method.setAccessible(true);
                try {
                    result = method.invoke(null, arguments);
                } catch (IllegalAccessException inner) {
                    throw new LinkageError("Cannot access " + label + " method " + location, inner);
                }
            }
            if (cancellationRequested.getAsBoolean()) {
                return Optional.empty();
            }
            if (result == null) {
                skipped.add(label + " method " + location + " returned null and was skipped");
                return Optional.empty();
            }
            if (result instanceof Task task) {
                validateAnchor(task, method);
                return Optional.of(task);
            }
            if (result instanceof TaskBuilder<?> builder) {
                final var task = builder.build();
                if (cancellationRequested.getAsBoolean()) {
                    return Optional.empty();
                }
                if (task == null) {
                    throw new ResolverException(
                            "Invalid " + label + " method on " + location + ": build() returned null");
                }
                validateAnchor(task, method);
                return Optional.of(task);
            }
            throw new ResolverException(
                    "Invalid " + label + " method on " + location + ": return type must be Task or TaskBuilder");
        } catch (InvocationTargetException e) {
            final var cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof CompilationException compilation) {
                throw compilation;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ResolverException("Failed to invoke " + label + " method on " + location, cause);
        }
    }

    /**
     * Returns the arguments a discovered method is invoked with.
     *
     * @param method the discovered method; must not be {@code null}
     * @return the run configuration when the method declares its one parameter, otherwise an empty array; never
     *     {@code null}
     */
    private Object[] argumentsFor(final Method method) {
        return method.getParameterCount() == 1 ? new Object[] {configuration} : new Object[0];
    }

    private static String locationOf(final Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    /**
     * The discovered tree and the framework groups recognized by the compiler.
     *
     * @param root the discovered root
     * @param runGroup the group that gates factory tasks on the before-all hooks
     * @param factoryGroup the group whose children honor discovery fail-fast
     * @param factoryTags the annotation tags in the factory group's declaration order
     */
    public record DiscoveredTasks(Task root, Task runGroup, Task factoryGroup, List<List<String>> factoryTags) {}

    private record MethodKey(String name, Class<?>[] parameterTypes) {

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MethodKey key)) {
                return false;
            }
            return name.equals(key.name) && Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + Arrays.hashCode(parameterTypes);
        }
    }

    private record FactoryCandidate(
            int priority, String packageName, String className, String methodName, Method method, List<String> tags) {}

    private record HookCandidate(
            int priority, String packageName, String className, String methodName, Method method) {}
}
