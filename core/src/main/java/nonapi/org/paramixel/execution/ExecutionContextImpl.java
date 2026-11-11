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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Key;
import org.paramixel.api.exception.ResourceException;

/**
 * The read-only {@link Context} view handed to user callbacks.
 *
 * <p>A context is bound to one occurrence and one attempt. It exposes identity, cancellation state, the nearest lexical
 * primary instance, and keyed resources; it never exposes a mutable execution handle.
 */
final class ExecutionContextImpl implements Context {

    private final ExecutionEngine engine;
    private final OccurrenceState state;
    private final int attempt;

    ExecutionContextImpl(final ExecutionEngine engine, final OccurrenceState state, final int attempt) {
        this.engine = Objects.requireNonNull(engine, "engine is null");
        this.state = Objects.requireNonNull(state, "state is null");
        this.attempt = attempt;
    }

    @Override
    public Configuration configuration() {
        return engine.configuration();
    }

    @Override
    public String runId() {
        return engine.runId();
    }

    @Override
    public String occurrenceId() {
        return state.id;
    }

    @Override
    public Optional<String> parentOccurrenceId() {
        return Optional.ofNullable(state.parent == null ? null : state.parent.id);
    }

    @Override
    public String name() {
        return state.name;
    }

    @Override
    public String path() {
        return state.path;
    }

    @Override
    public int attempt() {
        return attempt;
    }

    @Override
    public List<String> tags() {
        return state.compiled.task().tags();
    }

    @Override
    public boolean isCanceled() {
        return state.isCanceled();
    }

    @Override
    public Optional<String> cancellationReason() {
        return Optional.ofNullable(state.cancellationReason());
    }

    @Override
    public Object primaryInstance() {
        final var instance = state.nearestInstance();
        if (instance == null) {
            throw new IllegalStateException("No primary instance is bound for occurrence " + state.path
                    + "; wrap the task in forInstance(name, factory)");
        }
        return instance;
    }

    @Override
    public <T> T require(final Key<T> key) {
        Objects.requireNonNull(key, "key is null");
        final var value = state.findResource(key);
        if (value == null) {
            throw new ResourceException(key, state.path, "No resource is bound for key " + key + " at " + state.path);
        }
        return key.type().cast(value);
    }

    @Override
    public <T> Optional<T> find(final Key<T> key) {
        Objects.requireNonNull(key, "key is null");
        final var value = state.findResource(key);
        return value == null ? Optional.empty() : Optional.of(key.type().cast(value));
    }
}
