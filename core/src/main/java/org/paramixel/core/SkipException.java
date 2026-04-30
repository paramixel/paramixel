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

import org.paramixel.core.support.Arguments;

/**
 * Signals that an action should be marked as skipped.
 *
 * <p>SkipException is a control-flow exception used to mark an action as SKIPPED
 * without propagating an error up the call stack. When thrown from an action's
 * execution, the framework catches it and sets the action's result to SKIP.</p>
 *
 * <h3>Behavior</h3>
 * <p>When a SkipException is thrown:</p>
 * <ul>
 *   <li>The action's result is set to SKIP with the message from the exception</li>
 *   <li>The exception is <strong>not</strong> passed to {@link Listener#actionThrowable}</li>
 *   <li>The exception does <strong>not</strong> propagate to parent actions</li>
 *   <li>Execution continues with subsequent actions</li>
 *   <li>Skipped actions do not count as failures for parent status</li>
 * </ul>
 *
 * <h3>Usage Patterns</h3>
 * <p>There are several ways to mark an action as skipped:</p>
 *
 * <p><strong>1. Static Method (Recommended):</strong></p>
 * <pre>{@code
 * Direct.of("conditional", ctx -> {
 *     if (!shouldRun) {
 *         SkipException.skip("Test disabled by configuration");
 *     }
 *     // Test logic
 * });
 * }</pre>
 *
 * <p><strong>2. Static Method with Default Message:</strong></p>
 * <pre>{@code
 * Direct.of("optional", ctx -> {
 *     if (notApplicable) {
 *         SkipException.skip(); // Uses default message "skipped"
 *     }
 * });
 * }</pre>
 *
 * <p><strong>3. Constructor:</strong></p>
 * <pre>{@code
 * Direct.of("custom", ctx -> {
 *     SkipException.skip("precondition not met");
 * });
 * }</pre>
 *
 * <h3>When to Skip vs Fail</h3>
 * <table border="1">
 *   <tr>
 *     <th>Skip When...</th>
 *     <th>Fail When...</th>
 *   </tr>
 *   <tr>
 *     <td>Preconditions are not met</td>
 *     <td>Assertions fail</td>
 *   </tr>
 *   <tr>
 *     <td>Feature is disabled</td>
 *     <td>Expected behavior is broken</td>
 *   </tr>
 *   <tr>
 *     <td>Environment is not suitable</td>
 *     <td>Code throws unexpected exception</td>
 *   </tr>
 *   <tr>
 *     <td>Test is not applicable</td>
 *     <td>Validation fails</td>
 *   </tr>
 *   <tr>
 *     <td>Dependencies are unavailable</td>
 *     <td>Resource access fails</td>
 *   </tr>
 * </table>
 *
 * <h3>Comparison with FailException</h3>
 * <ul>
 *   <li>{@link SkipException} - Marks action as SKIPPED (execution not attempted or incomplete by design)</li>
 *   <li>{@link FailException} - Marks action as FAILED (execution attempted but failed)</li>
 * </ul>
 *
 * <h3>Impact on Parent Actions</h3>
 * <p>Skipped actions affect parent action status differently than failures:</p>
 * <ul>
 *   <li>If any child failed → parent is FAIL</li>
 *   <li>Else if any child skipped → parent is SKIP</li>
 *   <li>Else (all passed) → parent is PASS</li>
 * </ul>
 * <p>This means skipped children can cause parent actions to be marked as SKIP,
 * but not as FAIL.</p>
 *
 * <h3>Behavior in Strict Actions</h3>
 * <p>In strict action types (e.g., {@link org.paramixel.core.action.StrictSequential}),
 * remaining siblings are skipped when a failure occurs. These skips are automatic
 * and do not involve SkipException.</p>
 *
 * <h3>Best Practices</h3>
 * <ul>
 *   <li>Use static methods ({@link #skip()} and {@link #skip(String)}) for readability</li>
 *   <li>Provide descriptive messages explaining why the action was skipped</li>
 *   <li>Use SkipException for conditional execution (preconditions, feature flags)</li>
 *   <li>Use FailException for assertion failures and validation errors</li>
 *   <li>Avoid throwing SkipException from listener callbacks</li>
 *   <li>Consider logging skip reasons for audit purposes</li>
 * </ul>
 *
 * @see FailException
 * @see Status#isSkip()
 * @see Result
 * @see Listener
 * @see Action#skip(Context)
 */
