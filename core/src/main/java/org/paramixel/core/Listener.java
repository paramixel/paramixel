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

import org.paramixel.core.listener.Listeners;

/**
 * Receives notifications during action execution.
 *
 * <p>Listeners provide a callback mechanism for observing and reacting to action execution.
 * They are notified at key points during the execution lifecycle, enabling functionality such as:
 * <ul>
 *   <li>Logging and monitoring</li>
 *   <li>Progress reporting</li>
 *   <li>Metrics collection</li>
 *   <li>Summary generation</li>
 *   <li>Custom notification integration</li>
 * </ul>
 *
 * <h3>Callback Invocation Order</h3>
 * <p>For each action, callbacks are invoked in the following order:</p>
 * <pre>
 * runStarted(rootAction)
 *   beforeAction(action)
 *     [action executes]
 *   actionThrowable(if exception thrown)
 *   afterAction(action, result)
 * runCompleted(rootAction)
 * </pre>
 *
 * <p>For nested actions, parent callbacks wrap child callbacks:</p>
 * <pre>
 * beforeAction(parent)
 *   beforeAction(child1)
 *     afterAction(child1, result1)
 *   beforeAction(child2)
 *     afterAction(child2, result2)
 * afterAction(parent, parentResult)
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Listeners are invoked from multiple threads during parallel execution.
 * Implementations must be thread-safe or use synchronization as needed.
 * The framework does not provide synchronization guarantees.</p>
 *
 * <h3>Exception Handling</h3>
 * <p>Exceptions thrown by listener methods can interrupt execution. To prevent listener
 * exceptions from breaking the execution flow, wrap listeners in
 * {@link org.paramixel.core.listener.SafeListener} which catches and logs non-fatal
 * exceptions. Note that {@link Error} subclasses are never caught by {@code SafeListener}
 * and always propagate immediately.</p>
 *
 * <h3>Built-in Listeners</h3>
 * <p>The framework provides several built-in listeners:</p>
 * <ul>
 *   <li>{@link org.paramixel.core.listener.StatusListener} - Logs action status</li>
 *   <li>{@link org.paramixel.core.listener.SummaryListener} - Generates execution summary</li>
 *   <li>{@link org.paramixel.core.listener.CompositeListener} - Combines multiple listeners</li>
 * </ul>
 *
 * <h3>Custom Listeners</h3>
 * <p>Create custom listeners by implementing this interface:</p>
 * <pre>{@code
 * public class CustomListener implements Listener {
 *     @Override
 *     public void afterAction(Context ctx, Action action, Result result) {
 *         // Custom logic
 *     }
 * }
 * }</pre>
 *
 * <h3>Listener Composition</h3>
 * <p>Combine multiple listeners using {@link org.paramixel.core.listener.CompositeListener}:</p>
 * <pre>{@code
 * Listener combined = CompositeListener.of(
 *     StatusListener.of(),
 *     new SummaryListener(),
 *     new CustomListener()
 * );
 * }</pre>
 *
 * @see Runner
 * @see Context
 * @see Action
 * @see Result
 * @see org.paramixel.core.listener.StatusListener
 * @see org.paramixel.core.listener.SummaryListener
 * @see org.paramixel.core.listener.CompositeListener
 * @see org.paramixel.core.listener.SafeListener
 */
public interface Listener {

    /**
     * Creates the default execution listener.
     *
     * <p>The default listener combines {@link org.paramixel.core.listener.StatusListener}
     * and {@link org.paramixel.core.listener.SummaryListener} with a table-formatted
     * summary renderer.</p>
     *
     * <p>The default listener provides:
     * <ul>
     *   <li>Status logging for each action (PASS/FAIL/SKIP)</li>
     *   <li>Execution timing information</li>
     *   <li>Table-formatted summary at completion</li>
     *   <li>ANSI color coding for terminal output</li>
     * </ul>
     *
     * <p>This is suitable for most console-based applications. For different output formats,
     * use {@link #treeListener()} or create a custom listener composition.</p>
     *
     * @return a default listener instance; never {@code null}
     * @see #treeListener()
     * @see org.paramixel.core.listener.StatusListener
     * @see org.paramixel.core.listener.SummaryListener
     */
    static Listener defaultListener() {
        return Listeners.defaultListener();
    }

