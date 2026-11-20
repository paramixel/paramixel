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

package org.paramixel.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The immutable outcome of one Paramixel run.
 *
 * <p>{@link #status()} is the semantic aggregate status of the run and is never rewritten to reflect exit-code policy.
 * {@link #exitCode()} applies the configured mapping ({@code failureOnSkip}, {@code failureOnAbort},
 * {@code failIfNoTests}) and is the single authority for a process exit code.
 *
 * <p>{@link #isQuiescent()} is the honesty flag for timeouts. It is {@code true} for a normally settled run, where no
 * framework worker, permit, or finalizer remains active. It is {@code false} when a callback ignored cooperative
 * cancellation past the shutdown grace period: user work may still be running, finalizers were deliberately withheld
 * to avoid touching resources that the stuck callback is still mutating, and permits may still be held. A non-quiescent
 * run never reports clean finalization, and its exit code is non-zero.
 */
public final class Result {

    private final String runId;
    private final Status status;
    private final int exitCode;
    private final boolean quiescent;
    private final Instant startedAt;
    private final Instant completedAt;
    private final TaskResult root;
    private final List<Failure> failures;
    private final List<String> diagnostics;
    private final Map<String, String> configurationSummary;
    private final Long shuffleSeed;
    private final int occurrenceCount;
    private final int attemptCount;
    private final int passedCount;
    private final int failedCount;
    private final int skippedCount;
    private final int abortedCount;
    private final int canceledCount;
    private final int timedOutCount;

    private Result(final Builder builder) {
        this.runId = builder.runId;
        this.status = builder.status;
        this.exitCode = builder.exitCode;
        this.quiescent = builder.quiescent;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.root = builder.root;
        this.failures = List.copyOf(builder.failures);
        this.diagnostics = List.copyOf(builder.diagnostics);
        this.configurationSummary = Collections.unmodifiableMap(new TreeMap<>(builder.configurationSummary));
        this.shuffleSeed = builder.shuffleSeed;
        this.occurrenceCount = builder.counters.occurrenceCount;
        this.attemptCount = builder.counters.attemptCount;
        this.passedCount = builder.counters.passedCount;
        this.failedCount = builder.counters.failedCount;
        this.skippedCount = builder.counters.skippedCount;
        this.abortedCount = builder.counters.abortedCount;
        this.canceledCount = builder.counters.canceledCount;
        this.timedOutCount = builder.counters.timedOutCount;
    }

    /**
     * Returns a new builder.
     *
     * @return a builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the stable run identifier.
     *
     * @return the identifier; never {@code null}
     */
    public String runId() {
        return runId;
    }

    /**
     * Returns the semantic aggregate status.
     *
     * @return the status; never {@code null}
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the process exit code implied by this result and the effective configuration.
     *
     * @return zero for a clean pass, non-zero otherwise
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * Returns whether the run settled completely.
     *
     * @return {@code true} when no framework worker, permit, finalizer, or callback remains active
     */
    public boolean isQuiescent() {
        return quiescent;
    }

    /**
     * Returns when the run started.
     *
     * @return the start instant, or an empty optional
     */
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /**
     * Returns when the run completed.
     *
     * @return the completion instant, or an empty optional
     */
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    /**
     * Returns the root result node.
     *
     * @return the root, or an empty optional when nothing was executed
     */
    public Optional<TaskResult> root() {
        return Optional.ofNullable(root);
    }

    /**
     * Returns run-level failures: infrastructure defects and listener failures that are not attributable to one test.
     *
     * @return the immutable failures; never {@code null}
     */
    public List<Failure> failures() {
        return failures;
    }

    /**
     * Returns run-level diagnostics, including warnings and infrastructure notes, in delivery order.
     *
     * @return the immutable diagnostics; never {@code null}
     */
    public List<String> diagnostics() {
        return diagnostics;
    }

    /**
     * Returns a redacted summary of the effective configuration.
     *
     * @return the immutable summary; never {@code null}
     */
    public Map<String, String> configurationSummary() {
        return configurationSummary;
    }

    /**
     * Returns the discovery shuffle seed, when discovery ordering was shuffled.
     *
     * @return the seed, or an empty optional
     */
    public Optional<Long> shuffleSeed() {
        return Optional.ofNullable(shuffleSeed);
    }

    /**
     * Returns the number of executed task occurrences, excluding attempt nodes.
     *
     * @return the occurrence count
     */
    public int occurrenceCount() {
        return occurrenceCount;
    }

    /**
     * Returns the number of attempt or iteration nodes.
     *
     * @return the attempt count
     */
    public int attemptCount() {
        return attemptCount;
    }

    /**
     * Returns the number of passed task occurrences.
     *
     * <p>Counters include container occurrences: a task whose children all pass is itself passed. The status counters
     * therefore sum to {@link #occurrenceCount()} for a terminal run. Counters count each logical occurrence once, so a
     * successful retry contributes one passed occurrence and {@link #attemptCount()} increments, while the failed inner
     * steps of an earlier attempt remain visible only as {@code ATTEMPT} nodes in the result tree.
     *
     * @return the passed count
     */
    public int passedCount() {
        return passedCount;
    }

    /**
     * Returns the number of failed task occurrences.
     *
     * @return the failed count
     */
    public int failedCount() {
        return failedCount;
    }

    /**
     * Returns the number of skipped task occurrences.
     *
     * @return the skipped count
     */
    public int skippedCount() {
        return skippedCount;
    }

    /**
     * Returns the number of aborted task occurrences.
     *
     * @return the aborted count
     */
    public int abortedCount() {
        return abortedCount;
    }

    /**
     * Returns the number of canceled task occurrences.
     *
     * @return the canceled count
     */
    public int canceledCount() {
        return canceledCount;
    }

    /**
     * Returns the number of task occurrences whose timeout expired.
     *
     * @return the timeout count
     */
    public int timedOutCount() {
        return timedOutCount;
    }

    /**
     * Returns a copy of this result with additional run-level diagnostics appended.
     *
     * <p>Used after the run completed, when a late diagnostic (a listener failure, for example) must be attached to an
     * already delivered result. The returned result is a new immutable instance; the original is unchanged.
     *
     * @param additional the diagnostics to append; must not be {@code null} and must not contain {@code null}
     * @return the new result; never {@code null}
     * @throws NullPointerException if any argument or element is {@code null}
     */
    public Result withAdditionalDiagnostics(final List<String> additional) {
        Objects.requireNonNull(additional, "additional is null");
        if (additional.isEmpty()) {
            return this;
        }
        final var merged = new ArrayList<>(diagnostics);
        additional.forEach(value -> merged.add(Objects.requireNonNull(value, "diagnostic is null")));
        final var builder = builder()
                .runId(runId)
                .status(status, exitCode, quiescent)
                .timings(startedAt, completedAt)
                .root(root)
                .failures(failures)
                .diagnostics(merged)
                .configurationSummary(configurationSummary)
                .shuffleSeed(shuffleSeed)
                .occurrenceCount(occurrenceCount)
                .attemptCount(attemptCount)
                .passedCount(passedCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .abortedCount(abortedCount)
                .canceledCount(canceledCount)
                .timedOutCount(timedOutCount);
        return builder.build();
    }

    @Override
    public String toString() {
        return "Result[" + runId + " " + status + " exit=" + exitCode + " quiescent=" + quiescent + "]";
    }

    /**
     * The mutable occurrence and attempt counters a {@link Builder} collects before building.
     */
    private static final class Counters {

        private int occurrenceCount;
        private int attemptCount;
        private int passedCount;
        private int failedCount;
        private int skippedCount;
        private int abortedCount;
        private int canceledCount;
        private int timedOutCount;
    }

    /**
     * Builds {@link Result} instances.
     *
     * <p>The builder is used by the framework and by reporting integrations that reconstruct a result from a stored
     * tree. It is mutable and single-thread-confined.
     */
    public static final class Builder {

        private String runId;
        private Status status = Status.PENDING;
        private int exitCode;
        private boolean quiescent = true;
        private Instant startedAt;
        private Instant completedAt;
        private TaskResult root;
        private List<Failure> failures = new ArrayList<>();
        private List<String> diagnostics = new ArrayList<>();
        private Map<String, String> configurationSummary = new TreeMap<>();
        private Long shuffleSeed;
        private final Counters counters = new Counters();

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the run identifier.
         *
         * @param runId the identifier; must not be {@code null}
         * @return this builder
         */
        public Builder runId(final String runId) {
            this.runId = Objects.requireNonNull(runId, "runId is null");
            return this;
        }

        /**
         * Sets the statuses.
         *
         * @param status the semantic aggregate status; must not be {@code null}
         * @param exitCode the process exit code
         * @param quiescent whether the run settled completely
         * @return this builder
         */
        public Builder status(final Status status, final int exitCode, final boolean quiescent) {
            this.status = Objects.requireNonNull(status, "status is null");
            this.exitCode = exitCode;
            this.quiescent = quiescent;
            return this;
        }

        /**
         * Sets the run timings.
         *
         * @param startedAt the start instant, or {@code null}
         * @param completedAt the completion instant, or {@code null}
         * @return this builder
         */
        public Builder timings(final Instant startedAt, final Instant completedAt) {
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            return this;
        }

        /**
         * Sets the root result node.
         *
         * @param root the root, or {@code null} when nothing was executed
         * @return this builder
         */
        public Builder root(final TaskResult root) {
            this.root = root;
            return this;
        }

        /**
         * Sets run-level failures.
         *
         * @param values the failures; must not be {@code null}
         * @return this builder
         */
        public Builder failures(final List<Failure> values) {
            this.failures = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets run-level diagnostics.
         *
         * @param values the diagnostics; must not be {@code null}
         * @return this builder
         */
        public Builder diagnostics(final List<String> values) {
            this.diagnostics = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets the redacted configuration summary.
         *
         * @param values the summary; must not be {@code null}
         * @return this builder
         */
        public Builder configurationSummary(final Map<String, String> values) {
            this.configurationSummary = new TreeMap<>(values);
            return this;
        }

        /**
         * Sets the discovery shuffle seed.
         *
         * @param shuffleSeed the seed, or {@code null}
         * @return this builder
         */
        public Builder shuffleSeed(final Long shuffleSeed) {
            this.shuffleSeed = shuffleSeed;
            return this;
        }

        /**
         * Sets the number of task occurrences, excluding attempt nodes.
         *
         * @param value the occurrence count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder occurrenceCount(final int value) {
            counters.occurrenceCount = requireCount("occurrenceCount", value);
            return this;
        }

        /**
         * Sets the number of attempt or iteration nodes.
         *
         * @param value the attempt count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder attemptCount(final int value) {
            counters.attemptCount = requireCount("attemptCount", value);
            return this;
        }

        /**
         * Sets the number of passed task occurrences.
         *
         * @param value the passed count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder passedCount(final int value) {
            counters.passedCount = requireCount("passedCount", value);
            return this;
        }

        /**
         * Sets the number of failed task occurrences.
         *
         * @param value the failed count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder failedCount(final int value) {
            counters.failedCount = requireCount("failedCount", value);
            return this;
        }

        /**
         * Sets the number of skipped task occurrences.
         *
         * @param value the skipped count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder skippedCount(final int value) {
            counters.skippedCount = requireCount("skippedCount", value);
            return this;
        }

        /**
         * Sets the number of aborted task occurrences.
         *
         * @param value the aborted count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder abortedCount(final int value) {
            counters.abortedCount = requireCount("abortedCount", value);
            return this;
        }

        /**
         * Sets the number of canceled task occurrences.
         *
         * @param value the canceled count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder canceledCount(final int value) {
            counters.canceledCount = requireCount("canceledCount", value);
            return this;
        }

        /**
         * Sets the number of task occurrences whose timeout expired.
         *
         * @param value the timeout count; must not be negative
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder timedOutCount(final int value) {
            counters.timedOutCount = requireCount("timedOutCount", value);
            return this;
        }

        /**
         * Builds the immutable result.
         *
         * <p>The counters must be mutually consistent: the status counts (passed, failed, skipped, aborted, canceled)
         * together, and the timeout count on its own, cannot exceed {@link Result#occurrenceCount()}. The status counts
         * may sum to less than the occurrence count only when some occurrence never reached a terminal status.
         *
         * @return the result; never {@code null}
         * @throws NullPointerException if the run identifier is missing
         * @throws IllegalArgumentException if the counters are inconsistent
         */
        public Result build() {
            Objects.requireNonNull(runId, "runId is null");
            final var statusTotal = (long) counters.passedCount
                    + counters.failedCount
                    + counters.skippedCount
                    + counters.abortedCount
                    + counters.canceledCount;
            if (statusTotal > counters.occurrenceCount) {
                throw new IllegalArgumentException("Status counts sum to " + statusTotal
                        + ", which exceeds occurrenceCount " + counters.occurrenceCount);
            }
            if (counters.timedOutCount > counters.occurrenceCount) {
                throw new IllegalArgumentException("timedOutCount " + counters.timedOutCount
                        + " exceeds occurrenceCount " + counters.occurrenceCount);
            }
            return new Result(this);
        }

        private static int requireCount(final String name, final int value) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must not be negative: " + value);
            }
            return value;
        }
    }
}
