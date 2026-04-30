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
import org.paramixel.core.FailException;
import org.paramixel.core.Result;
import org.paramixel.core.SkipException;
import org.paramixel.core.support.Arguments;

/**
 * A built-in action that executes a provided block directly.
 *
 * <p>Direct is the simplest form of action that executes custom logic provided as a
 * lambda expression or method reference. It is ideal for inline action definitions
 * where creating a separate Action subclass would be unnecessary.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Executes exactly one provided block of code</li>
 *   <li>Has no child actions (leaf action)</li>
 *   <li>Captures and handles exceptions appropriately</li>
 *   <li>Supports lambda expressions and method references</li>
 *   <li>Thread-safe for parallel execution</li>
 * </ul>
 *
 * <h3>Exception Handling</h3>
 * <p>Direct handles exceptions thrown by the executable block:</p>
 * <ul>
 *   <li>{@link SkipException} - Result is SKIP with exception message</li>
 *   <li>{@link FailException} - Result is FAIL with exception message</li>
 *   <li>Other exceptions - Result is FAIL, listener's actionThrowable callback is invoked</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Simple Lambda:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("greet", ctx -> {
 *     System.out.println("Hello, World!");
 * });
 * }</pre>
 *
 * <p><strong>Method Reference:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("test", this::runTest);
 *
 * private void runTest(Context ctx) {
 *     // Test logic
 * }
 * }</pre>
 *
 * <p><strong>With Context Access:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("databaseTest", ctx -> {
 *     String url = ctx.getConfiguration().get("db.url");
 *     Connection conn = DriverManager.getConnection(url);
 *     ctx.setAttachment(conn);
 *     // Test logic using connection
 * });
 * }</pre>
 *
 * <p><strong>With FailException:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("validation", ctx -> {
 *     User user = getUser();
 *     if (user == null) {
 *         FailException.fail("User not found");
 *     }
 *     if (!user.isActive()) {
 *         FailException.fail("User is inactive");
 *     }
 * });
 * }</pre>
 *
 * <p><strong>With SkipException:</strong></p>
 * <pre>{@code
 * Action action = Direct.of("conditionalTest", ctx -> {
 *     if (!isFeatureEnabled()) {
 *         SkipException.skip("Feature disabled");
 *     }
 *     // Test logic
 * });
 * }</pre>
 *
 * <h3>Comparison with Other Actions</h3>
 * <table border="1">
 *   <tr>
 *     <th>Action Type</th>
 *     <th>Use Case</th>
 *     <th>Has Children</th>
 *   </tr>
 *   <tr>
 *     <td>Direct</td>
 *     <td>Inline custom logic</td>
 *     <td>No</td>
 *   </tr>
 *   <tr>
 *     <td>Noop</td>
 *     <td>Placeholder, testing</td>
 *     <td>No</td>
 *   </tr>
 *   <tr>
 *     <td>Sequential</td>
 *     <td>Ordered execution</td>
 *     <td>Yes</td>
 *   </tr>
 *   <tr>
 *     <td>Parallel</td>
 *     <td>Concurrent execution</td>
 *     <td>Yes</td>
 *   </tr>
 * </table>
 *
 * <h3>Thread Safety</h3>
 * <p>Direct actions are thread-safe when used with parallel execution. Each execution
 * receives its own {@link Context} instance. However, the executable block must be
 * thread-safe if it accesses shared state.</p>
 *
 * <h3>Performance</h3>
 * <p>Direct has minimal overhead:
 * <ul>
 *   <li>Result capture and timing</li>
 *   <li>Listener notifications</li>
 *   <li>Exception handling</li>
 * </ul>
 * For very performance-critical code, consider that lambda execution adds a small
 * indirection cost compared to direct method calls.</p>
 *
 * @see Executable
 * @see Noop
 * @see Sequential
 * @see FailException
 * @see SkipException
 * @see Context
 */
public class Direct extends AbstractAction {

    protected final Executable executable;

    /**
     * Creates a direct action backed by the supplied executable callback.
     *
     * <p>Callers should normally use {@link #of(String, Executable)} so validation and
     * initialization occur before the instance is published.</p>
     *
     * @param name the action name
     * @param executable the callback invoked during execution
     */
    protected Direct(String name, Executable executable) {
        super();
        this.name = validateName(name);
        this.executable = executable;
    }

