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

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one phase of an execution occurrence.
 *
 * <p>Phase results are retained separately so a report can show that a body passed while a finalizer failed, or that
 * a {@code before} callback stopped later callbacks.
 */
public final class PhaseResult {

    private final TaskPhase phase;
    private final String name;
    private final Status status;
    private final List<Failure> failures;

    private PhaseResult(final TaskPhase phase, final String name, final Status status, final List<Failure> failures) {
        this.phase = Objects.requireNonNull(phase, "phase is null");
        this.name = Objects.requireNonNull(name, "name is null");
        this.status = Objects.requireNonNull(status, "status is null");
        this.failures = List.copyOf(failures);
    }

    /**
     * Creates a phase result.
     *
     * @param phase the phase; must not be {@code null}
     * @param name the phase instance name, for example the callback name; must not be {@code null}
     * @param status the status; must not be {@code null}
     * @param failures the failures recorded for the phase; must not be {@code null} and must not contain
     *     {@code null}
     * @return the phase result; never {@code null}
     * @throws NullPointerException if any argument or element is {@code null}
     */
    public static PhaseResult of(
            final TaskPhase phase, final String name, final Status status, final List<Failure> failures) {
        return new PhaseResult(phase, name, status, failures);
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
     * Returns the phase instance name.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the status.
     *
     * @return the status; never {@code null}
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the failures of this phase.
     *
     * @return the immutable, ordered failures; never {@code null}
     */
    public List<Failure> failures() {
        return failures;
    }

    @Override
    public String toString() {
        return phase + " " + name + " " + status;
    }
}
