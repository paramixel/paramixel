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

package nonapi.org.paramixel.compiler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.paramixel.api.Configuration;
import org.paramixel.api.ExecutionMode;
import org.paramixel.api.Pool;
import org.paramixel.api.ResourceDeclaration;
import org.paramixel.api.Task;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * Compiles an immutable task definition into an immutable plan, validating every invariant before any user code runs.
 *
 * <p>Compilation is pure and repeatable: it never invokes a factory, lifecycle callback, body, or runtime predicate, so
 * a definition that fails validation cannot have produced side effects.
 *
 * <p>Validation collects independent diagnostics instead of failing at the first problem. Errors prevent execution;
 * warnings are exposed on the plan. The compiler states its own limit honestly: it can prove structural facts such as
 * undefined pools, same-pool nesting, and acquisition-order cycles, but it cannot prove the absence of deadlocks inside
 * arbitrary user code.
 *
 * <p>Occurrence identifiers are structural ordinal paths ({@code 0}, {@code 0.1}, {@code 0.1.0}) and are stable for a
 * structurally identical definition. Definition identifiers are structural fingerprints, so a reused definition has
 * one definition identity and several occurrence identities.
 */
public final class PlanCompiler {

    /** Tag that marks the discovery factory group, whose children honor the run-level fail-fast policy. */
    public static final String FAIL_FAST_SCOPE_TAG = "paramixel.discovery.actions";

    /**
     * Tag that marks framework scaffolding: nodes the discovery composition adds for gating, scheduling, and hook
     * wrapping. Such a node keeps its own name as its display path, and its children start a fresh display path, so
     * user-facing paths never repeat a constant {@code Paramixel > run > actions} segment.
     */
    public static final String STRUCTURAL_TAG = "paramixel.structural";

    /**
     * Separator between levels of a display path, for example {@code KafkaTest > apache/kafka:3.7.2 > tests}.
     *
     * <p>{@code >} is used instead of {@code /} because task names often contain slashes (Docker images, URLs, file
     * paths), which made levels hard to tell apart. Display paths are for humans; identity is the occurrence id.
     */
    public static final String PATH_SEPARATOR = " > ";

    private final Configuration configuration;
    private final Map<Pool, Integer> poolOverrides;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String, Integer> capacities = new LinkedHashMap<>();
    private final Set<String> reportedUndefinedPools = new HashSet<>();

