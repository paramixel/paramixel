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
import org.paramixel.core.internal.DefaultContext;
import org.paramixel.core.internal.Results;
import org.paramixel.core.internal.util.Arguments;

/**
 * A built-in action that executes child actions sequentially in random order.
 *
 * <p>RandomSequential executes all child actions one at a time, but in a randomized
 * order determined by a {@link java.util.Random} instance. This is useful for:
 * <ul>
 *   <li>Testing independence of test cases</li>
 *   <li>Discovering order-dependent bugs</li>
 *   <li>Ensuring test isolation</li>
 *   <li>Statistical testing scenarios</li>
 * </ul>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>All children execute (no early termination on failure)</li>
 *   <li>Execution order is randomized</li>
 *   <li>Optional seed for reproducible ordering</li>
 *   <li>Status derived from child results (FAIL if any failed)</li>
 *   <li>Timing includes all children sequentially</li>
 * </ul>
 *
 * <h3>Randomization</h3>
 * <p>The execution order is randomized using {@link java.util.Collections#shuffle}:
 * <ul>
 *   <li>Without seed: order is non-deterministic (different each execution)</li>
 *   <li>With seed: order is reproducible (same order for same seed)</li>
 *   <li>Random instance is created fresh each execution</li>
 * </ul>
 *
 * <h3>Status Derivation</h3>
 * <p>Parent status is derived from child results after all complete:</p>
 * <ul>
 *   <li>If any child failed → parent is FAIL</li>
 *   <li>Else if any child skipped → parent is SKIP</li>
 *   <li>Else (all passed) → parent is PASS</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Random Test Execution:</strong></p>
 * <pre>{@code
 * Action tests = RandomSequential.of("testSuite",
 *     Direct.of("test1", this::test1),
 *     Direct.of("test2", this::test2),
 *     Direct.of("test3", this::test3)
 * );
 * // Tests execute in random order each time
 * }</pre>
 *
 * <p><strong>Reproducible Random Order:</strong></p>
 * <pre>{@code
 * Action tests = RandomSequential.of("testSuite", 42L,
 *     Direct.of("test1", this::test1),
 *     Direct.of("test2", this::test2),
 *     Direct.of("test3", this::test3)
 * );
 * // Same order every time (seed=42)
 * }</pre>
 *
 * <p><strong>From List:</strong></p>
 * <pre>{@code
 * List<Action> testActions = loadTests();
 * Action suite = RandomSequential.of("allTests", testActions);
 * }</pre>
 *
 * <h3>Comparison with Sequential</h3>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>RandomSequential</th>
 *     <th>Sequential</th>
 *   </tr>
 *   <tr>
 *     <td>Execution Order</td>
 *     <td>Randomized</td>
 *     <td>Registration order</td>
 *   </tr>
 *   <tr>
 *     <td>Reproducibility</td>
 *     <td>Optional via seed</td>
 *     <td>Always reproducible</td>
 *   </tr>
 *   <tr>
 *     <td>Use Case</td>
 *     <td>Testing independence</td>
 *     <td>Ordered workflows</td>
 *   </tr>
 *   <tr>
 *     <td>Early Termination</td>
 *     <td>No</td>
 *     <td>No</td>
 *   </tr>
 * </table>
 *
 * <h3>When to Use</h3>
 * <ul>
 *   <li><strong>Testing:</strong> Ensure tests don't depend on execution order</li>
 *   <li><strong>Debugging:</strong> Discover order-dependent bugs by randomizing</li>
 *   <li><strong>Reproducibility:</strong> Use seeded randomization for CI</li>
 *   <li><strong>Statistical Testing:</strong> Run tests in random order for coverage</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>RandomSequential is thread-safe for parallel execution. The Random instance
 * is created per execution and is not shared. Each child action executes in its
 * own child context.</p>
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li>Shuffling overhead: O(n) where n is number of children</li>
 *   <li>Execution: Sequential (same total time as Sequential)</li>
 *   <li>Random instance creation: Minimal overhead</li>
 * </ul>
 *
 * @see Sequential
 * @see StrictRandomSequential
 * @see java.util.Collections#shuffle
 * @see java.util.Random
 */
public class RandomSequential extends AbstractAction {

    private final List<Action> children;
    private final OptionalLong seed;

    private RandomSequential(String name, List<Action> children, OptionalLong seed) {
        super(name);
        this.children = validateChildren(children);
        this.seed = seed;
    }

