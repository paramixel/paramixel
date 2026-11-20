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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of one executed occurrence in a run's result tree.
 *
 * <p>Results describe what happened. They never reference a live {@code TaskExecution}, a builder, a fixture, or a
 * mutable scope, so a snapshot taken after a run cannot change when a later run executes the same definition.
 *
 * <p>Two facts are deliberately separate: {@link #status()} is the occurrence's aggregate outcome including its
 * descendants and finalizers, while {@link #bodyStatus()} is the outcome of the occurrence's own body operation. A
 * passed body therefore never conceals a failed child subtree.
 *
 * <p>A retried or repeated occurrence contains {@link Kind#ATTEMPT} children, one per attempt or iteration, with their
 * own statuses and timestamps. Historical failed attempts remain visible even when the final aggregate passed.
 */
public final class TaskResult {

    /**
     * The kind of result node.
     */
    public enum Kind {

        /** A task occurrence. */
        TASK,

        /** One attempt or iteration of a retried or repeated occurrence. */
        ATTEMPT
    }

    private final Kind kind;
    private final String occurrenceId;
    private final String definitionId;
    private final String parentOccurrenceId;
    private final String name;
    private final String path;
    private final List<String> tags;
    private final ExecutionMode mode;
    private final String poolName;
    private final String exclusiveLock;
    private final Long shuffleSeed;
    private final List<String> executionOrder;
    private final Status status;
    private final Status bodyStatus;
    private final List<PhaseResult> phases;
    private final List<TaskResult> children;
    private final List<Failure> failures;
    private final String reason;
    private final int attemptIndex;
    private final Instant queuedAt;
    private final Instant admittedAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration waitDuration;
    private final Duration executionDuration;
    private final Duration finalizationDuration;
    private final boolean timedOut;

    private TaskResult(final Builder builder) {
        this.kind = builder.kind;
        this.occurrenceId = builder.identity.occurrenceId();
        this.definitionId = builder.identity.definitionId();
        this.parentOccurrenceId = builder.identity.parentOccurrenceId();
        this.name = builder.identity.name();
        this.path = builder.identity.path();
        this.tags = List.copyOf(builder.tags);
        this.mode = builder.attributes.mode();
        this.poolName = builder.attributes.poolName();
        this.exclusiveLock = builder.attributes.exclusiveLock();
        this.shuffleSeed = builder.attributes.shuffleSeed();
        this.executionOrder = List.copyOf(builder.attributes.executionOrder());
        this.status = builder.status;
        this.bodyStatus = builder.bodyStatus;
        this.phases = List.copyOf(builder.phases);
        this.children = List.copyOf(builder.children);
        this.failures = List.copyOf(builder.failures);
        this.reason = builder.reason;
        this.attemptIndex = builder.attemptIndex;
        this.queuedAt = builder.timings.queuedAt();
        this.admittedAt = builder.timings.admittedAt();
        this.startedAt = builder.timings.startedAt();
        this.completedAt = builder.timings.completedAt();
        this.waitDuration = builder.timings.waitDuration();
        this.executionDuration = builder.timings.executionDuration();
        this.finalizationDuration = builder.timings.finalizationDuration();
        this.timedOut = builder.timedOut;
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
     * Returns the node kind.
     *
     * @return the kind; never {@code null}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the stable occurrence identifier.
     *
     * @return the identifier; never {@code null}
     */
    public String occurrenceId() {
        return occurrenceId;
    }

    /**
     * Returns the stable definition identifier of the underlying definition node.
     *
     * @return the definition identifier; never {@code null}
     */
    public String definitionId() {
        return definitionId;
    }

    /**
     * Returns the parent occurrence identifier.
     *
     * @return the parent identifier, or an empty optional for the root
     */
    public Optional<String> parentOccurrenceId() {
        return Optional.ofNullable(parentOccurrenceId);
    }

    /**
     * Returns the display name.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the display-name path from the run root.
     *
     * <p>Framework scaffolding added by factory discovery (the synthetic root, the hook-gating group, and the factory
     * group) is excluded, so a factory occurrence's path starts at the factory name instead of repeating a constant
     * framework prefix. Stable identity always comes from {@link #occurrenceId()}, not from the path.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }

    /**
     * Returns the declared tags.
     *
     * @return the immutable tags; never {@code null}
     */
    public List<String> tags() {
        return tags;
    }

    /**
     * Returns the child execution mode.
     *
     * @return the mode; never {@code null}
     */
    public ExecutionMode mode() {
        return mode;
    }

    /**
     * Returns the pool name of a parallel task.
     *
     * @return the pool name, or an empty optional
     */
    public Optional<String> poolName() {
        return Optional.ofNullable(poolName);
    }

    /**
     * Returns the exclusive lock name.
     *
     * @return the lock name, or an empty optional
     */
    public Optional<String> exclusiveLock() {
        return Optional.ofNullable(exclusiveLock);
    }

    /**
     * Returns the effective shuffle seed of this task's direct children.
     *
     * @return the seed, or an empty optional when the children were not shuffled
     */
    public Optional<Long> shuffleSeed() {
        return Optional.ofNullable(shuffleSeed);
    }

    /**
     * Returns the child names in effective execution order.
     *
     * @return the immutable ordered names; never {@code null}
     */
    public List<String> executionOrder() {
        return executionOrder;
    }

    /**
     * Returns the aggregate status.
     *
     * @return the status; never {@code null}
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the status of this occurrence's own body operation.
     *
     * @return the body status, or an empty optional when the task has no body or never ran it
     */
    public Optional<Status> bodyStatus() {
        return Optional.ofNullable(bodyStatus);
    }

    /**
     * Returns the phase results in phase order.
     *
     * @return the immutable phase results; never {@code null}
     */
    public List<PhaseResult> phases() {
        return phases;
    }

    /**
     * Returns the child results in structural order.
     *
     * <p>For a retried or repeated occurrence the children are the {@link Kind#ATTEMPT} nodes of that occurrence.
     *
     * @return the immutable children; never {@code null}
     */
    public List<TaskResult> children() {
        return children;
    }

    /**
     * Returns this occurrence's recorded failures in deterministic order.
     *
     * @return the immutable failures; never {@code null}
     */
    public List<Failure> failures() {
        return failures;
    }

    /**
     * Returns the explicit reason for a skip, cancellation, or timeout.
     *
     * @return the reason, or an empty optional
     */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /**
     * Returns the 1-based attempt or iteration index of an {@link Kind#ATTEMPT} node.
     *
     * @return the attempt index, or zero for a task node
     */
    public int attemptIndex() {
        return attemptIndex;
    }

    /**
     * Returns when the occurrence became ready.
     *
     * @return the instant, or an empty optional
     */
    public Optional<Instant> queuedAt() {
        return Optional.ofNullable(queuedAt);
    }

    /**
     * Returns when the occurrence was admitted.
     *
     * @return the instant, or an empty optional
     */
    public Optional<Instant> admittedAt() {
        return Optional.ofNullable(admittedAt);
    }

    /**
     * Returns when the occurrence started executing user work.
     *
     * @return the instant, or an empty optional
     */
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /**
     * Returns when the occurrence reached its terminal status.
     *
     * @return the instant, or an empty optional
     */
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    /**
     * Returns the admission wait.
     *
     * @return the wait, or an empty optional
     */
    public Optional<Duration> waitDuration() {
        return Optional.ofNullable(waitDuration);
    }

    /**
     * Returns the execution duration before finalization started.
     *
     * @return the duration, or an empty optional
     */
    public Optional<Duration> executionDuration() {
        return Optional.ofNullable(executionDuration);
    }

    /**
     * Returns the finalization duration of the {@code after} and resource-release phases.
     *
     * @return the duration, or an empty optional
     */
    public Optional<Duration> finalizationDuration() {
        return Optional.ofNullable(finalizationDuration);
    }

    /**
     * Returns whether a timeout expired for this occurrence.
     *
     * @return {@code true} when the timeout expired
     */
    public boolean timedOut() {
        return timedOut;
    }

    /**
     * Returns whether the aggregate status is {@code PASSED}.
     *
     * @return {@code true} when passed
     */
    public boolean isPassed() {
        return status.isPassed();
    }

    @Override
    public String toString() {
        return kind + "[" + occurrenceId + " " + path + " " + status + "]";
    }

    /** The identity values a {@link Builder} collects through {@link Builder#identity}. */
    private record Identity(
            String occurrenceId, String definitionId, String parentOccurrenceId, String name, String path) {}

    /** The scheduling attributes a {@link Builder} collects through {@link Builder#attributes}. */
    private record Attributes(
            ExecutionMode mode, String poolName, String exclusiveLock, Long shuffleSeed, List<String> executionOrder) {}

    /** The timing values a {@link Builder} collects through {@link Builder#timings}. */
    private record Timings(
            Instant queuedAt,
            Instant admittedAt,
            Instant startedAt,
            Instant completedAt,
            Duration waitDuration,
            Duration executionDuration,
            Duration finalizationDuration) {}

    /**
     * Builds immutable {@link TaskResult} instances.
     *
     * <p>A builder is mutable and single-thread-confined; {@link #build()} copies every collection, so later mutation
     * of the builder cannot alter a built snapshot.
     */
    public static final class Builder {

        private Kind kind = Kind.TASK;
        private Identity identity = new Identity(null, null, null, null, null);
        private List<String> tags = new ArrayList<>();
        private Attributes attributes = new Attributes(ExecutionMode.SEQUENTIAL, null, null, null, List.of());
        private Status status = Status.PENDING;
        private Status bodyStatus;
        private List<PhaseResult> phases = new ArrayList<>();
        private List<TaskResult> children = new ArrayList<>();
        private List<Failure> failures = new ArrayList<>();
        private String reason;
        private int attemptIndex;
        private Timings timings = new Timings(null, null, null, null, null, null, null);
        private boolean timedOut;

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the node kind.
         *
         * @param kind the kind; must not be {@code null}
         * @return this builder
         */
        public Builder kind(final Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind is null");
            return this;
        }

        /**
         * Sets identity fields.
         *
         * @param occurrenceId the occurrence identifier; must not be {@code null}
         * @param definitionId the definition identifier; must not be {@code null}
         * @param parentOccurrenceId the parent identifier, or {@code null}
         * @param name the display name; must not be {@code null}
         * @param path the display path; must not be {@code null}
         * @return this builder
         */
        public Builder identity(
                final String occurrenceId,
                final String definitionId,
                final String parentOccurrenceId,
                final String name,
                final String path) {
            this.identity = new Identity(occurrenceId, definitionId, parentOccurrenceId, name, path);
            return this;
        }

        /**
         * Sets the tags.
         *
         * @param values the tags; must not be {@code null}
         * @return this builder
         */
        public Builder tags(final List<String> values) {
            this.tags = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets the structural attributes.
         *
         * @param mode the child execution mode; must not be {@code null}
         * @param poolName the pool name, or {@code null}
         * @param exclusiveLock the lock name, or {@code null}
         * @param shuffleSeed the shuffle seed, or {@code null}
         * @param executionOrder the resolved child order; must not be {@code null}
         * @return this builder
         */
        public Builder attributes(
                final ExecutionMode mode,
                final String poolName,
                final String exclusiveLock,
                final Long shuffleSeed,
                final List<String> executionOrder) {
            this.attributes =
                    new Attributes(mode, poolName, exclusiveLock, shuffleSeed, new ArrayList<>(executionOrder));
            return this;
        }

        /**
         * Sets the statuses.
         *
         * @param status the aggregate status; must not be {@code null}
         * @param bodyStatus the body status, or {@code null}
         * @return this builder
         */
        public Builder status(final Status status, final Status bodyStatus) {
            this.status = status;
            this.bodyStatus = bodyStatus;
            return this;
        }

        /**
         * Sets the phase results.
         *
         * @param values the phase results; must not be {@code null}
         * @return this builder
         */
        public Builder phases(final List<PhaseResult> values) {
            this.phases = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets the children.
         *
         * @param values the child results; must not be {@code null}
         * @return this builder
         */
        public Builder children(final List<TaskResult> values) {
            this.children = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets the failures.
         *
         * @param values the failures; must not be {@code null}
         * @return this builder
         */
        public Builder failures(final List<Failure> values) {
            this.failures = new ArrayList<>(values);
            return this;
        }

        /**
         * Sets the reason.
         *
         * @param reason the reason, or {@code null}
         * @return this builder
         */
        public Builder reason(final String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets the attempt index.
         *
         * @param attemptIndex the 1-based attempt index, or zero for a task node
         * @return this builder
         */
        public Builder attemptIndex(final int attemptIndex) {
            this.attemptIndex = attemptIndex;
            return this;
        }

        /**
         * Sets the timing fields.
         *
         * @param queuedAt the ready instant, or {@code null}
         * @param admittedAt the admission instant, or {@code null}
         * @param startedAt the start instant, or {@code null}
         * @param completedAt the completion instant, or {@code null}
         * @param waitDuration the admission wait, or {@code null}
         * @param executionDuration the execution duration, or {@code null}
         * @param finalizationDuration the finalization duration, or {@code null}
         * @return this builder
         */
        public Builder timings(
                final Instant queuedAt,
                final Instant admittedAt,
                final Instant startedAt,
                final Instant completedAt,
                final Duration waitDuration,
                final Duration executionDuration,
                final Duration finalizationDuration) {
            this.timings = new Timings(
                    queuedAt,
                    admittedAt,
                    startedAt,
                    completedAt,
                    waitDuration,
                    executionDuration,
                    finalizationDuration);
            return this;
        }

        /**
         * Sets whether a timeout expired.
         *
         * @param timedOut whether the timeout expired
         * @return this builder
         */
        public Builder timedOut(final boolean timedOut) {
            this.timedOut = timedOut;
            return this;
        }

        /**
         * Builds the snapshot.
         *
         * @return the immutable result node; never {@code null}
         * @throws NullPointerException if a required field is missing
         */
        public TaskResult build() {
            Objects.requireNonNull(identity.occurrenceId(), "occurrenceId is null");
            Objects.requireNonNull(identity.definitionId(), "definitionId is null");
            Objects.requireNonNull(identity.name(), "name is null");
            Objects.requireNonNull(identity.path(), "path is null");
            Objects.requireNonNull(status, "status is null");
            Objects.requireNonNull(attributes.mode(), "mode is null");
            return new TaskResult(this);
        }
    }
}