    /**
     * Creates a listener with tree-formatted summary output.
     *
     * <p>This listener combines {@link org.paramixel.core.listener.StatusListener}
     * and {@link org.paramixel.core.listener.SummaryListener} with a tree-formatted
     * summary renderer.</p>
     *
     * <p>The tree listener provides:
     * <ul>
     *   <li>Status logging for each action (PASS/FAIL/SKIP)</li>
     *   <li>Execution timing information</li>
     *   <li>Hierarchical tree-formatted summary showing action nesting</li>
     *   <li>ANSI color coding for terminal output</li>
     * </ul>
     *
     * <p>The tree format is useful for visualizing the action hierarchy and understanding
     * parent-child relationships. Use this when the action tree has significant depth
     * or when visual hierarchy is important.</p>
     *
     * @return a listener with tree summary; never {@code null}
     * @see #defaultListener()
     * @see org.paramixel.core.listener.TreeSummaryRenderer
     */
    static Listener treeListener() {
        return Listeners.treeListener();
    }

    /**
     * Invoked before the execution plan starts.
     *
     * <p>This callback is invoked once at the beginning of execution, before any action
     * executes. It provides the root action and the runner that will manage execution.</p>
     *
     * <p><strong>Typical Uses:</strong></p>
     * <ul>
     *   <li>Initialize logging or monitoring</li>
     *   <li>Record start time</li>
     *   <li>Display execution banner</li>
     *   <li>Validate configuration</li>
     * </ul>
     *
     * <p><strong>State:</strong> The root action's result is in STAGED state.
     * No actions have executed yet.</p>
     *
     * <p><strong>Thread:</strong> Always invoked on the main execution thread.</p>
     *
     * <p><strong>Default Implementation:</strong> Does nothing.</p>
     *
     * @param runner the runner executing the plan; never {@code null}
     * @param action the root action to be executed; never {@code null}
     * @see #runCompleted(Runner, Action)
     * @see Runner#run(Action)
     */
    default void runStarted(Runner runner, Action action) {}

    /**
     * Invoked before an action starts executing.
     *
     * <p>This callback is invoked for every action immediately before it begins execution.
     * The action's result is in STAGED state at this point.</p>
     *
     * <p><strong>Invocation Order:</strong></p>
     * <pre>
     * beforeAction(parent)
     *   beforeAction(child1)
     *   beforeAction(child2)
     * </pre>
     *
     * <p><strong>Typical Uses:</strong></p>
     * <ul>
     *   <li>Log action start</li>
     *   <li>Record start time for metrics</li>
     *   <li>Display progress</li>
     *   <li>Validate prerequisites</li>
     * </ul>
     *
     * <p><strong>State:</strong> The action's result is {@link Status#isStaged()}.
     * No children have executed yet. The context is available for inspection.</p>
     *
     * <p><strong>Thread:</strong> Invoked on the thread that will execute the action.
     * For parallel actions, invoked on the worker thread.</p>
     *
     * <p><strong>Default Implementation:</strong> Does nothing.</p>
     *
     * @param context the active context; never {@code null}
     * @param action the action about to execute; never {@code null}
     * @see #afterAction(Context, Action, Result)
     * @see #actionThrowable(Context, Action, Throwable)
     * @see Action#execute(Context)
     */
    default void beforeAction(Context context, Action action) {}

