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
import org.paramixel.core.Status;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that manages before, main, and after phases.
 *
 * <p>Lifecycle implements the common pattern of setup (before), execution (main), and
 * cleanup (after) phases. It guarantees that the after phase always runs, even if the
 * before or main phases fail.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Three-phase execution: before → main → after</li>
 *   <li>After phase always runs (cleanup guarantee)</li>
 *   <li>Phases share a common context for attachment passing</li>
 *   <li>Propagates status according to phase outcomes</li>
 *   <li>Skips descendants appropriately when phases fail</li>
 * </ul>
 *
 * <h3>Phase Execution Order</h3>
 * <ol>
 *   <li><strong>Before:</strong> Setup phase (e.g., acquire resources, initialize state)</li>
 *   <li><strong>Main:</strong> Core execution (only if before passed)</li>
 *   <li><strong>After:</strong> Cleanup phase (always runs, even on failure)</li>
 * </ol>
 *
 * <h3>Status Propagation Rules</h3>
 * <table border="1">
 *   <tr>
 *     <th>Before</th>
 *     <th>Main</th>
 *     <th>After</th>
 *     <th>Final Status</th>
 *   </tr>
 *   <tr>
 *     <td>PASS</td>
 *     <td>PASS</td>
 *     <td>PASS</td>
 *     <td>PASS</td>
 *   </tr>
 *   <tr>
 *     <td>PASS</td>
 *     <td>PASS</td>
 *     <td>FAIL</td>
 *     <td>FAIL</td>
 *   </tr>
 *   <tr>
 *     <td>PASS</td>
 *     <td>FAIL</td>
 *     <td>any</td>
 *     <td>FAIL</td>
 *   </tr>
 *   <tr>
 *     <td>PASS</td>
 *     <td>SKIP</td>
 *     <td>any</td>
 *     <td>SKIP</td>
 *   </tr>
 *   <tr>
 *     <td>FAIL</td>
 *     <td>skipped</td>
 *     <td>any</td>
 *     <td>FAIL</td>
 *   </tr>
 *   <tr>
 *     <td>SKIP</td>
 *     <td>skipped</td>
 *     <td>any</td>
 *     <td>SKIP</td>
 *   </tr>
 * </table>
 *
 * <h3>Context Sharing</h3>
 * <p>The before and after phases share a common context, allowing the before phase
 * to set attachments that the after phase can access:</p>
 * <pre>{@code
 * Lifecycle.of("databaseTest",
 *     Direct.of("setup", ctx -> {
 *         Connection conn = createConnection();
 *         ctx.setAttachment(conn);
 *     }),
 *     Direct.of("test", ctx -> {
 *         Connection conn = ctx.findAttachment(1).flatMap(a -> a.to(Connection.class)).orElseThrow();
 *         // Run tests with connection
 *     }),
 *     Direct.of("cleanup", ctx -> {
 *         Connection conn = ctx.getAttachment().flatMap(a -> a.to(Connection.class)).orElseThrow();
 *         conn.close();
 *     })
 * );
 * }</pre>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Database Test Lifecycle:</strong></p>
 * <pre>{@code
 * Action test = Lifecycle.of("userCrudTest",
 *     Direct.of("setup", ctx -> {
 *         Database db = Database.create();
 *         db.insert(testUser);
 *         ctx.setAttachment(db);
 *     }),
 *     Direct.of("test", ctx -> {
 *         Database db = ctx.getAttachment().flatMap(a -> a.to(Database.class)).orElseThrow();
 *         User found = db.findUser(testUser.getId());
 *         assertNotNull(found);
 *     }),
 *     Direct.of("teardown", ctx -> {
 *         Database db = ctx.getAttachment().flatMap(a -> a.to(Database.class)).orElseThrow();
 *         db.delete(testUser);
 *         db.close();
 *     })
 * );
 * }</pre>
 *
 * <p><strong>Resource Management:</strong></p>
 * <pre>{@code
 * Action action = Lifecycle.of("fileProcessing",
 *     Direct.of("open", ctx -> {
 *         InputStream stream = new FileInputStream("data.txt");
 *         ctx.setAttachment(stream);
 *     }),
 *     Direct.of("process", ctx -> {
 *         InputStream stream = ctx.getAttachment().flatMap(a -> a.to(InputStream.class)).orElseThrow();
 *         // Process file
 *     }),
 *     Direct.of("close", ctx -> {
 *         InputStream stream = ctx.getAttachment().flatMap(a -> a.to(InputStream.class)).orElseThrow();
 *         stream.close();
 *     })
 * );
 * }</pre>
 *
 * <h3>Comparison with Sequential</h3>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Lifecycle</th>
 *     <th>Sequential</th>
 *   </tr>
 *   <tr>
 *     <td>Phase Semantics</td>
 *     <td>Explicit before/main/after</td>
 *     <td>Generic ordered execution</td>
 *   </tr>
 *   <tr>
 *     <td>Context Sharing</td>
 *     <td>Before and after share context</td>
 *     <td>Each child has own context</td>
 *   </tr>
 *   <tr>
 *     <td>Cleanup Guarantee</td>
 *     <td>After always runs</td>
 *     <td>Stops on failure</td>
 *   </tr>
 *   <tr>
 *     <td>Use Case</td>
 *     <td>Setup/teardown patterns</td>
 *     <td>General sequential execution</td>
 *   </tr>
 * </table>
 *
 * <h3>Error Handling</h3>
 * <p>When the main phase fails:
 * <ul>
 *   <li>Main phase result is FAIL</li>
 *   <li>After phase still runs</li>
 *   <li>Final result is FAIL (from main)</li>
 *   <li>After failure message only used if main had no message</li>
 * </ul>
 *
 * <p>When the before phase fails:
 * <ul>
 *   <li>Main phase is skipped (along with its descendants)</li>
 *   <li>After phase still runs</li>
 *   <li>Final result is FAIL (from before)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Lifecycle is thread-safe for parallel execution. Each phase execution receives
 * its own child context. The shared context between before and after phases is
 * isolated per execution.</p>
 *
 * @see Sequential
 * @see Direct
 * @see Context#setAttachment(Object)
 * @see Context#findAttachment(int)
 */
