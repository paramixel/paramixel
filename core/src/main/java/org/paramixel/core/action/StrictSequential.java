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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that executes child actions sequentially and stops on the first failure.
 *
 * <p>StrictSequential executes child actions one at a time in registration order,
 * but stops execution immediately when any child fails. Remaining siblings and their
 * descendants are marked as SKIPPED rather than executed.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Children execute in registration order</li>
 *   <li>Stops on first failure (fast-fail pattern)</li>
 *   <li>Remaining siblings are skipped (not executed)</li>
 *   <li>Status derived from child results (FAIL if any failed)</li>
 *   <li>Timing includes executed children only</li>
 * </ul>
 *
 * <h3>Fast-Fail Behavior</h3>
 * <p>When a child fails:</p>
 * <ol>
 *   <li>Failing child completes with FAIL status</li>
 *   <li>All remaining siblings are skipped</li>
 *   <li>Skip applies recursively to descendants</li>
 *   <li>Parent result is FAIL</li>
 * </ol>
 *
 * <h3>Status Derivation</h3>
 * <ul>
 *   <li>If any executed child failed → parent is FAIL</li>
 *   <li>Else if any executed child skipped → parent is SKIP</li>
 *   <li>Else (all passed) → parent is PASS</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Validation Pipeline:</strong></p>
 * <pre>{@code
 * Action validation = StrictSequential.of("validate",
 *     Direct.of("checkInput", this::validateInput),
 *     Direct.of("checkDatabase", this::validateDatabase),
 *     Direct.of("checkPermissions", this::validatePermissions)
 * );
 * // Stops on first validation failure
 * }</pre>
 *
 * <p><strong>Incremental Build:</strong></p>
 * <pre>{@code
 * Action build = StrictSequential.of("build",
 *     Direct.of("compile", this::compile),
 *     Direct.of("test", this::test),
 *     Direct.of("package", this::package)
 * );
 * // Won't test or package if compile fails
 * }</pre>
 *
 * <h3>Comparison with Sequential</h3>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>StrictSequential</th>
 *     <th>Sequential</th>
 *   </tr>
 *   <tr>
 *     <td>On Failure</td>
 *     <td>Stops, skips remaining</td>
 *     <td>Continues</td>
 *   </tr>
 *   <tr>
 *     <td>Total Time</td>
 *     <td>Shorter (stops early)</td>
 *     <td>Full execution time</td>
 *   </tr>
 *   <tr>
 *     <td>Use Case</td>
 *     <td>Validation, pipelines</td>
 *     <td>Test suites, reports</td>
 *   </tr>
 *   <tr>
 *     <td>Failure Visibility</td>
 *     <td>First failure only</td>
 *     <td>All failures</td>
 *   </tr>
 * </table>
 *
 * <h3>When to Use</h3>
 * <ul>
 *   <li><strong>Validation:</strong> Fail fast on invalid input</li>
 *   <li><strong>Pipelines:</strong> Don't proceed if a step fails</li>
 *   <li><strong>Builds:</strong> Stop compilation on errors</li>
 *   <li><strong>Prerequisites:</strong> Ensure setup before main logic</li>
 * </ul>
 *
 * @see Sequential
 * @see RandomSequential
 */
public class StrictSequential extends AbstractAction {

    private final List<Action> children;

    /**
     * Creates a strict sequential action with the supplied children.
     *
     * @param name the action name
     * @param children the child actions to execute in order
     */
    protected StrictSequential(String name, List<Action> children) {
        super();
        this.name = validateName(name);
        this.children = validateChildren(children);
    }

    /**
     * Creates a strict sequential action.
     *
     * <p>Children execute in registration order. Execution stops on the first failure.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new strict sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictSequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictSequential instance = new StrictSequential(name, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a strict sequential action from varargs children.
     *
     * <p>Convenient method for creating actions with a variable number of children.
     * Execution stops on the first failure.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new strict sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictSequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictSequential instance = new StrictSequential(name, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Returns the child actions executed by this strict sequential action.
     *
     * <p>The returned list is unmodifiable and preserves the registration order established
     * at construction time.</p>
     *
     * @return the child actions in registration order; never {@code null}
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    /**
     * Executes children sequentially, stopping on the first failure.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Execute each child in order</li>
     *   <li>If child fails: skip all remaining siblings</li>
     *   <li>Compute status from executed children</li>
     *   <li>Invoke listener's afterAction callback</li>
     * </ol>
     *
     * @param context the execution context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     */
    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Result.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();
        for (Action child : getChildren()) {
            child.execute(context.createChild());
            if (child.getResult().getStatus().isFailure()) {
                for (Action remaining : getChildren()
                        .subList(getChildren().indexOf(child) + 1, getChildren().size())) {
                    remaining.skip(context.createChild());
                }
                break;
            }
        }
        this.result = Result.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
