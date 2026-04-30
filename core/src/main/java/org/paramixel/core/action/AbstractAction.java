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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.internal.DefaultStatus;
import org.paramixel.core.internal.Results;

/**
 * Convenience base class for implementing {@link Action}.
 *
 * <p>This class provides common functionality for actions including:
 * <ul>
 *   <li>ID and name management</li>
 *   <li>Parent-child relationship management via {@link #addChild(Action)}</li>
 *   <li>Default {@link #skip(Context)} implementation</li>
 *   <li>Status computation from child results via {@link #computeStatus()}</li>
 *   <li>Timing utilities via {@link #durationSince(Instant)}</li>
 * </ul>
 *
 * <p>By default, {@code AbstractAction} behaves like a leaf action: {@link #getChildren()}
 * returns an empty list. Composite action implementations should store their children in an
 * unmodifiable list and usually initialize that list with {@link #validateChildren(List)} so
 * parent-child relationships and invariants are enforced consistently.</p>
 *
 * <p>Actions that extend this class should implement {@link Action#execute(Context)} to define
 * their execution logic. The {@code result} field is protected and can be set directly by subclasses.</p>
 *
 * <p><strong>Custom Composite Example:</strong></p>
 * <pre>{@code
 * public final class CustomComposite extends AbstractAction {
 *     private final List<Action> children;
 *
 *     public CustomComposite(String name, List<Action> children) {
 *         super(name);
 *         this.children = validateChildren(children);
 *     }
 *
 *     @Override
 *     public List<Action> getChildren() {
 *         return children;
 *     }
 *
 *     @Override
 *     public void execute(Context context) {
 *         // custom execution logic
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractAction implements Action {

    protected final String id;
    protected final String name;
    protected Action parent;
    protected volatile Result result = Results.staged();

    protected AbstractAction(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final Optional<Action> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public List<Action> getChildren() {
        return List.of();
    }

    @Override
    public final Result getResult() {
        return result;
    }

    /**
     * Establishes a parent-child relationship between this action and the given child.
     *
     * <p>This method only updates the child's parent reference. Composite implementations
     * are still responsible for storing their children so that {@link #getChildren()}
     * exposes the tree structure.</p>
     *
     * @param child The child action to add; must not be null.
     */
    @Override
    public void addChild(Action child) {
        Objects.requireNonNull(child, "child must not be null");
        if (child == this) {
            throw new IllegalArgumentException("action must not add itself as a child");
        }
        if (!(child instanceof AbstractAction executableChild)) {
            throw new IllegalArgumentException("child must extend AbstractAction");
        }
        if (executableChild.parent != null) {
            throw new IllegalStateException("child already has a parent");
        }
        executableChild.parent = this;
    }

    /**
     * Runs this action.
     *
     * <p>Subclasses must implement this method to define their run logic.</p>
     *
     * @param context The execution context.
     */
    @Override
    public abstract void execute(Context context);

    /**
     * Skips this action without running it.
     *
     * <p>The default implementation sets the result to SKIP and fires the appropriate listener callbacks.</p>
     *
     * @param context The execution context.
     */
    @Override
    public void skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Results.staged();
        context.getListener().beforeAction(context, this);
        this.result = Results.skip(Duration.ZERO);
        context.getListener().afterAction(context, this, this.result);
    }

    /**
     * Computes the status for this action from its child results.
     *
     * <p>This method reads the result status from all children returned by {@link #getChildren()}
     * and returns a composite status based on the following rules:
     * <ul>
     *   <li>If any child failed, returns {@link DefaultStatus#failure()}</li>
     *   <li>Else if any child skipped, returns {@link DefaultStatus#skip()}</li>
     *   <li>Otherwise returns {@link DefaultStatus#pass()}</li>
     * </ul>
     *
     * @return The computed status.
     */
    protected Status computeStatus() {
        for (Action child : getChildren()) {
            Objects.requireNonNull(child, "getChildren() must not contain null elements");
            if (child.getResult().getStatus().isFailure()) {
                return DefaultStatus.failure();
            }
        }
        for (Action child : getChildren()) {
            if (child.getResult().getStatus().isSkip()) {
                return DefaultStatus.skip();
            }
        }
        return DefaultStatus.pass();
    }

    /**
     * Computes the duration between the given start instant and the current time.
     *
     * @param start The start instant.
     * @return The elapsed duration.
     */
    protected Duration durationSince(Instant start) {
        return Duration.between(start, Instant.now());
    }

    /**
     * Validates the list of child actions, ensuring it is not null, not empty, and contains no null elements.
     * Also establishes parent-child relationships with each validated child.
     *
     * @param children the child actions to validate; must not be null or empty
     * @return an unmodifiable list of validated child actions
     * @throws NullPointerException if {@code children} or any child element is null
     * @throws IllegalArgumentException if {@code children} is empty
     * @throws IllegalStateException if any child already has a parent
     */
    protected List<Action> validateChildren(List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("action must have at least one child");
        }
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children must not contain null elements");
            addChild(child);
            validated.add(child);
        }
        return Collections.unmodifiableList(validated);
    }
}
