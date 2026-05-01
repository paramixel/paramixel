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

package org.paramixel.core;

import java.util.List;
import java.util.Optional;

/**
 * Represents a named, executable unit of work.
 *
 * <p>Actions form a tree structure where parent actions coordinate the execution of their child actions.
 * Each action owns its execution {@link Result}, which is STAGED before execution and PASS/FAIL/SKIP after.</p>
 *
 * <h3>Action Lifecycle</h3>
 * <p>Each action follows a defined lifecycle during execution:</p>
 * <ol>
 *   <li><strong>Creation:</strong> Action is instantiated and added to a parent via {@link #addChild(Action)}</li>
 *   <li><strong>Staged:</strong> Before execution begins, result is {@link Status#isStaged()}</li>
 *   <li><strong>Execution:</strong> {@link #execute(Context)} is called with a {@link Context}</li>
 *   <li><strong>Completion:</strong> Result transitions to PASS, FAIL, or SKIP</li>
 * </ol>
 *
 * <h3>Tree Structure</h3>
 * <p>Actions form a hierarchical tree:
 * <ul>
 *   <li>Each action has at most one parent (accessible via {@link #getParent()})</li>
 *   <li>Each action may have zero or more children (accessible via {@link #getChildren()})</li>
 *   <li>Root actions have no parent</li>
 *   <li>Leaf actions have no children</li>
 *   <li>Parent-child relationships are established via {@link #addChild(Action)}</li>
 * </ul>
 *
 * <h3>Execution Patterns</h3>
 * <p>Different action types implement different execution patterns:
 * <ul>
 *   <li>{@link org.paramixel.core.action.Direct} - Executes a provided block directly</li>
 *   <li>{@link org.paramixel.core.action.Sequential} - Executes children sequentially</li>
 *   <li>{@link org.paramixel.core.action.Parallel} - Executes children concurrently</li>
 *   <li>{@link org.paramixel.core.action.Lifecycle} - Executes before/main/after phases</li>
 *   <li>{@link org.paramixel.core.action.Noop} - Does nothing, always passes</li>
 *   <li>{@link org.paramixel.core.action.StrictSequential} - Stops on first failure</li>
 *   <li>{@link org.paramixel.core.action.RandomSequential} - Executes children in random order</li>
 * </ul>
 *
 * <h3>Result Ownership</h3>
 * <p>Each action owns exactly one {@link Result} that transitions from STAGED to PASS/FAIL/SKIP.
 * For parent actions, the result is typically derived from child results:
 * <ul>
 *   <li>FAIL if any child failed</li>
 *   <li>SKIP if any child skipped and none failed</li>
 *   <li>PASS if all children passed</li>
 * </ul>
 * Some action types (e.g., Lifecycle) have special result composition rules.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Actions are not thread-safe by default. However:
 * <ul>
 *   <li>Each action executes with its own {@link Context}</li>
 *   <li>Parallel actions execute in separate threads</li>
 *   <li>Parent actions coordinate child execution safely via synchronization</li>
 *   <li>Custom action implementations must ensure thread-safety if accessed concurrently</li>
 * </ul>
 *
 * <h3>Custom Actions</h3>
 * <p>Custom actions can either extend {@link org.paramixel.core.action.AbstractAction}
 * or implement this interface directly. Direct implementations must maintain their own
 * parent reference and implement {@link #setParent(Action)} according to this contract.</p>
 *
 * <p><strong>Subclassing Example:</strong></p>
 * <pre>{@code
 * public final class MyAction extends AbstractAction {
 *     private MyAction(String name) { super(); this.name = validateName(name); }
 *
 *     public static MyAction of(String name) {
 *         MyAction instance = new MyAction(name);
 *         instance.initialize();
 *         return instance;
 *     }
 *
 *     @Override public void execute(Context ctx) {
 *         // Your logic here
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Direct Implementation Example:</strong></p>
 * <pre>{@code
 * public final class MyAction implements Action {
 *     private Action parent;
 *
 *     @Override
 *     public void setParent(Action parent) {
 *         this.parent = Objects.requireNonNull(parent, "parent must not be null");
 *     }
 *
 *     // implement remaining Action methods
 * }
 * }</pre>
 *
 * @see Context
 * @see Result
 * @see Status
 * @see Listener
 * @see org.paramixel.core.action.AbstractAction
 * @see org.paramixel.core.action.Direct
 */
public interface Action {

    /**
     * Sentinel name for root actions that should be hidden from output.
     *
     * <p>Created with {@code new String("<run>")} to prevent JVM interning,
     * enabling identity comparison ({@code ==}) instead of value comparison
     * ({@code equals}). This ensures only code that explicitly assigns
     * {@code Action.HIDDEN} will match, not arbitrary {@code "<run>"} literals.
     *
     * <p>Listeners and renderers should check {@code action.getName() == Action.HIDDEN}
     * and suppress the root action from hierarchical paths and display names.
     *
     * @see org.paramixel.core.listener.StatusListener
     */
    String HIDDEN = new String("<run>");