public final class SkipException extends RuntimeException {

    /**
     * Creates a skip signal with a detail message.
     *
     * <p>The message is included in the action's {@link Status} and is available
     * via {@link Status#getMessage()}. It is also displayed in console output
     * and summary reports.</p>
     *
     * <p><strong>Message Best Practices:</strong></p>
     * <ul>
     *   <li>Explain why the action was skipped</li>
     *   <li>Include relevant conditions or state</li>
     *   <li>Keep messages concise but informative</li>
     *   <li>Use present tense (e.g., "feature disabled" not "feature was disabled")</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * throw new SkipException("Skipped: database not available in CI environment");
     * }</pre>
     *
     * @param message the detail message; may be {@code null}, but a descriptive message is recommended
     */
    private SkipException(final String message) {
        super(message);
    }

    /**
     * Creates a skip signal with a detail message.
     *
     * <p>The message is validated to be non-blank before creating the exception.
     * Use this factory method to construct a SkipException when you need to throw
     * it as a value rather than using the convenience {@link #skip()} or
     * {@link #skip(String)} methods.</p>
     *
     * @param message the detail message; must not be blank
     * @return a new SkipException with the specified message
     * @throws NullPointerException if {@code message} is null
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static SkipException of(String message) {
        return new SkipException(Arguments.requireNonBlank(message, "message must not be blank"));
    }

    /**
     * Throws a skip signal with a default message.
     *
     * <p>This method always throws a SkipException with the default message "skipped".
     * It is a convenient way to mark an action as skipped without a custom message.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Direct.of("optionalFeature", ctx -> {
     *     if (!featureEnabled) {
     *         SkipException.skip(); // Throws with message "skipped"
     *     }
     *     // Feature logic continues
     * });
     * }</pre>
     *
     * <p><strong>When to Use:</strong></p>
     * <ul>
     *   <li>Quick skip without needing a specific message</li>
     *   <li>Skip reason is obvious from action name</li>
     *   <li>Rapid prototyping or testing</li>
     * </ul>
     *
     * <p><strong>When Not to Use:</strong></p>
     * <ul>
     *   <li>Production code where skip context is important</li>
     *   <li>Actions that may be skipped for multiple reasons</li>
     *   <li>When debugging would benefit from specific messages</li>
     * </ul>
     *
     * <p>Consider using {@link #skip(String)} with a descriptive message for production code.</p>
     *
     * @throws SkipException always thrown with message "skipped"
     * @see #skip(String)
     */
    public static void skip() {
        throw new SkipException("skipped");
    }

    /**
     * Throws a skip signal with a detail message.
     *
     * <p>This method always throws a SkipException with the specified message.
     * It is the recommended way to mark an action as skipped with a custom reason.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Direct.of("databaseTest", ctx -> {
     *     String env = ctx.getConfiguration().get("environment");
     *     if ("CI".equals(env)) {
     *         SkipException.skip("Database tests skipped in CI environment");
     *     }
     *
     *     // Database test logic
     * });
     * }</pre>
     *
     * <p><strong>Message Guidelines:</strong></p>
     * <ul>
     *   <li>Explain why the action cannot or should not run</li>
     *   <li>Include relevant context (environment, configuration, state)</li>
     *   <li>Be concise but complete</li>
     *   <li>Use consistent formatting across similar conditions</li>
     * </ul>
     *
     * <p><strong>Common Skip Scenarios:</strong></p>
     * <ul>
     *   <li>"Skipped: feature flag 'EXPERIMENTAL_FEATURE' is disabled"</li>
     *   <li>"Skipped: database connection not available"</li>
     *   <li>"Skipped: test requires 'admin' role, current user has 'guest'"</li>
     *   <li>"Skipped: platform 'windows' not supported"</li>
     *   <li>"Skipped: precondition 'database populated' not met"</li>
     * </ul>
     *
     * @param message the detail message explaining why the action was skipped; may be {@code null},
     *                but a descriptive message is strongly recommended
     * @throws SkipException always thrown with the specified message
     * @see #skip()
     */
    public static void skip(final String message) {
        throw new SkipException(message);
    }
}
