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
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;
import org.paramixel.core.support.FastId;

/**
 * Base implementation for {@link Action} types.
 *
 * <p>This class provides generated identifiers, name validation, context scoping, and execute/skip null checks shared by
 * framework-provided action types.
 */
public abstract class AbstractAction implements Action {

    protected final String id;
    protected String name;
    private final ContextMode contextMode;

    /**
     * Creates an action with a generated stable identifier.
     */
    protected AbstractAction() {
        this(ContextMode.ISOLATED);
    }

    /**
     * Creates an action with a generated stable identifier and context mode.
     *
     * @param contextMode the context mode applied when this action executes or skips
     */
    protected AbstractAction(ContextMode contextMode) {
        this.id = FastId.generateId();
        this.contextMode = Objects.requireNonNull(contextMode, "contextMode must not be null");
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

    @Override
    public final ContextMode getContextMode() {
        return contextMode;
    }

    /**
     * Executes this action.
     *
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public final Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        return executeSelf(contextForSelf(context));
    }

    /**
     * Produces a skipped result for this action.
     *
     * @param context the execution context
     * @return the skipped result
     */
    @Override
    public final Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        return skipSelf(contextForSelf(context));
    }

    /**
     * Executes this action using its effective context.
     *
     * @param context the effective context after applying this action's context mode
     * @return the execution result
     */
    protected abstract Result executeSelf(Context context);

    /**
     * Produces a skipped result using this action's effective context.
     *
     * @param context the effective context after applying this action's context mode
     * @return the skipped result
     */
    protected abstract Result skipSelf(Context context);

    /**
     * Applies this action's context mode to the received parent context.
     *
     * @param context the context received from the parent action or runner
     * @return this action's effective execution context
     */
    protected final Context contextForSelf(Context context) {
        return contextMode == ContextMode.ISOLATED ? context.createChild() : context;
    }

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