public class Lifecycle extends AbstractAction {

    private final Action before;
    private final Action main;
    private final Action after;
    private final List<Action> actions;

    /**
     * Creates a lifecycle action with explicit before, main, and after phases.
     *
     * <p>Callers should normally use {@link #of(String, Action, Action, Action)} so the
     * instance is validated and fully initialized before use.</p>
     *
     * @param name the lifecycle action name
     * @param before the action executed before the main phase
     * @param main the primary action
     * @param after the action executed after the main phase
     */
    protected Lifecycle(String name, Action before, Action main, Action after) {
        super();
        this.name = validateName(name);
        this.before = before;
        this.main = main;
        this.after = after;
        this.actions = validateChildren(List.of(this.before, this.main, this.after));
    }

    /**
     * Creates a lifecycle action with before, main, and after phases.
     *
     * <p>All three phases are required and must not be {@code null}. Use
     * {@link Noop#of(String)} for phases that don't need implementation.</p>
     *
     * <p><strong>Parameter Validation:</strong></p>
     * <ul>
     *   <li>Name must not be {@code null} or blank</li>
     *   <li>Before phase must not be {@code null}</li>
     *   <li>Main phase must not be {@code null}</li>
     *   <li>After phase must not be {@code null}</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Lifecycle action = Lifecycle.of("test",
     *     Direct.of("setup", this::setup),
     *     Direct.of("test", this::runTest),
     *     Direct.of("teardown", this::teardown)
     * );
     * }</pre>
     *
     * <p><strong>With No-op Phases:</strong></p>
     * <pre>{@code
     * Lifecycle action = Lifecycle.of("simple",
     *     Noop.of("noop"),          // No setup
     *     Direct.of("test", this::runTest),
     *     Direct.of("cleanup", this::cleanup)
     * );
     * }</pre>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param before the action to run before the main phase; must not be {@code null}
     * @param main the main action; must not be {@code null}
     * @param after the action to run after the main phase; must not be {@code null}
     * @return a new lifecycle action; never {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     * @see Noop#of(String)
     */
    public static Lifecycle of(String name, Action before, Action main, Action after) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(before, "before must not be null");
        Objects.requireNonNull(main, "main must not be null");
        Objects.requireNonNull(after, "after must not be null");
        Lifecycle instance = new Lifecycle(name, before, main, after);
        instance.initialize();
        return instance;
    }

    /**
     * Returns the lifecycle phases in execution order: before, main, then after.
     *
     * <p>The returned list is unmodifiable and contains exactly three actions in the
     * order they are executed.</p>
     *
     * @return the lifecycle child actions; never {@code null}, always contains exactly 3 actions
     */
    @Override
    public List<Action> getChildren() {
        return actions;
    }

    /**
     * Executes the lifecycle phases in order with proper error handling.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Execute before phase</li>
     *   <li>If before passed: execute main phase</li>
     *   <li>If before failed/skipped: skip main and its descendants</li>
     *   <li>Execute after phase (always)</li>
     *   <li>Compute final status from phase results</li>
     *   <li>Invoke listener's afterAction callback</li>
     * </ol>
     *
     * <p><strong>Status Computation:</strong></p>
     * <ul>
     *   <li>If before failed → FAIL (before's message/throwable)</li>
     *   <li>If before skipped → SKIP (before's message)</li>
     *   <li>If main failed → FAIL (main's message/throwable)</li>
     *   <li>If main skipped → SKIP (main's message)</li>
     *   <li>If after failed → FAIL (main's message if present, else after's message)</li>
     *   <li>Otherwise → PASS</li>
     * </ul>
     *
     * <p><strong>Context Behavior:</strong></p>
     * <ul>
     *   <li>Before and after phases share a common context</li>
     *   <li>Main phase receives a child context of the shared context</li>
     *   <li>Attachments set in before are accessible in main (via findAttachment) and after</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong></p>
     * <p>This method is thread-safe and can be called concurrently from multiple threads.</p>
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

        Status computedStatus = Status.pass();

        Context lifecycleContext = context.createChild();
        before.execute(lifecycleContext);
        if (before.getResult().getStatus().isSkip()) {
            computedStatus =
                    Status.skip(before.getResult().getStatus().getMessage().orElse(null));
        } else if (before.getResult().getStatus().isFailure()) {
            computedStatus =
                    Status.failure(before.getResult().getStatus().getMessage().orElse(null));
        }

        if (computedStatus.isPass()) {
            main.execute(lifecycleContext.createChild());
            if (main.getResult().getStatus().isFailure()) {
                String mainFailureMessage =
                        main.getResult().getStatus().getMessage().orElse(null);
                computedStatus = Status.failure(mainFailureMessage);
            } else if (main.getResult().getStatus().isSkip()) {
                computedStatus =
                        Status.skip(main.getResult().getStatus().getMessage().orElse(null));
            }
        } else {
            skipWithDescendants(main, lifecycleContext.createChild());
        }

        after.execute(lifecycleContext);
        if (after.getResult().getStatus().isFailure()) {
            String afterFailureMessage =
                    after.getResult().getStatus().getMessage().orElse(null);
            if (!computedStatus.isFailure() || computedStatus.getMessage().isEmpty()) {
                computedStatus = Status.failure(afterFailureMessage);
            }
        }

        this.result = Result.of(computedStatus, durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }

    /**
     * Recursively skips an action and all its descendants.
     *
     * <p>This method is used when the before phase fails or skips, ensuring that
     * the main phase and all its descendants are properly marked as skipped.</p>
     *
     * @param action the action to skip (along with descendants)
     * @param context the execution context
     */
    private static void skipWithDescendants(Action action, Context context) {
        for (Action child : action.getChildren()) {
            skipWithDescendants(child, context);
        }
        action.skip(context);
    }
}
