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

package nonapi.org.paramixel;

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
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassInfo;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ScanResult;
import nonapi.org.paramixel.listener.Constants;
import org.paramixel.api.Configuration;
import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Builder;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Scans the classpath for {@link org.paramixel.api.Paramixel.Factory}
 * methods, validates them, invokes them to produce {@link Action} instances, and
 * collapses the results into a root {@link Parallel} action.
 *
 * <p>Discovery applies package, class, and tag filters from the {@link Selector}. Invalid methods
 * throw {@link org.paramixel.api.exception.ResolverException} at resolution time; blank tag values are
 * collected as validation-failure actions rather than failing the entire scan. Methods that return
 * {@code null} are skipped without producing an action.
 */
public final class ActionResolver {

    private static final String ROOT_NAME = Constants.ROOT_NAME;
    private static final String BODY_NAME = "actions";
    private static final String BEFORE_ALL_SEQUENCE_NAME = "BeforeAll hooks";
    private static final String AFTER_ALL_SEQUENCE_NAME = "AfterAll hooks";
    private static final String FACTORY_ANNOTATION = Paramixel.Factory.class.getName();
    private static final String BEFORE_ALL_ANNOTATION = Paramixel.BeforeAll.class.getName();
    private static final String AFTER_ALL_ANNOTATION = Paramixel.AfterAll.class.getName();

    private final Configuration configuration;
    private final Selector selector;
    private final boolean shuffled;
    private final long seed;

    /**
     * Creates a resolver with the supplied configuration and selector.
     *
     * @param configuration the configuration used for discovered root parallelism
     * @param selector the selector used for discovery filtering; must not be {@code null}
     * @throws NullPointerException if {@code configuration} or {@code selector} is {@code null}
     */
    public ActionResolver(final Configuration configuration, final Selector selector) {
        this(configuration, selector, false, 0L);
    }