    /**
     * Creates a random sequential action without a seed.
     *
     * <p>The execution order will be different on each run because the randomization
     * is not seeded. This is useful for testing independence and discovering
     * order-dependent bugs.</p>
     *
     * <p><strong>Parameter Validation:</strong></p>
     * <ul>
     *   <li>Name must not be {@code null} or blank</li>
     *   <li>Children must not be {@code null} or empty</li>
     * </ul>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     * @see #of(String, long, List)
     * @see #of(String, Action...)
     */
    public static RandomSequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        return new RandomSequential(name, children, OptionalLong.empty());
    }

    /**
     * Creates a random sequential action from varargs children without a seed.
     *
     * <p>Convenient method for creating actions with a variable number of children.
     * The execution order will be different on each run.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param children the child actions; must not be {@code null} or empty
     * @return a new random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     * @see #of(String, List)
     * @see #of(String, long, Action...)
     */
    public static RandomSequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        return new RandomSequential(name, List.of(children), OptionalLong.empty());
    }

    /**
     * Creates a seeded random sequential action.
     *
     * <p>The execution order will be reproducible because the randomization is seeded.
     * The same seed value will always produce the same execution order. This is
     * useful for CI/CD pipelines where reproducibility is important.</p>
     *
     * <p><strong>Parameter Validation:</strong></p>
     * <ul>
     *   <li>Name must not be {@code null} or blank</li>
     *   <li>Seed can be any long value (positive, negative, or zero)</li>
     *   <li>Children must not be {@code null} or empty</li>
     * </ul>
     *
     * <p><strong>Seed Guidelines:</strong></p>
     * <ul>
     *   <li>Use fixed seeds for reproducible CI runs</li>
     *   <li>Use timestamp seeds for random but reproducible runs</li>
     *   <li>Avoid seed 0 unless intentional</li>
     * </ul>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param seed the seed for the random number generator; any long value is valid
     * @param children the child actions; must not be {@code null} or empty
     * @return a new seeded random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     * @see #of(String, List)
     * @see #seed()
     */
    public static RandomSequential of(String name, long seed, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        return new RandomSequential(name, children, OptionalLong.of(seed));
    }

    /**
     * Creates a seeded random sequential action from varargs children.
     *
     * <p>Convenient method for creating actions with a variable number of children.
     * The execution order will be reproducible with the given seed.</p>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param seed the seed for the random number generator; any long value is valid
     * @param children the child actions; must not be {@code null} or empty
     * @return a new seeded random sequential action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code children} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or {@code children} is empty
     * @see #of(String, long, List)
     * @see #of(String, Action...)
     */
    public static RandomSequential of(String name, long seed, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        return new RandomSequential(name, List.of(children), OptionalLong.of(seed));
    }

    /**
     * Returns the child actions executed by this random sequential action.
     *
     * <p>The returned list is unmodifiable and reflects the registration order, not the
     * execution order. The execution order is randomized at runtime.</p>
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
     * <p>The seed determines the randomization behavior:
     * <ul>
     *   <li>Empty Optional - Non-deterministic order (different each execution)</li>
     *   <li>Present Optional - Deterministic order (same order for same seed)</li>
     * </ul>
     *
     * @return an {@link OptionalLong} containing the seed, or empty if not seeded
     * @see #of(String, long, List)
     * @see #of(String, long, Action...)
     */
    public OptionalLong seed() {
        return seed;
    }

    /**
     * Executes children sequentially in random order.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Create shuffled copy of children list</li>
     *   <li>Create Random instance (seeded or unseeded)</li>
     *   <li>Execute each child in shuffled order</li>
     *   <li>Compute status from child results</li>
     *   <li>Invoke listener's afterAction callback</li>
     * </ol>
     *
     * <p><strong>Randomization Details:</strong></p>
     * <ul>
     *   <li>Uses {@link java.util.Collections#shuffle} with Random instance</li>
     *   <li>If seeded: same order for same seed</li>
     *   <li>If unseeded: order varies each execution</li>
     *   <li>Random instance is created fresh per execution</li>
     * </ul>
     *
     * <p><strong>Status Computation:</strong></p>
     * <ul>
     *   <li>If any child failed → FAIL</li>
     *   <li>Else if any child skipped → SKIP</li>
     *   <li>Else → PASS</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong></p>
     * <p>This method is thread-safe. The Random instance is created per execution
     * and is not shared across threads.</p>
     *
     * @param context the execution context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     */
    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Results.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();

        List<Action> shuffled = new ArrayList<>(children);
        Random random = seed.isPresent() ? new Random(seed.getAsLong()) : new Random();
        Collections.shuffle(shuffled, random);

        DefaultContext defaultContext = (DefaultContext) context;
        for (Action child : shuffled) {
            child.execute(new DefaultContext(defaultContext));
        }

        this.result = Results.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
