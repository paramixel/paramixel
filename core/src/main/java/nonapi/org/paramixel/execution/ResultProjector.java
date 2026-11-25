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

package nonapi.org.paramixel.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.paramixel.api.Configuration;
import org.paramixel.api.Failure;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.TaskResult;

/**
 * Projects the engine's runtime occurrence tree into immutable result objects.
 *
 * <p>The projector holds no scheduling state and never mutates the occurrences it reads. The engine calls it only on
 * its own thread — {@link #attemptNode} when an attempt finishes and {@link #project} once the run has settled — so
 * every read of mutable occurrence state stays confined to the thread that owns that state.
 */
final class ResultProjector {

    private final String runId;
    private final Configuration configuration;
    private final Map<String, Integer> poolCapacities;
    private final boolean failureOnSkip;
    private final boolean failureOnAbort;
    private final Long discoveryShuffleSeed;

    /**
     * Creates a projector for one run.
     *
     * @param runId the stable run identifier; must not be {@code null}
     * @param configuration the effective configuration; must not be {@code null}
     * @param poolCapacities the resolved pool capacities; must not be {@code null}
     * @param discoveryShuffleSeed the discovery shuffle seed to report, or {@code null}
     * @throws NullPointerException if a required argument is {@code null}
     */
    ResultProjector(
            final String runId,
            final Configuration configuration,
            final Map<String, Integer> poolCapacities,
            final Long discoveryShuffleSeed) {
        this.runId = Objects.requireNonNull(runId, "runId is null");
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        // A LinkedHashMap copy keeps the plan's pool order, which the configuration summary reports.
        this.poolCapacities = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(poolCapacities, "poolCapacities is null")));
        this.failureOnSkip =
                configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(true);
        this.failureOnAbort =
                configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true);
        this.discoveryShuffleSeed = discoveryShuffleSeed;
    }

    /**
     * Builds the run result.
     *
     * @param root the root occurrence, or {@code null} when nothing was executed
     * @param quiescent whether the run settled completely
     * @param startedAt the run start instant; must not be {@code null}
     * @param failures the run-level failures; must not be {@code null}
     * @param diagnostics the run-level diagnostics; must not be {@code null}
     * @return the immutable result; never {@code null}
     */
    Result project(
            final OccurrenceState root,
            final boolean quiescent,
            final Instant startedAt,
            final List<Failure> failures,
            final List<String> diagnostics) {
        final var rootResult = root == null ? null : toResult(root);
        final var status = rootResult == null ? Status.skipped("nothing executed") : rootResult.status();
        final var counts = new Counts();
        if (rootResult != null) {
            counts.add(root);
        }
        return Result.builder()
                .runId(runId)
                .status(status, exitCodeFor(status, quiescent), quiescent)
                .timings(startedAt, Instant.now())
                .root(rootResult)
                .failures(failures)
                .diagnostics(diagnostics)
                .configurationSummary(configurationSummary())
                .shuffleSeed(discoveryShuffleSeed)
                .occurrenceCount(counts.occurrences)
                .attemptCount(counts.attempts)
                .passedCount(counts.passed)
                .failedCount(counts.failed)
                .skippedCount(counts.skipped)
                .abortedCount(counts.aborted)
                .canceledCount(counts.canceled)
                .timedOutCount(counts.timedOut)
                .build();
    }

    /**
     * Builds the result node of one finished attempt.
     *
     * @param state the occurrence; must not be {@code null}
     * @param attempt the finished attempt; must not be {@code null}
     * @param status the attempt's aggregate status; must not be {@code null}
     * @return the attempt node; never {@code null}
     */
    TaskResult attemptNode(final OccurrenceState state, final OccurrenceState.Attempt attempt, final Status status) {
        return TaskResult.builder()
                .kind(TaskResult.Kind.ATTEMPT)
                .attemptIndex(attempt.index)
                .identity(
                        state.id + "#" + attempt.index,
                        state.compiled.definitionId(),
                        state.parent == null ? null : state.parent.id,
                        state.name,
                        state.path)
                .parameters(state.compiled.task().parameters())
                .slot(slotOf(state))
                .tags(state.compiled.task().tags())
                .attributes(
                        state.compiled.mode(),
                        state.compiled.poolName(),
                        state.compiled.task().exclusiveLock().orElse(null),
                        state.compiled.shuffleSeed(),
                        state.compiled.executionOrder())
                .status(status, attempt.bodyStatus)
                .reason(
                        state.reason != null && state.attemptCount == attempt.index
                                ? state.reason
                                : reasonOf(status, attempt))
                .timedOut(state.timedOut)
                .phases(attempt.phases)
                .children(resultsOf(attempt))
                .failures(attempt.failures)
                .timings(
                        state.queuedAt,
                        state.admittedAt,
                        attempt.startedAt,
                        attempt.completedAt,
                        null,
                        attempt.startNanos == 0
                                ? null
                                : durationNanos(
                                        attempt.startNanos,
                                        endOfExecution(attempt.finalizationStartNanos, attempt.completedNanos)),
                        attempt.finalizationStartNanos == 0
                                ? null
                                : durationNanos(attempt.finalizationStartNanos, attempt.completedNanos))
                .build();
    }

    private TaskResult toResult(final OccurrenceState state) {
        final var attempt = state.attempt;
        final var builder = TaskResult.builder()
                .identity(
                        state.id,
                        state.compiled.definitionId(),
                        state.parent == null ? null : state.parent.id,
                        state.name,
                        state.path)
                .parameters(state.compiled.task().parameters())
                .slot(slotOf(state))
                .tags(state.compiled.task().tags())
                .attributes(
                        state.compiled.mode(),
                        state.compiled.poolName(),
                        state.compiled.task().exclusiveLock().orElse(null),
                        state.compiled.shuffleSeed(),
                        state.compiled.executionOrder())
                .status(state.status, attempt == null ? null : attempt.bodyStatus)
                .reason(finalReason(state))
                .timedOut(state.timedOut)
                .timings(
                        state.queuedAt,
                        state.admittedAt,
                        state.startedAt,
                        state.completedAt,
                        durationBetween(state.queuedAt, state.admittedAt),
                        state.admittedNanos == 0
                                ? null
                                : durationNanos(
                                        state.admittedNanos,
                                        endOfExecution(state.finalizationStartNanos, state.completedNanos)),
                        state.finalizationStartNanos == 0
                                ? null
                                : durationNanos(state.finalizationStartNanos, state.completedNanos));
        if (!state.attemptNodes.isEmpty()) {
            builder.children(state.attemptNodes);
        } else if (attempt != null) {
            builder.children(resultsOf(attempt));
        }
        if (attempt != null) {
            builder.phases(attempt.phases).failures(attempt.failures);
        }
        return builder.build();
    }

    /**
     * Returns the reason that belongs with an occurrence's final status.
     *
     * <p>An occurrence-level reason (timeout, cancellation, never admitted) wins. Otherwise, under a retry or repeat
     * policy, the final status is one attempt's status object, so the reason comes from that attempt and not from a
     * different one; if no recorded attempt has the final status, use the current attempt and final status.
     *
     * @param state the occurrence
     * @return the reason, or {@code null}
     */
    private static String finalReason(final OccurrenceState state) {
        if (state.reason != null) {
            return state.reason;
        }
        final var attempts = Math.min(state.attemptStatuses.size(), state.attemptNodes.size());
        for (var i = 0; i < attempts; i++) {
            if (state.attemptStatuses.get(i) == state.status) {
                return state.attemptNodes.get(i).reason().orElse(null);
            }
        }
        return reasonOf(state.status, state.attempt);
    }

    private static String reasonOf(final Status status, final OccurrenceState.Attempt attempt) {
        if (status.isCanceled()) {
            return status.message().orElse(null);
        }
        if (attempt != null && attempt.reason != null) {
            return attempt.reason;
        }
        return status.isSkipped() ? status.message().orElse(null) : null;
    }

    /**
     * Projects the children of one attempt in lifecycle order: the {@code before} subtree, the ordinary children, then
     * the {@code after} subtree.
     *
     * @param attempt the attempt; must not be {@code null}
     * @return the projected children; never {@code null}
     */
    private List<TaskResult> resultsOf(final OccurrenceState.Attempt attempt) {
        final var results = new ArrayList<TaskResult>(attempt.children.size() + 2);
        if (attempt.beforeChild != null) {
            results.add(toResult(attempt.beforeChild));
        }
        for (OccurrenceState child : attempt.children) {
            results.add(toResult(child));
        }
        if (attempt.afterChild != null) {
            results.add(toResult(attempt.afterChild));
        }
        return results;
    }

    private static TaskResult.Slot slotOf(final OccurrenceState state) {
        return switch (state.compiled.slot()) {
            case BEFORE -> TaskResult.Slot.BEFORE;
            case AFTER -> TaskResult.Slot.AFTER;
            case CHILD -> TaskResult.Slot.CHILD;
        };
    }

    private int exitCodeFor(final Status status, final boolean quiescent) {
        if (!quiescent) {
            return 1;
        }
        return switch (status.kind()) {
            case PASSED -> 0;
            case SKIPPED -> failureOnSkip ? 1 : 0;
            case ABORTED -> failureOnAbort ? 1 : 0;
            case FAILED, CANCELED -> 1;
            case PENDING, RUNNING -> 1;
        };
    }

    private Map<String, String> configurationSummary() {
        final var summary = new LinkedHashMap<String, String>();
        for (String key : configuration.keySet()) {
            if (!key.startsWith("paramixel.")) {
                continue;
            }
            configuration.getString(key).ifPresent(value -> summary.put(key, redact(key, value)));
        }
        final var poolSummary = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Integer> entry : poolCapacities.entrySet()) {
            poolSummary.put(entry.getKey(), Integer.toString(entry.getValue()));
        }
        if (!poolSummary.isEmpty()) {
            summary.put("pools", poolSummary.toString());
        }
        return summary;
    }

    private static String redact(final String key, final String value) {
        final var lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("secret")
                || lower.contains("token")
                || lower.contains("password")
                || lower.contains("credential")
                || lower.contains("apikey")
                || lower.contains("api-key")) {
            return "***";
        }
        return value;
    }

    private static Duration durationBetween(final Instant from, final Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return Duration.between(from, to);
    }

    /**
     * Returns where the execution span ends: at finalization start, or at completion when finalization never began.
     *
     * @param finalizationStartNanos the finalization start, or {@code 0}
     * @param completedNanos the completion, or {@code 0}
     * @return the end of the execution span, or {@code 0} when neither is known
     */
    private static long endOfExecution(final long finalizationStartNanos, final long completedNanos) {
        return finalizationStartNanos != 0 ? finalizationStartNanos : completedNanos;
    }

    private static Duration durationNanos(final long fromNanos, final long toNanosOrZero) {
        final var end = toNanosOrZero == 0 ? System.nanoTime() : toNanosOrZero;
        return Duration.ofNanos(Math.max(0, end - fromNanos));
    }

    /**
     * Counts logical occurrences once each.
     *
     * <p>Counters are computed from the runtime occurrence tree rather than the result tree, because the result tree
     * intentionally retains every historical attempt. A retried occurrence therefore contributes one occurrence and
     * one status (its final aggregate), plus one attempt per attempt node; the failed inner steps of an earlier attempt
     * stay visible as {@code ATTEMPT} nodes but are not counted as separate failed occurrences. Counting this way keeps
     * the status counters summing to the occurrence count and prevents a successful retry from inflating failure
     * metrics.
     */
    private static final class Counts {

        private int occurrences;
        private int attempts;
        private int passed;
        private int failed;
        private int skipped;
        private int aborted;
        private int canceled;
        private int timedOut;

        private void add(final OccurrenceState state) {
            occurrences++;
            switch (state.status.kind()) {
                case PASSED -> passed++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
                case ABORTED -> aborted++;
                case CANCELED -> canceled++;
                default -> {
                    // in-flight kinds do not occur in a finished result
                }
            }
            if (state.timedOut) {
                timedOut++;
            }
            attempts += state.attemptNodes.size();
            final var attempt = state.attempt;
            if (attempt != null) {
                if (attempt.beforeChild != null) {
                    add(attempt.beforeChild);
                }
                for (OccurrenceState child : attempt.children) {
                    add(child);
                }
                if (attempt.afterChild != null) {
                    add(attempt.afterChild);
                }
            }
        }
    }
}
