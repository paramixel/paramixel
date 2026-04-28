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

package org.paramixel.core.action;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.SkipException;

/**
 * Base implementation for all concrete actions.
 *
 * <p>Provides default implementations for {@link Action} interface methods
 * and defines the template method for execution.
 */
public abstract class AbstractAction implements Action {

    private final String id;
    private final String name;

    /**
     * Parent action when this action has been adopted.
     *
     * <p>This field uses two-phase construction: it is {@code null} after the
     * constructor completes, and is set by {@link #adopt(Action)} when the
     * action is added as a child to a parent action. Adoption happens
     * synchronously in the parent's constructor, so the mutation completes
     * before the action tree is observable.
     *
     * @see #adopt(Action)
     */
    private Action parent;

    /**
     * Creates an action with the supplied display name.
     *
     * @param name The action name; must not be null or blank.
     * @throws NullPointerException If {@code name} is null.
     * @throws IllegalArgumentException If {@code name} is blank.
     */
    protected AbstractAction(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    /**
     * Returns this action's unique identifier.
     *
     * @return The generated action identifier.
     */
    @Override
    public final String id() {
        return id;
    }

    /**
     * Returns this action's display name.
     *
     * @return The non-blank action name.
     */
    @Override
    public final String name() {
        return name;
    }

    /**
     * Returns this action's parent when it has been adopted by another action.
     *
     * @return An {@link Optional} containing the parent action, or empty when
     *     this action is a root.
     */
    @Override
    public final Optional<Action> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Executes this action in the supplied execution context.
     *
     * <p>This template method handles before/after callbacks, timing,
     * and exception handling.
     *
     * @param context The active execution context; must not be null.
     * @return The execution result.
     */
    @Override
    public final Result execute(Context context) {
        context.beforeAction(context, this);
        Instant start = Instant.now();
        Result result;
        try {
            result = doExecute(context, start);
        } catch (SkipException e) {
            result = Result.skip(this, durationSince(start), e);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            result = Result.fail(this, durationSince(start), t);
        }
        context.afterAction(context, this, result);
        return result;
    }

    /**
     * Skips this action without executing it.
     *
     * @param context The active execution context; must not be null.
     * @return A skipped result with zero timing.
     */
    @Override
    public final Result skip(Context context) {
        context.beforeAction(context, this);
        Result result = Result.skip(this, Duration.ZERO);
        context.afterAction(context, this, result);
        return result;
    }

    /**
     * Executes this action in the supplied execution context.
     *
     * @param context The active execution context; must not be null.
     * @param start The instant when execution began; must not be null.
     * @return The execution result.
     * @throws Throwable If execution fails for any reason.
     */
    protected abstract Result doExecute(Context context, Instant start) throws Throwable;

    /**
     * Assigns this action as the parent of a child action.
     *
     * <p>This method is intended to be called only from within the
     * constructor of a composite action ({@link Sequential}, {@link Parallel},
     * {@link org.paramixel.core.action.Lifecycle Lifecycle}) to establish
     * the parent-child relationship during two-phase construction.
     *
     * <p>The method guards against re-parenting and throws
     * {@link IllegalStateException} if the child already belongs to a
     * different parent.
     *
     * @param child The child action to adopt; must not be null.
     * @throws NullPointerException If {@code child} is null.
     * @throws IllegalStateException If {@code child} already belongs to another
     *     parent or is not an {@link AbstractAction}.
     */
    protected final void adopt(Action child) {
        Objects.requireNonNull(child, "child must not be null");
        if (!(child instanceof AbstractAction)) {
            throw new IllegalStateException("child must be an AbstractAction");
        }
        AbstractAction abstractChild = (AbstractAction) child;
        if (abstractChild.parent != null && abstractChild.parent != this) {
            throw new IllegalStateException("action '" + abstractChild.name + "' already has a parent");
        }
        abstractChild.parent = this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + name + "]";
    }

    /**
     * Computes the duration since a start instant.
     *
     * @param start The start instant; must not be null.
     * @return The elapsed duration.
     */
    protected static Duration durationSince(Instant start) {
        return Duration.between(start, Instant.now());
    }

    /**
     * Computes the status of a list of child results.
     *
     * @param results The child results; must not be null.
     * @return FAIL if any child fails, SKIP if all are skipped, otherwise PASS.
     */
    protected static Result.Status computeStatus(List<Result> results) {
        for (Result result : results) {
            if (result != null && result.status() == Result.Status.FAIL) {
                return Result.Status.FAIL;
            }
        }
        for (Result result : results) {
            if (result != null && result.status() == Result.Status.SKIP) {
                return Result.Status.SKIP;
            }
        }
        return Result.Status.PASS;
    }

    /**
     * Finds the first failure in a list of results.
     *
     * @param results The results to search; must not be null.
     * @return The failure throwable, or null if no result failed.
     */
    protected static Throwable findFailure(List<Result> results) {
        for (Result result : results) {
            if (result != null && result.status() == Result.Status.FAIL) {
                return result.failure().orElse(null);
            }
        }
        return null;
    }
}
