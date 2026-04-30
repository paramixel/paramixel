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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that executes child actions sequentially in random order and stops on the first failure.
 *
 * <p>StrictRandomSequential combines the randomization of {@link RandomSequential}
 * with the fast-fail behavior of {@link StrictSequential}. Children execute one at a
 * time in randomized order, but execution stops immediately when any child fails.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Children execute in random order</li>
 *   <li>Stops on first failure (fast-fail pattern)</li>
 *   <li>Remaining siblings are skipped (not executed)</li>
 *   <li>Optional seed for reproducible ordering</li>
 *   <li>Status derived from child results (FAIL if any failed)</li>
 * </ul>
 *
 * <h3>Randomization + Fast-Fail</h3>
 * <p>The execution order is randomized using {@link java.util.Collections#shuffle}:
 * <ul>
 *   <li>Without seed: order is non-deterministic</li>
 *   <li>With seed: order is reproducible</li>
 *   <li>Execution stops on first failure</li>
 *   <li>Remaining (shuffled) siblings are skipped</li>
 * </ul>
 *
 * <h3>Status Derivation</h3>
 * <ul>
 *   <li>If any executed child failed → parent is FAIL</li>
 *   <li>Else if any executed child skipped → parent is SKIP</li>
 *   <li>Else (all passed) → parent is PASS</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Randomized Validation:</strong></p>
 * <pre>{@code
 * Action validation = StrictRandomSequential.of("validate",
 *     Direct.of("check1", this::check1),
 *     Direct.of("check2", this::check2),
 *     Direct.of("check3", this::check3)
 * );
 * // Checks execute in random order, stop on first failure
 * }</pre>
 *
 * <p><strong>Reproducible Random Order:</strong></p>
 * <pre>{@code
 * Action tests = StrictRandomSequential.of("tests", 42L,
 *     Direct.of("test1", this::test1),
 *     Direct.of("test2", this::test2),
 *     Direct.of("test3", this::test3)
 * );
 * // Same order every time, stop on first failure
 * }</pre>
 *
 * <h3>Comparison</h3>
 * <table border="1">
 *   <tr>
 *     <th>Action Type</th>
 *     <th>Order</th>
 *     <th>On Failure</th>
 *   </tr>
 *   <tr>
 *     <td>Sequential</td>
 *     <td>Registration</td>
 *     <td>Continue</td>
 *   </tr>
 *   <tr>
 *     <td>RandomSequential</td>
 *     <td>Random</td>
 *     <td>Continue</td>
 *   </tr>
 *   <tr>
 *     <td>StrictSequential</td>
 *     <td>Registration</td>
 *     <td>Stop</td>
 *   </tr>
 *   <tr>
 *     <td>StrictRandomSequential</td>
 *     <td>Random</td>
 *     <td>Stop</td>
 *   </tr>
 * </table>
 *
 * @see RandomSequential
 * @see StrictSequential
 */
public class StrictRandomSequential extends AbstractAction {

    private final List<Action> children;
    private final OptionalLong seed;

    /**
     * Creates a strict random sequential action with optional deterministic seeding.
     *
     * @param name the action name
     * @param children the child actions to execute
     * @param seed the optional shuffle seed
     */
    protected StrictRandomSequential(String name, List<Action> children, OptionalLong seed) {
        super();
        this.name = validateName(name);
        this.children = validateChildren(children);
        this.seed = seed;
    }

    /**
     * Creates a strict random sequential action without a seed.
     *
     * <p>The execution order will be different on each run and stops on the first failure.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new strict random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictRandomSequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictRandomSequential instance = new StrictRandomSequential(name, children, OptionalLong.empty());
        instance.initialize();
        return instance;
    }

    /**
     * Creates a strict random sequential action from varargs children without a seed.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new strict random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictRandomSequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictRandomSequential instance = new StrictRandomSequential(name, List.of(children), OptionalLong.empty());
        instance.initialize();
        return instance;
    }

    /**
     * Creates a seeded strict random sequential action.
     *
     * <p>The execution order will be reproducible with the given seed and stops on the first failure.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param seed the seed for the random number generator; any long value is valid
     * @param children the child actions; must not be {@code null} or empty
     * @return a new seeded strict random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictRandomSequential of(String name, long seed, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictRandomSequential instance = new StrictRandomSequential(name, children, OptionalLong.of(seed));
        instance.initialize();
        return instance;
    }

    /**
     * Creates a seeded strict random sequential action from varargs children.
     *
     * @param name the action name; must not be {@code null} or blank
     * @param seed the seed for the random number generator; any long value is valid
     * @param children the child actions; must not be {@code null} or empty
     * @return a new seeded strict random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     */
    public static StrictRandomSequential of(String name, long seed, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        StrictRandomSequential instance = new StrictRandomSequential(name, List.of(children), OptionalLong.of(seed));
        instance.initialize();
        return instance;
    }

    /**
     * Returns the child actions executed by this strict random sequential action.
     *
     * <p>The returned list is unmodifiable and preserves the registration order established
     * at construction time. Randomization happens per execution, not in the stored tree.</p>
     *
     * @return the child actions in registration order; never {@code null}
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    /**
     * Returns the seed if provided.
     *
     * @return an {@link OptionalLong} containing the seed, or empty if not seeded
     */
    public OptionalLong seed() {
        return seed;
    }

    /**
     * Executes children in random order, stopping on the first failure.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Shuffle children list</li>
     *   <li>Execute each child in shuffled order</li>
     *   <li>If child fails: skip all remaining shuffled siblings</li>
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

        List<Action> shuffled = new ArrayList<>(children);
        Random random = seed.isPresent() ? new Random(seed.getAsLong()) : new Random();
        Collections.shuffle(shuffled, random);

        for (int index = 0; index < shuffled.size(); index++) {
            Action child = shuffled.get(index);
            child.execute(context.createChild());
            if (child.getResult().getStatus().isFailure()) {
                for (Action remaining : shuffled.subList(index + 1, shuffled.size())) {
                    remaining.skip(context.createChild());
                }
                break;
            }
        }

        this.result = Result.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