    /**
     * Creates a direct action with the specified name and executable block.
     *
     * <p>This is the primary factory method for creating Direct actions. The executable
     * is invoked exactly once when the action executes.</p>
     *
     * <p><strong>Parameter Validation:</strong></p>
     * <ul>
     *   <li>Name must not be {@code null} or blank</li>
     *   <li>Executable must not be {@code null}</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Action action = Direct.of("myAction", ctx -> {
     *     // Your logic here
     *     String config = ctx.getConfiguration().get("key");
     *     System.out.println(config);
     * });
     * }</pre>
     *
     * @param name the action name; must not be {@code null} or blank
     * @param executable the executable block to run; must not be {@code null}
     * @return a new direct action; never {@code null}
     * @throws NullPointerException if {@code name} or {@code executable} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     * @see Executable
     */
    public static Direct of(String name, Executable executable) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(executable, "executable must not be null");
        Direct instance = new Direct(name, executable);
        instance.initialize();
        return instance;
    }

    /**
     * Executes this action by invoking the provided executable block.
     *
     * <p><strong>Execution Flow:</strong></p>
     * <ol>
     *   <li>Set result to STAGED</li>
     *   <li>Invoke listener's beforeAction callback</li>
     *   <li>Record start time</li>
     *   <li>Invoke the executable block</li>
     *   <li>Handle any exceptions:</li>
     *     <ul>
     *       <li>{@link SkipException} → result is SKIP</li>
     *       <li>{@link FailException} → result is FAIL</li>
     *       <li>Other exceptions → result is FAIL, call listener's actionThrowable callback</li>
     *     </ul>
     *   <li>If no exception → result is PASS</li>
     *   <li>Invoke listener's afterAction callback</li>
     * </ol>
     *
     * <p><strong>Exception Behavior:</strong></p>
     * <ul>
     *   <li>All exceptions are caught and converted to appropriate result status</li>
     *   <li>Only unexpected exceptions trigger listener's actionThrowable callback</li>
     *   <li>Exceptions do not propagate to parent actions</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong></p>
     * <p>This method is thread-safe and can be called concurrently from multiple threads.
     * Each invocation receives its own context instance.</p>
     *
     * @param context the execution context; must not be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     * @see Executable#execute(Context)
     * @see FailException
     * @see SkipException
     */
    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Result.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();
        try {
            executable.execute(context);
            this.result = Result.pass(durationSince(start));
        } catch (SkipException e) {
            this.result = Result.skip(durationSince(start), e.getMessage());
        } catch (FailException e) {
            this.result = Result.fail(durationSince(start), e.getMessage());
        } catch (Throwable t) {
            context.getListener().actionThrowable(context, this, t);
            this.result = Result.fail(durationSince(start), t);
        }

        context.getListener().afterAction(context, this, this.result);
    }

    /**
     * Functional interface for the execution callback.
     *
     * <p>This interface defines the contract for executable blocks that can be run
     * by {@link Direct} actions. It is a {@link FunctionalInterface} and can be
     * implemented using lambda expressions or method references.</p>
     *
     * <h3>Contract</h3>
     * <ul>
     *   <li>The method receives a {@link Context} providing runtime state</li>
     *   <li>May throw any exception, which will be handled by Direct</li>
     *   <li>Should complete quickly or use the executor service for long operations</li>
     *   <li>Should be thread-safe if used in parallel execution</li>
     * </ul>
     *
     * <h3>Exception Handling</h3>
     * <p>Exceptions are handled by Direct as follows:</p>
     * <ul>
     *   <li>{@link SkipException} - Action is marked SKIPPED</li>
     *   <li>{@link FailException} - Action is marked FAILED</li>
     *   <li>Other exceptions - Action is marked FAILED, stack trace logged</li>
     * </ul>
     *
     * <h3>Usage Examples</h3>
     * <p><strong>Lambda Expression:</strong></p>
     * <pre>{@code
     * Executable exec = ctx -> {
     *     System.out.println("Running");
     * };
     * }</pre>
     *
     * <p><strong>Method Reference:</strong></p>
     * <pre>{@code
     * Executable exec = this::myMethod;
     *
     * private void myMethod(Context ctx) {
     *     // Logic
     * }
     * }</pre>
     *
     * <p><strong>With Control Flow:</strong></p>
     * <pre>{@code
     * Executable exec = ctx -> {
     *     if (!precondition()) {
     *         SkipException.skip("Precondition failed");
     *     }
     *     if (error()) {
     *         FailException.fail("Error occurred");
     *     }
     *     // Success path
     * };
     * }</pre>
     *
     * @see Direct
     * @see Context
     * @see FailException
     * @see SkipException
     */
    @FunctionalInterface
    public interface Executable {

        /**
         * Executes this action body.
         *
         * <p>This method is called by {@link Direct} during action execution. The
         * implementation should contain the action's business logic.</p>
         *
         * <p><strong>Context Usage:</strong></p>
         * <ul>
         *   <li>Access configuration via {@code ctx.getConfiguration()}</li>
         *   <li>Access parent attachments via {@code ctx.findAttachment(level)}</li>
         *   <li>Set child attachments via {@code ctx.setAttachment(value)}</li>
         *   <li>Use executor service for parallel work via {@code ctx.getExecutorService()}</li>
         *   <li>Invoke listener callbacks via {@code ctx.getListener()}</li>
         * </ul>
         *
         * <p><strong>Exception Guidelines:</strong></p>
         * <ul>
         *   <li>Throw {@link SkipException} to mark action as skipped</li>
         *   <li>Throw {@link FailException} to mark action as failed</li>
         *   <li>Throw other exceptions for unexpected errors</li>
         *   <li>Avoid catching and suppressing exceptions unless intentional</li>
         * </ul>
         *
         * <p><strong>Thread Safety:</strong></p>
         * <p>If this method accesses shared state, it must be thread-safe. The
         * context is unique per execution but may be accessed from multiple threads
         * during parallel execution.</p>
         *
         * @param context the execution context providing runtime state; never {@code null}
         * @throws Throwable if execution fails (will be captured and handled by Direct)
         * @see Context
         * @see FailException
         * @see SkipException
         */
        void execute(Context context) throws Throwable;
    }
}
