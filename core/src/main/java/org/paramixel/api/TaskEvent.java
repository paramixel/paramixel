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
import java.util.Objects;
import java.util.Optional;

/**
 * One lifecycle event of one occurrence, delivered to a {@link Listener}.
 *
 * <p>Events carry the run, occurrence, parent, attempt, phase, and a per-run monotonic sequence number. Events for the
 * same occurrence always arrive in legal phase order; events for unrelated parallel branches interleave and imply no
 * relative causality beyond their sequence numbers.
 *
 * <p>{@link #elapsed()} carries the occurrence duration for terminal and timeout events so a listener can render timing
 * without tracking admission timestamps itself. Other phases have no elapsed value.
 */
public final class TaskEvent {

    private final long sequence;
    private final String runId;
    private final String occurrenceId;
    private final String parentOccurrenceId;
    private final String name;
    private final String path;
    private final TaskPhase phase;
    private final int attempt;
    private final Status status;
    private final Instant timestamp;
    private final Duration elapsed;

    private TaskEvent(final Builder builder) {
        this.sequence = builder.sequence;
        this.runId = Objects.requireNonNull(builder.runId, "runId is null");
        this.occurrenceId = Objects.requireNonNull(builder.occurrenceId, "occurrenceId is null");
        this.parentOccurrenceId = builder.parentOccurrenceId;
        this.name = Objects.requireNonNull(builder.name, "name is null");
        this.path = Objects.requireNonNull(builder.path, "path is null");
        this.phase = Objects.requireNonNull(builder.phase, "phase is null");
        this.attempt = builder.attempt;
        this.status = Objects.requireNonNull(builder.status, "status is null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp is null");
        this.elapsed = builder.elapsed;
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
     * Returns the per-run monotonic sequence number.
     *
     * @return the sequence number
     */
    public long sequence() {
        return sequence;
    }

    /**
     * Returns the run identifier.
     *
     * @return the identifier; never {@code null}
     */
    public String runId() {
        return runId;
    }

    /**
     * Returns the occurrence identifier.
     *
     * @return the identifier; never {@code null}
     */
    public String occurrenceId() {
        return occurrenceId;
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
     * Returns the display path.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }

    /**
     * Returns the phase.
     *
     * @return the phase; never {@code null}
     */
    public TaskPhase phase() {
        return phase;
    }

    /**
     * Returns the 1-based attempt index.
     *
     * @return the attempt index; at least one
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Returns the status of the phase, or the terminal status for a {@link TaskPhase#TERMINAL} event.
     *
     * @return the status; never {@code null}
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the event instant.
     *
     * @return the instant; never {@code null}
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Returns the occurrence duration for a terminal or timeout event.
     *
     * @return the elapsed duration, or an empty optional for other phases
     */
    public Optional<Duration> elapsed() {
        return Optional.ofNullable(elapsed);
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + path + " " + phase + " " + status;
    }

    /**
     * Builds {@link TaskEvent} instances.
     *
     * <p>The builder is used by the framework and by integrations that replay stored events. It is mutable and
     * single-thread-confined.
     */
    public static final class Builder {

        private long sequence;
        private String runId;
        private String occurrenceId;
        private String parentOccurrenceId;
        private String name;
        private String path;
        private TaskPhase phase;
        private int attempt = 1;
        private Status status;
        private Instant timestamp;
        private Duration elapsed;

        private Builder() {
            // Intentionally empty
        }

        /**
         * Sets the per-run monotonic sequence number.
         *
         * @param sequence the sequence number
         * @return this builder
         */
        public Builder sequence(final long sequence) {
            this.sequence = sequence;
            return this;
        }

        /**
         * Sets the run identifier.
         *
         * @param runId the run identifier; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code runId} is {@code null}
         */
        public Builder runId(final String runId) {
            this.runId = Objects.requireNonNull(runId, "runId is null");
            return this;
        }

        /**
         * Sets the occurrence identity.
         *
         * @param occurrenceId the occurrence identifier; must not be {@code null}
         * @param parentOccurrenceId the parent identifier, or {@code null}
         * @param name the display name; must not be {@code null}
         * @param path the display path; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if a required argument is {@code null}
         */
        public Builder identity(
                final String occurrenceId, final String parentOccurrenceId, final String name, final String path) {
            this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId is null");
            this.parentOccurrenceId = parentOccurrenceId;
            this.name = Objects.requireNonNull(name, "name is null");
            this.path = Objects.requireNonNull(path, "path is null");
            return this;
        }

        /**
         * Sets the phase and its 1-based attempt index.
         *
         * @param phase the phase; must not be {@code null}
         * @param attempt the 1-based attempt index
         * @return this builder
         * @throws NullPointerException if {@code phase} is {@code null}
         */
        public Builder phase(final TaskPhase phase, final int attempt) {
            this.phase = Objects.requireNonNull(phase, "phase is null");
            this.attempt = attempt;
            return this;
        }

        /**
         * Sets the status of the phase or occurrence.
         *
         * @param status the status; must not be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code status} is {@code null}
         */
        public Builder status(final Status status) {
            this.status = Objects.requireNonNull(status, "status is null");
            return this;
        }

        /**
         * Sets the event timing.
         *
         * @param timestamp the event instant; must not be {@code null}
         * @param elapsed the occurrence duration for a terminal or timeout event, or {@code null}
         * @return this builder
         * @throws NullPointerException if {@code timestamp} is {@code null}
         */
        public Builder timing(final Instant timestamp, final Duration elapsed) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp is null");
            this.elapsed = elapsed;
            return this;
        }

        /**
         * Builds the immutable event.
         *
         * @return the event; never {@code null}
         * @throws NullPointerException if the run identifier, identity, phase, status, or timestamp is missing
         */
        public TaskEvent build() {
            return new TaskEvent(this);
        }
    }
}
