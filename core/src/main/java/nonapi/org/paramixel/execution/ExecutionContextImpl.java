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
import org.paramixel.api.exception.ContextException;

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
    private final OccurrenceState.Attempt writerAttempt;
    private final OccurrenceState.Attempt scope;

    ExecutionContextImpl(final ExecutionEngine engine, final OccurrenceState state, final int attempt) {
        this.engine = Objects.requireNonNull(engine, "engine is null");
        this.state = Objects.requireNonNull(state, "state is null");
        this.attempt = attempt;
        this.writerAttempt = state.attempt;
        this.scope = state.scopeAttempt();
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
        return state.compiled.tags();
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
    public void put(final String name, final Object value) {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(value, "value is null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Context entry name is blank");
        }
        final var target = writableScope();
        if (target != null) {
            target.context.put(name, value);
        }
    }

    @Override
    public void remove(final String name) {
        Objects.requireNonNull(name, "name is null");
        final var target = writableScope();
        if (target != null) {
            target.context.remove(name);
        }
    }

    @Override
    public <T> T require(final String name, final Class<T> type) {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        final var value = state.findContextEntry(name);
        if (value == null) {
            throw new ContextException(name, state.path, "No context entry named \"" + name + "\" at " + state.path);
        }
        if (!type.isInstance(value)) {
            throw new ContextException(
                    name,
                    state.path,
                    "Context entry \"" + name + "\" at " + state.path + " holds "
                            + value.getClass().getName() + " but " + type.getName() + " was requested");
        }
        return type.cast(value);
    }

    @Override
    public <T> Optional<T> find(final String name, final Class<T> type) {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(type, "type is null");
        final var value = state.findContextEntry(name);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * Returns the attempt this context may write to, or {@code null} when the write must be discarded.
     *
     * <p>A write is discarded when the writer's own attempt has been superseded (a timeout, a cancellation, or a hook
     * that ignored interruption), when the scope owner has moved on to another attempt, or when the scope was already
     * closed and cleared. Without this, an abandoned callback could contaminate the next attempt.
     *
     * @return the writable attempt, or {@code null}
     */
    private OccurrenceState.Attempt writableScope() {
        if (scope == null || scope.contextClosed) {
            return null;
        }
        if (state.attempt != writerAttempt) {
            return null;
        }
        if (state.scopeAttempt() != scope) {
            return null;
        }
        return scope;
    }
}
