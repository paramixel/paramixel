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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.paramixel.api.support.RepeatPolicy;
import org.paramixel.api.support.RetryPolicy;

/**
 * The immutable state shared by every {@link Task} node.
 *
 * <p>This type is the implementation seam between {@link TaskBuilder} and the three built node types. It is package
 * private on purpose: authors see {@link Task} and its accessors, never the storage shape, so the accessors can be
 * widened without changing the authoring surface.
 */
final class TaskState {

    /**
     * The maximum number of characters of a rendered plan parameter; longer renderings are truncated.
     */
    static final int MAX_PARAMETER_LENGTH = 256;

    /**
     * The character appended to a truncated plan parameter.
     */
    static final String TRUNCATION_MARKER = "\u2026";

    private final String name;
    private final ExecutionMode mode;
    private final Pool pool;
    private final boolean dependent;
    private final List<Task> children;
    private final Class<?> anchor;
    private final Map<String, Object> bindings;
    private final Map<String, String> parameters;
    private final PhaseHook before;
    private final PhaseHook after;
    private final TaskCallback body;
    private final List<String> tags;
    private final Long shuffleSeed;
    private final Predicate<Context> condition;
    private final Duration timeout;
    private final RetryPolicy retry;
    private final RepeatPolicy repeat;
    private final String exclusiveLock;

    TaskState(
            final Structure structure,
            final Lifecycle lifecycle,
            final Policies policies,
            final Map<String, String> parameters) {
        this.name = structure.name();
        this.mode = structure.mode();
        this.pool = structure.pool();
        this.dependent = structure.dependent();
        this.children = List.copyOf(structure.children());
        this.anchor = structure.anchor();
        this.bindings = ordered(structure.bindings());
        this.parameters = ordered(parameters);
        this.before = lifecycle.before();
        this.after = lifecycle.after();
        this.body = lifecycle.body();
        this.tags = List.copyOf(policies.tags());
        this.shuffleSeed = policies.shuffleSeed();
        this.condition = policies.condition();
        this.timeout = policies.timeout();
        this.retry = policies.retry();
        this.repeat = policies.repeat();
        this.exclusiveLock = policies.exclusiveLock();
    }

    /**
     * The structural part of a definition.
     *
     * @param name the display name
     * @param mode the child execution mode
     * @param pool the pool of a parallel task, or {@code null}
     * @param dependent whether a non-passing child stops later siblings
     * @param children the ordered children
     * @param anchor the anchor class the definition resolved its ids against, or {@code null}
     * @param bindings the values seeded into this task's scope
     */
    record Structure(
            String name,
            ExecutionMode mode,
            Pool pool,
            boolean dependent,
            List<Task> children,
            Class<?> anchor,
            Map<String, Object> bindings) {}

    /**
     * The lifecycle part of a definition.
     *
     * @param before the before slot, or {@code null}
     * @param after the after slot, or {@code null}
     * @param body the body, or {@code null}
     */
    record Lifecycle(PhaseHook before, PhaseHook after, TaskCallback body) {}

    /**
     * The execution policies of a definition.
     *
     * @param tags the tags
     * @param shuffleSeed the effective shuffle seed, or {@code null}
     * @param condition the condition, or {@code null}
     * @param timeout the timeout, or {@code null}
     * @param retry the retry policy, or {@code null}
     * @param repeat the repeat policy, or {@code null}
     * @param exclusiveLock the exclusive lock name, or {@code null}
     */
    record Policies(
            List<String> tags,
            Long shuffleSeed,
            Predicate<Context> condition,
            Duration timeout,
            RetryPolicy retry,
            RepeatPolicy repeat,
            String exclusiveLock) {}

    /**
     * Renders one bound value as a report parameter.
     *
     * <p>The rendering happens once, when the definition is built, so a report describes the plan that actually ran.
     * A rendering longer than {@value #MAX_PARAMETER_LENGTH} characters is truncated and marked with {@code …}.
     *
     * @param value the bound value; must not be {@code null}
     * @return the rendered parameter; never {@code null}
     */
    static String renderParameter(final Object value) {
        final var text = String.valueOf(value);
        if (text.length() <= MAX_PARAMETER_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PARAMETER_LENGTH) + TRUNCATION_MARKER;
    }

    private static <V> Map<String, V> ordered(final Map<String, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values is null")));
    }

    String name() {
        return name;
    }

    ExecutionMode mode() {
        return mode;
    }

    Pool pool() {
        return pool;
    }

    boolean dependent() {
        return dependent;
    }

    List<Task> children() {
        return children;
    }

    Class<?> anchor() {
        return anchor;
    }

    Map<String, Object> bindings() {
        return bindings;
    }

    Map<String, String> parameters() {
        return parameters;
    }

    PhaseHook before() {
        return before;
    }

    PhaseHook after() {
        return after;
    }

    TaskCallback body() {
        return body;
    }

    List<String> tags() {
        return tags;
    }

    Long shuffleSeed() {
        return shuffleSeed;
    }

    Predicate<Context> condition() {
        return condition;
    }

    Duration timeout() {
        return timeout;
    }

    RetryPolicy retry() {
        return retry;
    }

    RepeatPolicy repeat() {
        return repeat;
    }

    String exclusiveLock() {
        return exclusiveLock;
    }
}
