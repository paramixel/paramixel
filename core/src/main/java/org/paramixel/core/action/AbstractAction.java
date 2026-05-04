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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;
import org.paramixel.core.support.FastId;

/**
 * Base implementation for {@link Action} types.
 *
 * <p>This class provides generated identifiers, name validation, and parent tracking shared by both leaf and branch
 * actions.
 *
 * @implSpec Parent assignment is one-time only. After a parent has been set, later attempts to replace it fail.
 */
public abstract class AbstractAction implements Action {

    protected final String id;
    protected String name;
    private final AtomicReference<Action> parent = new AtomicReference<>();

    /**
     * Creates an action with a generated stable identifier.
     */
    protected AbstractAction() {
        this.id = FastId.generateId();
    }

    /**
     * Validates and returns an action name.
     *
     * @param name the name to validate
     * @return the validated name
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    protected String validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        return name;
    }

    /**
     * Performs post-construction initialization.
     *
     * <p>Subclasses may override this hook when a factory method needs to finish setup after construction.
     */
    protected void initialize() {}

    /**
     * Returns the generated identifier for this action.
     *
     * @return the action identifier
     */
    @Override
    public final String getId() {
        return id;
    }

    /**
     * Returns the configured display name for this action.
     *
     * @return the action name
     */
    @Override
    public final String getName() {
        return name;
    }

    /**
     * Returns the parent action, if one has been assigned.
     *
     * @return the parent action, or an empty {@link Optional} when this action is a root
     */
    @Override
    public final Optional<Action> getParent() {
        return Optional.ofNullable(parent.get());
    }

    /**
     * Records the supplied action as a child by assigning this action as that child's parent.
     *
     * @param child the child action to attach
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code child} is this action
     */
    @Override
    public void addChild(Action child) {
        Objects.requireNonNull(child, "child must not be null");
        if (child == this) {
            throw new IllegalArgumentException("action must not add itself as a child");
        }
        child.setParent(this);
    }

    /**
     * Sets the parent for this action.
     *
     * @param parent the parent action
     * @throws NullPointerException if {@code parent} is {@code null}
     * @throws IllegalArgumentException if {@code parent} is this action
     * @throws IllegalStateException if this action already has a parent
     */
    @Override
    public void setParent(Action parent) {
        Objects.requireNonNull(parent, "parent must not be null");
        if (parent == this) {
            throw new IllegalArgumentException("action must not be its own parent");
        }
        if (!this.parent.compareAndSet(null, parent)) {
            throw new IllegalStateException("child already has a parent");
        }
    }

    /**
     * Executes this action.
     *
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public abstract Result execute(Context context);

    /**
     * Produces a skipped result for this action.
     *
     * @param context the execution context
     * @return the skipped result
     */
    @Override
    public abstract Result skip(Context context);

    /**
     * Returns the elapsed duration since the supplied instant.
     *
     * @param start the start instant
     * @return the duration between {@code start} and the current instant
     */
    protected Duration durationSince(Instant start) {
        return Duration.between(start, Instant.now());
    }
}
