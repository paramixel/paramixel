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
import java.util.concurrent.atomic.AtomicReference;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.support.Arguments;
import org.paramixel.core.support.FastId;

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
 *     private CustomComposite(String name, List<Action> children) {
 *         super();
 *         this.name = validateName(name);
 *         this.children = validateChildren(children);
 *     }
 *
 *     public static CustomComposite of(String name, List<Action> children) {
 *         Objects.requireNonNull(children, "children must not be null");
 *         CustomComposite instance = new CustomComposite(name, children);
 *         instance.initialize();
 *         return instance;
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
    protected String name;
    private final AtomicReference<Action> parent = new AtomicReference<>();
    protected volatile Result result = Result.staged();

    /**
     * Creates a new abstract action with a generated 4-character unique identifier.
     *
     * <p>Subclasses are responsible for assigning and validating {@link #name} before the
     * action becomes externally visible, typically from a factory method.</p>
     */
    protected AbstractAction() {
        this.id = FastId.generateId();
    }

    /**
     * Validates an action name before it is assigned to the action.
     *
     * <p>Names must be non-null and contain at least one non-whitespace character.</p>
     *
     * @param name the proposed action name
     * @return the validated name, unchanged
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    protected String validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        return name;
    }

    /**
     * Hook method called by factory methods after construction.
     *
     * <p>Subclasses may override this method to perform post-construction setup
     * that requires the fully initialized object. The default implementation does nothing.</p>
     *
     * <p>This method is called automatically by static factory methods (e.g., {@code of(...)})
     * after construction. When subclassing, remember to call {@code initialize()} from your
     * own factory methods.</p>
     *
     * <p>Name validation is handled by {@link #validateName(String)} in the constructor,
     * not by this method.</p>
     */
    protected void initialize() {
        // Default no-op. Subclasses may override for post-construction setup.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Optional<Action> getParent() {
        return Optional.ofNullable(parent.get());
    }

    /**
     * Returns this action's child actions.
     *
     * <p>The base implementation returns an empty immutable list. Composite action types
     * should override this method to expose their managed children.</p>
     *
     * @return an immutable list of child actions, empty for leaf actions
     */
    @Override
    public List<Action> getChildren() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     */
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
        child.setParent(this);
    }

    /**
     * Assigns this action's parent exactly once.
     *
     * <p>This method prevents self-parenting and reassignment after a parent has already
     * been established. Parent assignment is thread-safe; if multiple threads attempt to
     * set the parent concurrently, exactly one will succeed and all others will receive
     * an {@link IllegalStateException}.</p>
     *
     * @apiNote This implementation uses atomic compare-and-set, so concurrent calls are
     *          safe and will not silently overwrite an existing parent assignment.
     * @param parent the parent action to assign
     * @throws NullPointerException if {@code parent} is {@code null}
     * @throws IllegalArgumentException if {@code parent == this}
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
     * <p>The default implementation fires {@code beforeAction}, sets the result to SKIP, and fires
     * {@code afterAction} atomically. This is appropriate for leaf actions. Composite actions that
     * need to skip descendants with interleaved listener callbacks (parent beforeAction, then
     * children, then parent afterAction) should manage the skip lifecycle manually using
     * {@link #setResult(Result)}.</p>
     *
     * @param context The execution context.
     */
    @Override
    public void skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Result.staged();
        context.getListener().beforeAction(context, this);
        this.result = Result.skip(Duration.ZERO);
        context.getListener().afterAction(context, this, this.result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setResult(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        this.result = result;
    }

    /**
     * Computes the status for this action from its child results.
     *
     * <p>This method reads the result status from all children returned by {@link #getChildren()}
     * and returns a composite status based on the following rules:
     * <ul>
     *   <li>If any child failed, returns {@link Status#failure()}</li>
     *   <li>Else if any child skipped, returns {@link Status#skip()}</li>
     *   <li>Otherwise returns {@link Status#pass()}</li>
     * </ul>
     *
     * @return The computed status.
     */
    protected Status computeStatus() {
        for (Action child : getChildren()) {
            Objects.requireNonNull(child, "getChildren() must not contain null elements");
            if (child.getResult().getStatus().isFailure()) {
                return Status.failure();
            }
        }
        for (Action child : getChildren()) {
            if (child.getResult().getStatus().isSkip()) {
                return Status.skip();
            }
        }
        return Status.pass();
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
            throw new IllegalArgumentException("children must not be empty");
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
