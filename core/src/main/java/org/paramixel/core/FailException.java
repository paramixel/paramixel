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

/**
 * Signals that an action should be marked as failed.
 *
 * <p>FailException is a control-flow exception used to mark an action as FAILED
 * without propagating an error up the call stack. When thrown from an action's
 * execution, the framework catches it and sets the action's result to FAIL.</p>
 *
 * <h3>Behavior</h3>
 * <p>When a FailException is thrown:</p>
 * <ul>
 *   <li>The action's result is set to FAIL with the message from the exception</li>
 *   <li>The exception is <strong>not</strong> passed to {@link Listener#actionThrowable}</li>
 *   <li>The exception does <strong>not</strong> propagate to parent actions</li>
 *   <li>Execution continues with subsequent actions</li>
 * </ul>
 *
 * <h3>Usage Patterns</h3>
 * <p>There are several ways to mark an action as failed:</p>
 *
 * <p><strong>1. Static Method (Recommended):</strong></p>
 * <pre>{@code
 * Direct.of("validation", ctx -> {
 *     if (!isValid) {
 *         FailException.fail("validation failed: invalid input");
 *     }
 *     // Success path
 * });
 * }</pre>
 *
 * <p><strong>2. Static Method with Default Message:</strong></p>
 * <pre>{@code
 * Direct.of("check", ctx -> {
 *     if (error) {
 *         FailException.fail(); // Uses default message "failed"
 *     }
 * });
 * }</pre>
 *
 * <p><strong>3. Constructor:</strong></p>
 * <pre>{@code
 * Direct.of("custom", ctx -> {
 *     throw new FailException("custom failure reason");
 * });
 * }</pre>
 *
 * <h3>Comparison with Regular Exceptions</h3>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>FailException</th>
 *     <th>Regular Exception</th>
 *   </tr>
 *   <tr>
 *     <td>Result Status</td>
 *     <td>FAIL</td>
 *     <td>FAIL</td>
 *   </tr>
 *   <tr>
 *     <td>Listener Notification</td>
 *     <td>No actionThrowable callback</td>
 *     <td>actionThrowable callback invoked</td>
 *   </tr>
 *   <tr>
 *     <td>Stack Trace Capture</td>
 *     <td>Minimal (faster)</td>
 *     <td>Full stack trace</td>
 *   </tr>
 *   <tr>
 *     <td>Propagation</td>
 *     <td>Stopped at action boundary</td>
 *     <td>Stopped at action boundary</td>
 *   </tr>
 *   <tr>
 *     <td>Use Case</td>
 *     <td>Expected failures, validation errors</td>
 *     <td>Unexpected errors, assertions</td>
 *   </tr>
 * </table>
 *
 * <h3>Comparison with SkipException</h3>
 * <ul>
 *   <li>{@link FailException} - Marks action as FAILED (execution attempted but failed)</li>
 *   <li>{@link SkipException} - Marks action as SKIPPED (execution not attempted)</li>
 * </ul>
 *
 * <h3>Best Practices</h3>
 * <ul>
 *   <li>Use static methods ({@link #fail()} and {@link #fail(String)}) for readability</li>
 *   <li>Provide descriptive messages for debugging</li>
 *   <li>Use FailException for expected failures (validation, assertions)</li>
 *   <li>Use regular exceptions for unexpected errors (null pointers, I/O errors)</li>
 *   <li>Avoid throwing FailException from listener callbacks</li>
 * </ul>
 *
 * @see SkipException
 * @see Status#isFailure()
 * @see Result
 * @see Listener
 */
public class FailException extends RuntimeException {

    /**
     * Creates a failure signal with a detail message.
     *
     * <p>The message is included in the action's {@link Status} and is available
     * via {@link Status#getMessage()}. It is also displayed in console output
     * and summary reports.</p>
     *
     * <p><strong>Message Best Practices:</strong></p>
     * <ul>
     *   <li>Be specific about what failed and why</li>
     *   <li>Include relevant values or identifiers</li>
     *   <li>Keep messages concise but informative</li>
     *   <li>Use present tense (e.g., "validation failed" not "validation has failed")</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * throw new FailException("User validation failed: email is required");
     * }</pre>
     *
     * @param message the detail message; may be {@code null}, but a descriptive message is recommended
     */
    public FailException(final String message) {
        super(message);
    }

    /**
     * Throws a failure signal with a default message.
     *
     * <p>This method always throws a FailException with the default message "failed".
     * It is a convenient way to mark an action as failed without a custom message.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Direct.of("quickCheck", ctx -> {
     *     if (!condition) {
     *         FailException.fail(); // Throws with message "failed"
     *     }
     *     // Success path continues
     * });
     * }</pre>
     *
     * <p><strong>When to Use:</strong></p>
     * <ul>
     *   <li>Quick failure without needing a specific message</li>
     *   <li>Failure reason is obvious from action name</li>
     *   <li>Rapid prototyping or testing</li>
     * </ul>
     *
     * <p><strong>When Not to Use:</strong></p>
     * <ul>
     *   <li>Production code where failure context is important</li>
     *   <li>Actions that may fail for multiple reasons</li>
     *   <li>When debugging would benefit from specific messages</li>
     * </ul>
     *
     * <p>Consider using {@link #fail(String)} with a descriptive message for production code.</p>
     *
     * @throws FailException always thrown with message "failed"
     * @see #fail(String)
     */
    public static void fail() {
        throw new FailException("failed");
    }

    /**
     * Throws a failure signal with a detail message.
     *
     * <p>This method always throws a FailException with the specified message.
     * It is the recommended way to mark an action as failed with a custom reason.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>{@code
     * Direct.of("validateUser", ctx -> {
     *     User user = getUser();
     *     if (user == null) {
     *         FailException.fail("User not found");
     *     }
     *     if (user.getEmail() == null) {
     *         FailException.fail("User email is required");
     *     }
     *     if (!user.isActive()) {
     *         FailException.fail("User account is inactive");
     *     }
     *     // All validations passed
     * });
     * }</pre>
     *
     * <p><strong>Message Guidelines:</strong></p>
     * <ul>
     *   <li>Describe what failed, not just that it failed</li>
     *   <li>Include relevant context (ids, values, states)</li>
     *   <li>Be concise but complete</li>
     *   <li>Use consistent formatting across similar validations</li>
     * </ul>
     *
     * <p><strong>Example Messages:</strong></p>
     * <ul>
     *   <li>"Connection timeout after 30 seconds"</li>
     *   <li>"Invalid response: expected 200, got 404"</li>
     *   <li>"Database query returned 0 rows, expected at least 1"</li>
     *   <li>"User 'john.doe' lacks required permission: ADMIN"</li>
     * </ul>
     *
     * @param message the detail message explaining why the action failed; may be {@code null},
     *                but a descriptive message is strongly recommended
     * @throws FailException always thrown with the specified message
     * @see #fail()
     */
    public static void fail(final String message) {
        throw new FailException(message);
    }
}
