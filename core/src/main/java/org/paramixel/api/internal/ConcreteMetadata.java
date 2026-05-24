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

package org.paramixel.api.internal;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Status;
import org.paramixel.api.action.Metadata;
import org.paramixel.spi.action.Mode;

/**
 * Mutable implementation of {@link Metadata} carrying execution-occurrence identity and state.
 *
 * <p>Identity fields ({@code id}, {@code name}, {@code kind}, {@code className}) are immutable
 * after construction. State fields ({@code status}, {@code mode}, {@code runDuration},
 * {@code startNanos}) are mutable and protected by {@code synchronized} access.
 *
 * <p><strong>Thread safety:</strong> All getters and setters for mutable state are
 * {@code synchronized}. Concurrent reads and writes are safe. Identity fields are
 * effectively immutable and require no synchronization.
 */
public final class ConcreteMetadata implements Metadata {

    private final String id;
    private final String name;
    private final String kind;
    private final String className;
    private Mode mode = Mode.RUN;
    private Status status = Status.PENDING;
    private Duration runDuration = Duration.ZERO;
    private long startNanos;

    /**
     * Creates metadata from the supplied identity fields.
     *
     * @param id the generated execution-occurrence identifier; must not be {@code null}
     * @param name the human-readable action name; must not be {@code null}
     * @param className the runtime action class name; must not be {@code null}
     * @param kind the action kind; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public ConcreteMetadata(final String id, final String name, final String className, final String kind) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.className = Objects.requireNonNull(className, "className must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public synchronized Status status() {
        return status;
    }

    @Override
    public synchronized Mode mode() {
        return mode;
    }

    @Override
    public synchronized Duration runDuration() {
        return runDuration;
    }

    @Override
    public synchronized Optional<String> message() {
        return status.message();
    }

    @Override
    public synchronized Optional<Throwable> throwable() {
        return status.throwable();
    }

    @Override
    public synchronized boolean isCompleted() {
        return status.isTerminal();
    }

    /**
     * Sets the execution status, validating the state transition.
     *
     * <p>Valid transitions:
     * <ul>
     *   <li>{@code PENDING} → {@code RUNNING}</li>
     *   <li>{@code RUNNING} → any terminal status</li>
     * </ul>
     *
     * <p>Setting {@link Status#PENDING} is always invalid. Transitioning from a terminal
     * status is always invalid.
     *
     * @param newStatus the new status; must not be {@code null}
     * @throws NullPointerException if {@code newStatus} is {@code null}
     * @throws IllegalArgumentException if {@code newStatus} is {@link Status#PENDING}
     * @throws IllegalStateException if the transition is invalid
     */
    public synchronized void setStatus(final Status newStatus) {
        Objects.requireNonNull(newStatus, "status must not be null");
        if (newStatus.isPending()) {
            throw new IllegalArgumentException("Cannot set PENDING status");
        }
        if (status.isPending()) {
            if (!newStatus.isRunning()) {
                throw new IllegalStateException("Metadata must transition from PENDING to RUNNING");
            }
            status = Status.RUNNING;
            startNanos = System.nanoTime();
            runDuration = Duration.ZERO;
            return;
        }
        if (status.isRunning()) {
            if (!newStatus.isTerminal()) {
                throw new IllegalStateException("Metadata must transition from RUNNING to terminal status");
            }
            status = newStatus;
            runDuration = Duration.ofNanos(System.nanoTime() - startNanos);
            return;
        }
        throw new IllegalStateException("Metadata already completed with status " + status.name());
    }

    /**
     * Sets the execution mode.
     *
     * @param mode the execution mode; must not be {@code null}
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    public synchronized void setMode(final Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * Marks this metadata as running by setting status to {@link Status#RUNNING}
     * and recording the start time.
     *
     * <p>Equivalent to calling {@code setStatus(Status.RUNNING)} but provided as
     * a convenience for the common PENDING → RUNNING transition.
     */
    synchronized void markRunning() {
        setStatus(Status.RUNNING);
    }
}
