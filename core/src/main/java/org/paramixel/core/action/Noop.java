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
import java.util.Objects;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that completes without doing any work.
 *
 * <p>Noop (no-operation) is an action that executes successfully without performing
 * any business logic. It always completes with a PASS status and zero elapsed time.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Always passes (result is PASS)</li>
 *   <li>Has no child actions (leaf action)</li>
 *   <li>Executes instantly with zero timing</li>
 *   <li>Not designed for concurrent re-execution (use separate instances)</li>
 * </ul>
 *
 * <h3>Use Cases</h3>
 * <ul>
 *   <li><strong>Placeholders:</strong> Reserve a spot in an action tree for future implementation</li>
 *   <li><strong>Testing:</strong> Create mock actions for unit tests</li>
 *   <li><strong>Conditional Branching:</strong> Provide a "do nothing" branch in conditional logic</li>
 *   <li><strong>Prototyping:</strong> Stub out actions during development</li>
 *   <li><strong>Default Actions:</strong> Provide a default when no action is needed</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Placeholder in Sequential:</strong></p>
 * <pre>{@code
 * Sequential actions = Sequential.of("workflow",
 *     Direct.of("step1", this::doStep1),
 *     Noop.of("step2-placeholder"), // TODO: implement later
 *     Direct.of("step3", this::doStep3)
 * );
 * }</pre>
 *
 * <p><strong>Testing:</strong></p>
 * <pre>{@code
 * @Test
 * void testRunner() {
 *     Action action = Noop.of("noop");
 *     Runner.builder().build().run(action);
 *     assertEquals(Status.PASS, action.getResult().getStatus());
 * }
 * }</pre>
 *
 * <p><strong>Conditional Branching:</strong></p>
 * <pre>{@code
 * Action action = condition ? realAction : Noop.of("no-op");
 * }</pre>
 *
 * <h3>Comparison with Direct</h3>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Noop</th>
 *     <th>Direct</th>
 *   </tr>
 *   <tr>
 *     <td>Purpose</td>
 *     <td>Do nothing</td>
 *     <td>Execute custom logic</td>
 *   </tr>
 *   <tr>
 *     <td>Result</td>
 *     <td>Always PASS</td>
 *     <td>Depends on execution</td>
 *   </tr>
 *   <tr>
 *     <td>Timing</td>
 *     <td>Always zero</td>
 *     <td>Actual execution time</td>
 *   </tr>
 *   <tr>
 *     <td>Performance</td>
 *     <td>Minimal overhead</td>
 *     <td>Lambda invocation overhead</td>
 *   </tr>
 * </table>
 *
 * <h3>Why Noop Instead of Empty Direct?</h3>
 * <p>While you could create an empty Direct action ({@code Direct.of("name", ctx -> {})}),
 * Noop is preferred because:
 * <ul>
 *   <li>Intent is explicit (no-op vs empty lambda)</li>
 *   <li>Better performance (no lambda invocation)</li>
 *   <li>Self-documenting code</li>
 *   <li>No custom executor or lambda required</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Noop actions are stateless in behavior but inherit mutable result state from
 * {@link org.paramixel.core.action.AbstractAction}. Use separate instances for separate
 * executions. Create instances with {@link #of(String)} when you need a no-op action
 * in an action tree.</p>
 *
 * @see Direct
 * @see Sequential
 * @see org.paramixel.core.Action
 */
public final class Noop extends AbstractAction {

    /**
     * Creates a named no-op action.
     *
     * <p>Callers should normally use {@link #of(String)}.</p>
     *
     * @param name the action name
     */
    private Noop(String name) {
        super();
        this.name = validateName(name);
    }

    /**
     * Creates a no-op action with the specified name.
     *
     * <p>Use this factory method when the action name is important for debugging,
     * logging, or understanding execution traces. When the name does not matter, callers
     * can still use {@code "noop"} and create a fresh instance with {@code Noop.of("noop")}.</p>
     *
     * <p><strong>Parameter Validation:</strong></p>
     * <ul>
     *   <li>Name must not be {@code null} or blank</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Action placeholder = Noop.of("step2-future-implementation");
     * Action stub = Noop.of("TBD");
     * }</pre>
     *
     * <p><strong>Naming Recommendations:</strong></p>
     * <ul>
     *   <li>Use descriptive names that explain why it's a no-op</li>
     *   <li>Include "placeholder" or "TODO" for future implementations</li>
     *   <li>Match naming conventions of sibling actions</li>
     * </ul>
     *
     * @param name the action name; must not be {@code null} or blank
     * @return a new no-op action; never {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static Noop of(String name) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Noop instance = new Noop(name);
        instance.initialize();
        return instance;
    }

    /**
     * Executes this no-op action.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Do nothing (no business logic)</li>
     *   <li>Set result to PASS with zero duration</li>
     *   <li>Invoke listener's afterAction callback</li>
     * </ol>
     *
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *   <li>Result is always PASS</li>
     *   <li>Elapsed time is always zero</li>
     *   <li>Listener callbacks are still invoked (useful for monitoring)</li>
     *   <li>Never throws exceptions</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong></p>
     * <p>Each Noop instance should be executed at most once. Use separate instances for
     * parallel or repeated execution.</p>
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
        this.result = Result.pass(durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