    /**
     * Returns this action's unique identifier.
     *
     * <p>The ID is a randomly generated UUID that uniquely identifies this action instance.
     * IDs are useful for:
     * <ul>
     *   <li>Tracking actions across logs and monitoring</li>
     *   <li>Correlating actions with their results in listeners</li>
     *   <li>Debugging execution traces</li>
     * </ul>
     *
     * <p>The ID is generated once at construction time and never changes. Two different
     * action instances will always have different IDs, even if they have the same name.</p>
     *
     * @return the generated action identifier; never {@code null}, always a valid UUID string
     */
    String getId();

    /**
     * Returns this action's display name.
     *
     * <p>The name is a human-readable identifier that describes the action's purpose.
     * Names are used in:
     * <ul>
     *   <li>Console output and log messages</li>
     *   <li>Listener callbacks for action identification</li>
     *   <li>User interfaces and reports</li>
     * </ul>
     *
     * <p>Names must be non-blank (not null, empty, or whitespace-only). They should be
     * descriptive and concise. Good naming practices:
     * <ul>
     *   <li>Use present tense verbs (e.g., "createDatabase", "validateUser")</li>
     *   <li>Be specific (e.g., "loadTestData" vs "load")</li>
     *   <li>Use camelCase for multi-word names</li>
     *   <li>Avoid special characters that might break formatting</li>
     * </ul>
     *
     * @return the non-blank action name; never {@code null} or blank
     */
    String getName();

    /**
     * Returns this action's parent when it has been adopted by another action.
     *
     * <p>The parent is the action that contains this action as a child. Parent actions
     * coordinate the execution of their children according to their execution pattern
     * (sequential, parallel, etc.).</p>
     *
     * <p>Parent-child relationships are established via {@link #addChild(Action)} and
     * {@link #setParent(Action)}. An action can have at most one parent. The root action
     * (top-level action in the tree) has no parent.</p>
     *
     * <p>Accessing the parent is useful for:
     * <ul>
     *   <li>Navigating the action hierarchy</li>
     *   <li>Understanding execution context</li>
     *   <li>Implementing custom execution patterns</li>
     * </ul>
     *
     * @return an {@link Optional} containing the parent action, or empty when this action is a root
     * @see #addChild(Action)
     * @see #getChildren()
     */
    Optional<Action> getParent();

    /**
     * Sets this action's parent.
     *
     * <p>This method establishes the parent-child relationship for this action. It is
     * typically called by composite actions during tree construction.</p>
     *
     * <p>Implementations must enforce the following invariants:</p>
     * <ul>
     *   <li>parent must not be {@code null}</li>
     *   <li>an action must not be its own parent</li>
     *   <li>a parent may only be assigned once</li>
     * </ul>
     *
     * @param parent the parent action
     * @throws NullPointerException if {@code parent} is {@code null}
     * @throws IllegalArgumentException if {@code parent} is this action
     * @throws IllegalStateException if this action already has a parent
     * @see #getParent()
     * @see #addChild(Action)
     */
    void setParent(Action parent);

    /**
     * Returns this action's child actions.
     *
     * <p>Child actions are the actions that this action coordinates. The execution order
     * and coordination pattern depends on the action type:
     * <ul>
     *   <li>{@link org.paramixel.core.action.Sequential} - Children execute in order</li>
     *   <li>{@link org.paramixel.core.action.Parallel} - Children execute concurrently</li>
     *   <li>{@link org.paramixel.core.action.Lifecycle} - Phases execute in order</li>
     *   <li>Leaf actions (e.g., {@link org.paramixel.core.action.Direct}) - Return empty list</li>
     * </ul>
     *
     * <p>The returned list is unmodifiable. Implementations must not expose a mutable
     * backing collection. Structural modifications through the returned list must throw
     * {@link UnsupportedOperationException}. The order of children in the list reflects
     * the execution order (for sequential actions) or registration order (for parallel
     * actions).</p>
     *
     * @return the child actions in execution order; never {@code null}, may be empty
     * @see #addChild(Action)
     * @see #getParent()
     */
    List<Action> getChildren();