    /**
     * Invoked when an unexpected exception is thrown during action execution.
     *
     * <p>This callback is invoked when an action throws an exception that is not a
     * {@link FailException} or {@link SkipException}. These control-flow exceptions
     * are handled differently and do not trigger this callback.</p>
     *
     * <p><strong>Invocation Timing:</strong></p>
     * <p>This method is called <strong>before</strong> {@link #afterAction(Context, Action, Result)}
     * for the failed action. The action's result will be set to FAIL with the exception
     * information.</p>
     *
     * <p><strong>Typical Uses:</strong></p>
     * <ul>
     *   <li>Log stack traces</li>
     *   <li>Send error notifications</li>
     *   <li>Record failure metrics</li>
     *   <li>Trigger error handling workflows</li>
     * </ul>
     *
     * <p><strong>State:</strong> The action's result is still in STAGED state.
     * The exception information is provided in the throwable parameter.
     * The {@link #afterAction(Context, Action, Result)} callback will follow.</p>
     *
     * <p><strong>Thread:</strong> Invoked on the thread where the exception occurred.</p>
     *
     * <p><strong>Exception Handling:</strong> Exceptions thrown from this method
     * propagate up and may interrupt execution. Wrap listeners in
     * {@link org.paramixel.core.listener.SafeListener} to prevent this.
     * Note that {@link Error} subclasses are always rethrown, even by {@code SafeListener}.</p>
     *
     * <p><strong>Default Implementation:</strong> Does nothing.</p>
     *
     * @param context the active context; never {@code null}
     * @param action the action that threw the exception; never {@code null}
     * @param throwable the exception that was thrown; never {@code null}
     * @see #beforeAction(Context, Action)
     * @see #afterAction(Context, Action, Result)
     * @see FailException
     * @see SkipException
     */
    default void actionThrowable(Context context, Action action, Throwable throwable) {}

    /**
     * Invoked after an action finishes executing.
     *
     * <p>This callback is invoked for every action after it completes execution,
     * whether it passed, failed, or was skipped.</p>
     *
     * <p><strong>Invocation Order:</strong></p>
     * <pre>
     * beforeAction(parent)
     *   beforeAction(child1)
     *   afterAction(child1, result1)
     *   beforeAction(child2)
     *   afterAction(child2, result2)
     * afterAction(parent, parentResult)
     * </pre>
     *
     * <p><strong>Typical Uses:</strong></p>
     * <ul>
     *   <li>Log action completion</li>
     *   <li>Record execution metrics</li>
     *   <li>Update progress indicators</li>
     *   <li>Collect results for reporting</li>
     * </ul>
     *
     * <p><strong>State:</strong> The action's result is final (PASS/FAIL/SKIP).
     * All children have completed (for parent actions). Timing information is available.</p>
     *
     * <p><strong>Result States:</strong></p>
     * <ul>
     *   <li>{@link Status#isPass()} - Action succeeded</li>
     *   <li>{@link Status#isFailure()} - Action failed (exception or FailException)</li>
     *   <li>{@link Status#isSkip()} - Action skipped (SkipException or parent skip)</li>
     * </ul>
     *
     * <p><strong>Thread:</strong> Invoked on the thread that executed the action.
     * For parallel actions, invoked on the worker thread.</p>
     *
     * <p><strong>Exception Handling:</strong> Exceptions thrown from this method
     * propagate up and may interrupt execution. Wrap listeners in
     * {@link org.paramixel.core.listener.SafeListener} to prevent this.
     * Note that {@link Error} subclasses are always rethrown, even by {@code SafeListener}.</p>
     *
     * <p><strong>Default Implementation:</strong> Does nothing.</p>
     *
     * @param context the active context; never {@code null}
     * @param action the completed action; never {@code null}
     * @param result the execution result; never {@code null}
     * @see #beforeAction(Context, Action)
     * @see #actionThrowable(Context, Action, Throwable)
     * @see Result
     * @see Status
     */
    default void afterAction(Context context, Action action, Result result) {}

    /**
     * Invoked after the execution plan completes.
     *
     * <p>This callback is invoked once after all actions have finished executing.
     * All actions have completed and their final results are available.</p>
     *
     * <p><strong>Typical Uses:</strong></p>
     * <ul>
     *   <li>Generate execution summary</li>
     *   <li>Log completion statistics</li>
     *   <li>Cleanup resources</li>
     *   <li>Display final results</li>
     * </ul>
     *
     * <p><strong>State:</strong> All actions have completed. Their results reflect
     * their final outcomes (PASS/FAIL/SKIP). The root action's result is available via
     * {@code action.getResult()}.</p>
     *
     * <p><strong>Thread:</strong> Always invoked on the main execution thread.</p>
     *
     * <p><strong>Default Implementation:</strong> Does nothing.</p>
     *
     * @param runner the runner that executed the plan; never {@code null}
     * @param action the root action that was executed; never {@code null}
     * @see #runStarted(Runner, Action)
     * @see Runner#run(Action)
     */
    default void runCompleted(Runner runner, Action action) {}
}