    /**
     * Creates a resolver with the supplied configuration, selector, and shuffle settings.
     *
     * <p>When {@code shuffled} is {@code true}, the root {@link Parallel} action built by
     * {@code collapse()} will shuffle its children using {@code seed}. The existing
     * two-argument constructor delegates to this one with {@code shuffled = false}
     * and {@code seed = 0L}.
     *
     * @param configuration the configuration used for discovered root parallelism
     * @param selector the selector used for discovery filtering; must not be {@code null}
     * @param shuffled whether to shuffle the root action's children
     * @param seed the PRNG seed when {@code shuffled} is {@code true}
     * @throws NullPointerException if {@code configuration} or {@code selector} is {@code null}
     */
    public ActionResolver(
            final Configuration configuration, final Selector selector, final boolean shuffled, final long seed) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.selector = Objects.requireNonNull(selector, "selector is null");
        this.shuffled = shuffled;
        this.seed = seed;
    }

    /**
     * Resolves the root action for all discovered factories matching this resolver's selector.
     *
     * @return the resolved root action, or an empty {@link Optional} when no factories match
     */
    public Optional<Action> resolveRootAction() {
        final var parallelism = resolveParallelism();
        return scan(new ClassGraph(), parallelism, shuffled, seed, null);
    }

    /**
     * Resolves the root action using the supplied action as the body, wrapping it
     * with discovered {@code @BeforeAll} and {@code @AfterAll} hooks when present.
     *
     * @param body the body action; must not be {@code null}
     * @return the resolved root action, never empty when {@code body} is non-null
     * @throws NullPointerException if {@code body} is {@code null}
     */
    public Optional<Action> resolveRootAction(final Action body) {
        Objects.requireNonNull(body, "body is null");
        final var parallelism = resolveParallelism();
        return scan(new ClassGraph(), parallelism, shuffled, seed, body);
    }

    private int resolveParallelism() {
        return configuration
                .getInteger(Configuration.RUNNER_PARALLELISM)
                .orElse(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Performs a classpath scan and returns the root action for all discovered and validated factories.
     *
     * <p>When {@code body} is {@code null}, factory methods annotated with {@link Paramixel.Factory}
     * are discovered and collapsed into a {@link Parallel} body action. When {@code body} is non-null,
     * factory scanning is skipped and the supplied action is used as the body.
     *
     * <p>{@code @BeforeAll} and {@code @AfterAll} hook methods are always discovered and composed into
     * a {@link Scope} wrapping the body when present.
     *
     * @param classGraph the ClassGraph instance to configure and scan
     * @param parallelism the parallelism of the root parallel action when factories are discovered
     * @param shuffled whether to shuffle the root action's children when factories are discovered
     * @param seed the PRNG seed when {@code shuffled} is {@code true}
     * @param body the body action when factory discovery is skipped, or {@code null}
     * @return the root action, or an empty {@link Optional} when no factories match and no body is supplied
     */
    Optional<Action> scan(
            final ClassGraph classGraph,
            final int parallelism,
            final boolean shuffled,
            final long seed,
            final Action body) {
        var candidates = new ArrayList<ActionCandidate>();
        var failures = new ArrayList<DiscoveryValidationFailure>();
        var beforeAllCandidates = new ArrayList<HookCandidate>();
        var afterAllCandidates = new ArrayList<HookCandidate>();
        try (ScanResult scanResult = classGraph
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .scan()) {
            if (body == null) {
                for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(FACTORY_ANNOTATION)) {
                    if (!acceptsClass(classInfo)) {
                        continue;
                    }
                    var clazz = classInfo.loadClass();
                    resolveFactoryMethod(clazz, failures).ifPresent(candidates::add);
                }
            }
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(BEFORE_ALL_ANNOTATION)) {
                var clazz = classInfo.loadClass();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(Paramixel.BeforeAll.class)) {
                        continue;
                    }
                    if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                        continue;
                    }
                    final var location = locationOf(method);
                    validateHookMethod(method, location, "@Paramixel.BeforeAll");
                    beforeAllCandidates.add(new HookCandidate(
                            priorityOf(method),
                            method.getDeclaringClass().getPackageName(),
                            method.getDeclaringClass().getName(),
                            method.getName(),
                            method,
                            location));
                }
            }
            for (ClassInfo classInfo : scanResult.getClassesWithMethodAnnotation(AFTER_ALL_ANNOTATION)) {
                var clazz = classInfo.loadClass();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(Paramixel.AfterAll.class)) {
                        continue;
                    }
                    if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                        continue;
                    }
                    final var location = locationOf(method);
                    validateHookMethod(method, location, "@Paramixel.AfterAll");
                    afterAllCandidates.add(new HookCandidate(
                            priorityOf(method),
                            method.getDeclaringClass().getPackageName(),
                            method.getDeclaringClass().getName(),
                            method.getName(),
                            method,
                            location));
                }
            }
        }

        var beforeActions = beforeAllCandidates.stream()
                .sorted(Comparator.comparingInt(HookCandidate::priority)
                        .reversed()
                        .thenComparing(HookCandidate::packageName)
                        .thenComparing(HookCandidate::className)
                        .thenComparing(HookCandidate::methodName))
                .map(c -> invokeStaticAction(c.method(), c.location(), "@Paramixel.BeforeAll"))
                .flatMap(Optional::stream)
                .toList();

        var afterActions = afterAllCandidates.stream()
                .sorted(Comparator.comparingInt(HookCandidate::priority)
                        .thenComparing(HookCandidate::packageName, Comparator.reverseOrder())
                        .thenComparing(HookCandidate::className, Comparator.reverseOrder())
                        .thenComparing(HookCandidate::methodName, Comparator.reverseOrder()))
                .map(c -> invokeStaticAction(c.method(), c.location(), "@Paramixel.AfterAll"))
                .flatMap(Optional::stream)
                .toList();

        var beforeHook = buildHookSequence(beforeActions, BEFORE_ALL_SEQUENCE_NAME);
        var afterHook = buildHookSequence(afterActions, AFTER_ALL_SEQUENCE_NAME);

        final Optional<Action> bodyAction;
        if (body != null) {
            bodyAction = Optional.of(body);
        } else {
            final var actions = candidates.stream()
                    .sorted(Comparator.comparingInt(ActionCandidate::priority)
                            .reversed()
                            .thenComparing(ActionCandidate::packageName)
                            .thenComparing(ActionCandidate::className)
                            .thenComparing(ActionCandidate::methodName))
                    .map(this::resolveActionFromMethod)
                    .flatMap(Optional::stream)
                    .toList();

            var allActions = Stream.concat(actions.stream(), discoveryFailureActions(failures).stream())
                    .toList();
            if (allActions.isEmpty()) {
                return Optional.empty();
            }
            bodyAction = collapse(allActions, parallelism, shuffled, seed);
        }

        if (beforeHook.isEmpty() && afterHook.isEmpty()) {
            return bodyAction;
        }

        var scope = Scope.builder(ROOT_NAME);
        beforeHook.ifPresent(scope::before);
        scope.body(bodyAction.orElseThrow());
        afterHook.ifPresent(scope::after);
        return Optional.of(scope.build());
    }

    Optional<Action> scan(final ClassGraph classGraph, final int parallelism, final boolean shuffled, final long seed) {
        return scan(classGraph, parallelism, shuffled, seed, null);
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
                failures.add(new DiscoveryValidationFailure(location, "tag value is blank"));
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
        var current = Objects.requireNonNull(clazz, "clazz is null");
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                MethodKey key = new MethodKey(method.getName(), method.getParameterTypes());
                if (!seenSignatures.add(key)) {
                    continue;
                }
                if (!method.isAnnotationPresent(Paramixel.Factory.class)) {
                    continue;
                }
                if (method.isAnnotationPresent(Paramixel.BeforeAll.class)
                        || method.isAnnotationPresent(Paramixel.AfterAll.class)) {
                    failures.add(
                            new DiscoveryValidationFailure(
                                    locationOf(method),
                                    "method cannot be both @@Paramixel.Factory and a hook annotation (@Paramixel.BeforeAll, @Paramixel.AfterAll)"));
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

    private List<? extends Action> discoveryFailureActions(final List<DiscoveryValidationFailure> failures) {
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

    private Optional<Action> collapse(
            final List<? extends Action> factoryActions,
            final int parallelism,
            final boolean shuffled,
            final long seed) {
        if (factoryActions.isEmpty()) {
            return Optional.empty();
        }
        var bodyVar = Parallel.builder(BODY_NAME).parallelism(parallelism);
        if (shuffled) {
            bodyVar.shuffle(seed);
        }
        for (Action action : factoryActions) {
            bodyVar.child(action);
        }
        return Optional.of(bodyVar.build());
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
        if (!Action.class.isAssignableFrom(method.getReturnType())
                && !Builder.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException(
                    "Invalid @Paramixel.Factory method on " + location + ": return type must be Action or Builder");
        }
    }

    private static void validateHookMethod(final Method method, final String location, final String annotationLabel) {
        final var modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new ResolverException(
                    "Invalid " + annotationLabel + " method on " + location + ": method must be public static");
        }
        if (method.getParameterCount() != 0) {
            throw new ResolverException(
                    "Invalid " + annotationLabel + " method on " + location + ": method must have no parameters");
        }
        if (!Action.class.isAssignableFrom(method.getReturnType())
                && !Builder.class.isAssignableFrom(method.getReturnType())) {
            throw new ResolverException("Invalid " + annotationLabel + " method on " + location
                    + ": return type must be Action or Builder");
        }
    }

    private Optional<Action> resolveActionFromMethod(final ActionCandidate candidate) {
        return invokeStaticAction(candidate.method(), candidate.location(), "@Paramixel.Factory");
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static Optional<Action> invokeStaticAction(
            final Method method, final String location, final String annotationLabel) {
        try {
            Object result;
            try {
                result = method.invoke(null);
            } catch (IllegalAccessException e) {
                method.setAccessible(true);
                try {
                    result = method.invoke(null);
                } catch (IllegalAccessException ex) {
                    throw new LinkageError("Cannot access " + annotationLabel + " method " + method, ex);
                }
            }
            if (result == null) {
                return Optional.empty();
            }
            Action action;
            if (result instanceof Action a) {
                action = a;
            } else if (result instanceof Builder b) {
                action = b.build();
                if (action == null) {
                    throw new ResolverException(
                            "Invalid " + annotationLabel + " method on " + location + ": build() returned null");
                }
            } else {
                throw new ResolverException("Invalid " + annotationLabel + " method on " + location
                        + ": return type must be Action or Builder");
            }
            return Optional.of(action);
        } catch (InvocationTargetException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ResolverException("Failed to invoke " + annotationLabel + " method on " + location, cause);
        }
    }

    private static Optional<Action> buildHookSequence(final List<Action> actions, final String displayName) {
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        if (actions.size() == 1) {
            return Optional.of(actions.get(0));
        }
        var builder = Sequence.builder(displayName);
        for (Action action : actions) {
            builder.child(action);
        }
        return Optional.of(builder.build());
    }

    private record MethodKey(String name, Class<?>[] parameterTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MethodKey other)) {
                return false;
            }
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

    private record HookCandidate(
            int priority, String packageName, String className, String methodName, Method method, String location) {}
}