    /**
     * Creates a compiler.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param poolOverrides explicit capacities that take precedence over configuration; must not be {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public PlanCompiler(final Configuration configuration, final Map<Pool, Integer> poolOverrides) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.poolOverrides = Map.copyOf(Objects.requireNonNull(poolOverrides, "poolOverrides is null"));
    }

    /**
     * Compiles the definition.
     *
     * @param root the root definition; must not be {@code null}
     * @return the immutable plan; never {@code null}
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws CompilationException if the definition or plan is invalid
     */
    public CompiledPlan compile(final Task root) {
        Objects.requireNonNull(root, "root is null");
        diagnostics.clear();
        capacities.clear();
        reportedUndefinedPools.clear();
        final var counter = new int[] {0};
        final var compiledRoot = expand(root, "0", null, null, null, 0, counter);
        final var occurrences = new ArrayList<CompiledOccurrence>();
        collectPreOrder(compiledRoot, occurrences);
        validate(root, compiledRoot);
        final var errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            final var messages = errors.stream().map(Diagnostic::toString).toList();
            throw new CompilationException(
                    "Invalid Paramixel plan (" + errors.size() + " error(s)):\n" + String.join("\n", messages),
                    messages,
                    null);
        }
        final var warnings = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == Diagnostic.Severity.WARNING)
                .toList();
        final var poolNames = new LinkedHashSet<String>();
        final var lockNames = new LinkedHashSet<String>();
        for (CompiledOccurrence occurrence : occurrences) {
            if (occurrence.poolName() != null) {
                poolNames.add(occurrence.poolName());
            }
            occurrence.task().exclusiveLock().ifPresent(lockNames::add);
        }
        return new CompiledPlan(compiledRoot, occurrences, poolNames, capacities, lockNames, warnings);
    }

    private CompiledOccurrence expand(
            final Task task,
            final String id,
            final String parentId,
            final String parentDisplayPath,
            final String bindingSlot,
            final int depth,
            final int[] counter) {
        final var mode = task.mode();
        final var poolName = task.pool().map(Pool::name).orElse(null);
        final var structural = task.tags().contains(STRUCTURAL_TAG);
        final var path = structural || parentDisplayPath == null
                ? task.name()
                : parentDisplayPath + PATH_SEPARATOR + task.name();
        final var budget = mode == ExecutionMode.PARALLEL ? capacityOf(poolName, path) : 1;
        final var failFastScope = task.tags().contains(FAIL_FAST_SCOPE_TAG);
        final var childDisplayPath = structural ? null : path;
        final var index = counter[0]++;
        final var childBinding = task.instanceFactory().isPresent() ? id : bindingSlot;
        final var ordered = orderedChildren(task);
        final var childOccurrences = new ArrayList<CompiledOccurrence>(ordered.size());
        var ordinal = 0;
        for (Task child : ordered) {
            childOccurrences.add(
                    expand(child, id + "." + ordinal, id, childDisplayPath, childBinding, depth + 1, counter));
            ordinal++;
        }
        final var executionOrder = ordered.stream().map(Task::name).toList();
        return new CompiledOccurrence(
                id,
                definitionId(task),
                parentId,
                task.name(),
                path,
                task,
                mode,
                poolName,
                budget,
                childBinding,
                index,
                depth,
                failFastScope,
                task.shuffleSeed().orElse(null),
                executionOrder,
                childOccurrences);
    }

    private static List<Task> orderedChildren(final Task task) {
        final var children = new ArrayList<>(task.children());
        final var seed = task.shuffleSeed().orElse(null);
        if (seed == null || children.size() < 2) {
            return children;
        }
        final var random = new Random(seed);
        for (var i = children.size() - 1; i > 0; i--) {
            final var j = random.nextInt(i + 1);
            Collections.swap(children, i, j);
        }
        return children;
    }

    private int capacityOf(final String poolName, final String path) {
        if (poolName == null) {
            return 1;
        }
        final var existing = capacities.get(poolName);
        if (existing != null) {
            return existing;
        }
        for (Map.Entry<Pool, Integer> override : poolOverrides.entrySet()) {
            if (override.getKey().name().equals(poolName)) {
                capacities.put(poolName, override.getValue());
                return override.getValue();
            }
        }
        final var raw = configuration.getString(Configuration.poolKey(poolName)).orElse(null);
        if (raw == null) {
            if (reportedUndefinedPools.add(poolName)) {
                diagnostics.add(Diagnostic.error(
                        "POOL_UNDEFINED",
                        path,
                        "Parallel pool '" + poolName + "' has no configured capacity",
                        "Set '" + Configuration.poolKey(poolName) + "' or call Runner.builder().pool(Pool.named(\""
                                + poolName + "\"), capacity)"));
            }
            capacities.put(poolName, 1);
            return 1;
        }
        final int capacity;
        try {
            capacity = PoolSizes.parse(raw);
        } catch (IllegalArgumentException e) {
            diagnostics.add(Diagnostic.error(
                    "POOL_INVALID",
                    path,
                    "Invalid capacity '" + raw + "' for pool '" + poolName + "': " + e.getMessage(),
                    "Use a positive integer, @cores, @cores*N or @cores/N"));
            capacities.put(poolName, 1);
            return 1;
        }
        capacities.put(poolName, capacity);
        return capacity;
    }

    private void collectPreOrder(final CompiledOccurrence occurrence, final List<CompiledOccurrence> target) {
        target.add(occurrence);
        occurrence.children().forEach(child -> collectPreOrder(child, target));
    }

    private void validate(final Task root, final CompiledOccurrence compiledRoot) {
        checkTask(root, compiledRoot, List.of(), new LinkedHashSet<>(), new LinkedHashSet<>());
        validateAcquisitionOrder(compiledRoot);
    }

    private void checkTask(
            final Task task,
            final CompiledOccurrence occurrence,
            final List<Task> ancestors,
            final Set<String> ancestorPools,
            final Set<String> ancestorLocks) {
        final var path = occurrence.path();
        if (task.name().isBlank()) {
            diagnostics.add(Diagnostic.error("NAME_BLANK", path, "Task name is blank", "Give the task a name"));
        }
        if (occurrence.mode() == ExecutionMode.PARALLEL && occurrence.poolName() == null) {
            diagnostics.add(Diagnostic.error(
                    "PARALLEL_WITHOUT_POOL",
                    path,
                    "Parallel task has no pool",
                    "Use parallel(name, Pool.named(\"...\"), ...)"));
        }
        if (task.requiresPrimaryInstance() && occurrence.bindingSlot() == null) {
            diagnostics.add(Diagnostic.error(
                    "INSTANCE_BINDING_MISSING",
                    path,
                    "Task uses typed instance callbacks but no enclosing forInstance task owns an instance",
                    "Wrap the task in forInstance(name, factory) or use the no-argument callbacks"));
        }
        task.timeout().ifPresent(timeout -> checkTimeout(timeout, path));
        if (task.retry().isPresent() && task.repeat().isPresent()) {
            diagnostics.add(Diagnostic.error(
                    "RETRY_AND_REPEAT",
                    path,
                    "A task may not be both retried and repeated",
                    "Choose one policy: retry re-attempts failures, repeat replays iterations"));
        }
        task.retry().ifPresent(policy -> checkRetry(policy, path));
        task.repeat().ifPresent(policy -> checkRepeat(policy, path));
        for (ResourceDeclaration declaration : task.resources()) {
            for (Task ancestor : ancestors) {
                final var shadowed = ancestor.resources().stream()
                        .anyMatch(parent -> parent.key().equals(declaration.key()));
                if (shadowed) {
                    diagnostics.add(Diagnostic.error(
                            "RESOURCE_SHADOWING",
                            path,
                            "Resource key " + declaration.key() + " is declared by an ancestor and again here",
                            "Use a distinct key; ambiguous shadowing is rejected in 7.0.0"));
                }
            }
        }
        final var poolName = occurrence.poolName();
        if (poolName != null && ancestorPools.contains(poolName)) {
            diagnostics.add(Diagnostic.error(
                    "POOL_NESTED_SAME",
                    path,
                    "Pool '" + poolName + "' is already active on this ancestry path",
                    "Nested use of the same pool is rejected regardless of configured capacity"));
        }
        final var lockName = task.exclusiveLock().orElse(null);
        if (lockName != null && ancestorLocks.contains(lockName)) {
            diagnostics.add(Diagnostic.error(
                    "LOCK_NESTED_SAME",
                    path,
                    "Exclusive lock '" + lockName + "' is already held on this ancestry path",
                    "A nested acquisition of the same lock can never succeed"));
        }
        if (task.isEmpty()) {
            diagnostics.add(
                    Diagnostic.warning("EMPTY_TASK", path, "Task declares no work and will pass as a no-op", null));
        }
        final var childAncestors = new ArrayList<>(ancestors);
        childAncestors.add(task);
        final var childPools = new LinkedHashSet<>(ancestorPools);
        if (poolName != null) {
            childPools.add(poolName);
        }
        final var childLocks = new LinkedHashSet<>(ancestorLocks);
        if (lockName != null) {
            childLocks.add(lockName);
        }
        for (CompiledOccurrence child : occurrence.children()) {
            checkTask(child.task(), child, childAncestors, childPools, childLocks);
        }
    }

    private void checkTimeout(final Duration timeout, final String path) {
        if (timeout.isZero() || timeout.isNegative()) {
            diagnostics.add(Diagnostic.error(
                    "TIMEOUT_INVALID", path, "Timeout must be positive: " + timeout, "Use a positive Duration"));
        }
    }

    private void checkRetry(final RetryPolicy policy, final String path) {
        if (policy.maxAttempts() < 1 || policy.maxAttempts() > RetryPolicy.MAX_ATTEMPTS) {
            diagnostics.add(Diagnostic.error(
                    "RETRY_UNBOUNDED",
                    path,
                    "Retry attempt limit is outside 1.." + RetryPolicy.MAX_ATTEMPTS + ": " + policy.maxAttempts(),
                    "Use RetryPolicy.attempts(n) with a finite bound"));
        }
        if (policy.retryOn().isEmpty()) {
            diagnostics.add(Diagnostic.error("RETRY_NO_TRIGGER", path, "Retry policy has no trigger status", null));
        }
        if (policy.retryOn().contains(org.paramixel.api.Status.Kind.CANCELED)
                || policy.retryOn().contains(org.paramixel.api.Status.Kind.ABORTED)) {
            diagnostics.add(Diagnostic.error(
                    "RETRY_ILLEGAL_TRIGGER",
                    path,
                    "Retry policy must not retry cancellation or assumption abort",
                    "Retry only FAILED (optionally SKIPPED)"));
        }
    }

    private void checkRepeat(final RepeatPolicy policy, final String path) {
        if (policy.maxIterations() < 1 || policy.maxIterations() > RepeatPolicy.MAX_ITERATIONS) {
            diagnostics.add(Diagnostic.error(
                    "REPEAT_UNBOUNDED",
                    path,
                    "Repeat iteration limit is outside 1.." + RepeatPolicy.MAX_ITERATIONS + ": "
                            + policy.maxIterations(),
                    "Set a finite maxIterations bound"));
        }
    }

    private void validateAcquisitionOrder(final CompiledOccurrence root) {
        final var edges = new HashMap<String, Set<String>>();
        final var examples = new HashMap<String, String>();
        collectAcquisitionEdges(root, List.of(), edges, examples);
        findCycle(edges).ifPresent(cycle -> {
            final var description = String.join(" -> ", cycle);
            final var example = examples.getOrDefault(cycle.get(0), "");
            diagnostics.add(Diagnostic.error(
                    "ACQUISITION_CYCLE",
                    example,
                    "Resource acquisition order contains a cycle: " + description,
                    "Reorder the nested pools and locks so every plan path acquires them in one global order"));
        });
    }

    private void collectAcquisitionEdges(
            final CompiledOccurrence occurrence,
            final List<String> inherited,
            final Map<String, Set<String>> edges,
            final Map<String, String> examples) {
        final var resources = new ArrayList<>(inherited);
        if (occurrence.poolName() != null) {
            resources.add("pool:" + occurrence.poolName());
        }
        occurrence.task().exclusiveLock().ifPresent(lock -> resources.add("lock:" + lock));
        for (var i = 1; i < resources.size(); i++) {
            final var from = resources.get(i - 1);
            final var to = resources.get(i);
            edges.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
            examples.putIfAbsent(from, occurrence.path());
        }
        for (CompiledOccurrence child : occurrence.children()) {
            collectAcquisitionEdges(child, resources, edges, examples);
        }
    }

    private static java.util.Optional<List<String>> findCycle(final Map<String, Set<String>> edges) {
        final var colors = new HashMap<String, Integer>();
        final var stack = new ArrayList<String>();
        for (String node : edges.keySet()) {
            final var cycle = visit(node, edges, colors, stack);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<List<String>> visit(
            final String node,
            final Map<String, Set<String>> edges,
            final Map<String, Integer> colors,
            final List<String> stack) {
        final var color = colors.getOrDefault(node, 0);
        if (color == 2) {
            return java.util.Optional.empty();
        }
        if (color == 1) {
            final var start = stack.indexOf(node);
            final var cycle = new ArrayList<>(stack.subList(start < 0 ? 0 : start, stack.size()));
            cycle.add(node);
            return java.util.Optional.of(cycle);
        }
        colors.put(node, 1);
        stack.add(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            final var cycle = visit(next, edges, colors, stack);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        stack.remove(stack.size() - 1);
        colors.put(node, 2);
        return java.util.Optional.empty();
    }

    /**
     * Computes a stable structural fingerprint for a definition node.
     *
     * <p>The fingerprint depends only on names, modes, pools, and structure, never on object identity, thread identity,
     * or completion order, so it is identical across JVMs for the same definition.
     *
     * @param task the definition; must not be {@code null}
     * @return a hexadecimal identifier prefixed with {@code d}; never {@code null}
     */
    public static String definitionId(final Task task) {
        Objects.requireNonNull(task, "task is null");
        final var hash = new long[] {0xcbf29ce484222325L};
        hashTask(task, hash);
        return "d" + Long.toHexString(hash[0]);
    }

    private static void hashTask(final Task task, final long[] hash) {
        hashText(task.name(), hash);
        hashText(task.mode().name(), hash);
        hashText(task.pool().map(Pool::name).orElse(""), hash);
        hashText(task.instanceFactory().isPresent() ? "i" : "-", hash);
        hashText(task.body().isPresent() ? "b" : "-", hash);
        hashText(Integer.toString(task.before().size()), hash);
        hashText(Integer.toString(task.after().size()), hash);
        hashText(task.condition().isPresent() ? "c" : "-", hash);
        for (Task child : task.children()) {
            hashText("[", hash);
            hashTask(child, hash);
            hashText("]", hash);
        }
    }

    private static void hashText(final String text, final long[] hash) {
        for (var i = 0; i < text.length(); i++) {
            hash[0] ^= text.charAt(i);
            hash[0] *= 0x100000001b3L;
        }
        hash[0] ^= '|';
        hash[0] *= 0x100000001b3L;
    }
}