    /**
     * Adds the given child action, making this action its parent.
     *
     * <p>This method establishes a parent-child relationship between actions. Implementations
     * typically do this by calling {@code child.setParent(this)}. Composite action
     * implementations are responsible for storing the child so it is exposed by
     * {@link #getChildren()}.</p>
     *
     * <p>Parent-child relationships are typically established when actions are constructed
     * and should not be changed after execution begins. Built-in action types automatically
     * add their children during construction.</p>
     *
     * <p><strong>Child Addition Rules:</strong></p>
     * <ul>
     *   <li>A child can have at most one parent</li>
     *   <li>Children that already have a parent are rejected</li>
     *   <li>An action cannot be added as its own child</li>
     *   <li>Null children are not allowed</li>
     * </ul>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * Sequential parent = Sequential.of("parent", child1, child2);
     * // parent automatically added child1 and child2
     *
     * // Manual child registration
     * Direct customChild = Direct.of("custom", ctx -> {});
     * parent.addChild(customChild);
     * }</pre>
     *
     * @param child the child action to add; must not be {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code child} is this action
     * @throws IllegalStateException if {@code child} already has a parent
     * @see #getParent()
     * @see #getChildren()
     */
    void addChild(Action child);

    /**
     * Executes this action with the provided context.
     *
     * <p>This method is called by the framework to run the action. The implementation
     * should:
     * <ol>
     *   <li>Set result to STAGED via listener callback</li>
     *   <li>Execute the action's business logic</li>
     *   <li>Handle any exceptions appropriately</li>
     *   <li>Set result to PASS, FAIL, or SKIP</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>Invoke {@code context.getListener().beforeAction(context, this)} before execution</li>
     *   <li>Execute the action's logic</li>
     *   <li>Invoke {@code context.getListener().afterAction(context, this, result)} after execution</li>
     *   <li>Set the final {@link Result} via {@code this.result = ...}</li>
     * </ul>
     *
     * <p><strong>Exception Handling:</strong></p>
     * <ul>
     *   <li>{@link FailException} - Marks action as FAILED, does not propagate</li>
     *   <li>{@link SkipException} - Marks action as SKIPPED, does not propagate</li>
     *   <li>Other exceptions - Marks action as FAILED, propagates to parent</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong></p>
     * <p>This method may be called concurrently for parallel actions. Implementations must
     * be thread-safe or use synchronization as needed. Each invocation receives its own
     * {@link Context} instance.</p>
     *
     * @param context the execution context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     * @see Context
     * @see Listener#beforeAction(Context, Action)
     * @see Listener#afterAction(Context, Action, Result)
     * @see #skip(Context)
     */
    void execute(Context context);

    /**
     * Skips this action without executing it.
     *
     * <p>This method marks the action as SKIPPED without running its business logic.
     * The implementation should:
     * <ol>
     *   <li>Set result to STAGED via listener callback</li>
     *   <li>Immediately set result to SKIP with zero duration</li>
     *   <li>Invoke listener callbacks</li>
     * </ol>
     *
     * <p><strong>Expected Behavior:</strong></p>
     * <ul>
     *   <li>Invoke {@code context.getListener().beforeAction(context, this)}</li>
     *   <li>Set result to SKIP with {@link java.time.Duration#ZERO}</li>
     *   <li>Invoke {@code context.getListener().afterAction(context, this, result)}</li>
     *   <li>Do not execute any business logic</li>
     * </ul>
     *
     * <p>Skipping is useful for:
     * <ul>
     *   <li>Conditional execution based on runtime state</li>
     *   <li>Skipping remaining siblings after a failure (in strict action types)</li>
     *   <li>Disabling specific tests or actions</li>
     *   <li>Implementing feature flags</li>
     * </ul>
     *
     * <p><strong>Alternative:</strong> Throw {@link SkipException} from within {@link #execute(Context)}
     * to skip dynamically based on conditions.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * if (!shouldRun) {
     *     action.skip(context);
     *     return;
     * }
     * }</pre>
     *
     * @param context the execution context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     * @see SkipException
     * @see #execute(Context)
     */
    void skip(Context context);

    /**
     * Returns this action's execution result.
     *
     * <p>The result provides information about the action's execution outcome:
     * <ul>
     *   <li>{@link Result#getStatus()} - PASS, FAIL, SKIP, or STAGED</li>
     *   <li>{@link Result#getElapsedTime()} - Execution duration</li>
     * </ul>
     *
     * <p><strong>Result Lifecycle:</strong></p>
     * <ul>
     *   <li>Before execution: STAGED</li>
     *   <li>After successful execution: PASS</li>
     *   <li>After failed execution: FAIL</li>
     *   <li>After skipped execution: SKIP</li>
     * </ul>
     *
     * <p>For parent actions, the result is derived from child results according to the
     * action type's composition rules. For leaf actions, the result reflects the
     * execution of the action's business logic.</p>
     *
     * <p>The result is available:
     * <ul>
     *   <li>During execution after {@link Listener#afterAction(Context, Action, Result)} callback</li>
     *   <li>After execution completes</li>
     *   <li>In listener callbacks</li>
     * </ul>
     *
     * <p>During execution (before listener callbacks), the result may be in the STAGED state.</p>
     *
     * @return the result for this action; never {@code null}
     * @see Result
     * @see Status
     * @see Listener#afterAction(Context, Action, Result)
     */
    Result getResult();
}
