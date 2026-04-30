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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Provides runtime state during action execution.
 *
 * <p>Context is available to all actions during execution and provides access to:
 * <ul>
 *   <li>Configuration properties ({@link #getConfiguration()})</li>
 *   <li>Execution listener ({@link #getListener()})</li>
 *   <li>Thread pool for parallel execution ({@link #getExecutorService()})</li>
 *   <li>Parent context for hierarchy navigation ({@link #getParent()})</li>
 *   <li>Attachment storage for scoped data ({@link #setAttachment(Object)})</li>
 * </ul>
 *
 * <p>Contexts form a tree structure mirroring the action tree. Each action receives its own
 * child context, allowing scoped state that is isolated from sibling actions. Parent contexts
 * are accessible via {@link #getParent()} or {@link #findContext(int)} for ancestor navigation.</p>
 *
 * <p><strong>Thread Safety:</strong> Contexts are thread-safe for use within a single action's
 * execution. However, attachments are not synchronized across threads. When using parallel
 * execution, each action receives its own child context, ensuring isolation. Do not share
 * context instances across threads manually.</p>
 *
 * <p><strong>Lifecycle:</strong> A new context is created for each action execution. Child
 * actions receive child contexts with their parent set to the parent action's context.
 * Contexts are not reused between executions.</p>
 *
 * <p><strong>Attachment Scoping:</strong> Attachments set on a parent context are accessible
 * to child contexts via {@link #findAttachment(int)}. This allows for dependency injection
 * pattern where parent actions set up resources that child actions consume.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * Direct.of("child", ctx -> {
 *     // Access configuration
 *     String value = ctx.getConfiguration().get("my.key");
 *
 *     // Access parent attachment
 *     Optional<Attachment> db = ctx.findAttachment(1);
 *
 *     // Set child-specific attachment
 *     ctx.setAttachment("childData");
 *
 *     // Execute parallel tasks
 *     ExecutorService executor = ctx.getExecutorService();
 *     executor.submit(() -> doWork());
 * });
 * }</pre>
 *
 * @see Action
 * @see Listener
 * @see Attachment
 * @see Configuration
 */
public interface Context {

    /**
     * Returns the parent context for nested execution.
     *
     * <p>The parent context is the context associated with this action's parent action.
     * Parent contexts provide access to ancestor state, attachments, and configuration.
     * The root context (context for the top-level action) has no parent.</p>
     *
     * <p>Parent contexts are useful for:
     * <ul>
     *   <li>Accessing ancestor attachments via {@link #findAttachment(int)}</li>
     *   <li>Navigating the context hierarchy for debugging</li>
     *   <li>Understanding execution scope</li>
     * </ul>
     *
     * @return an {@link Optional} containing the parent context, or empty for the root context
     * @see #findContext(int)
     */
    Optional<Context> getParent();

    /**
     * Returns the configuration properties for this execution.
     *
     * <p>Configuration properties are loaded from multiple sources with later sources
     * taking precedence:
     * <ol>
     *   <li>Default classpath properties file: {@code paramixel.properties}</li>
     *   <li>JVM system properties</li>
     *   <li>Runner-specific configuration (if provided)</li>
     * </ol>
     *
     * <p>The returned map is immutable and thread-safe. Modifications to the map are not
     * supported and will throw {@link UnsupportedOperationException}.</p>
     *
     * <p>Configuration properties are shared across all contexts in the same execution.
     * All actions see the same configuration values.</p>
     *
     * <p><strong>Common Properties:</strong></p>
     * <ul>
     *   <li>{@code paramixel.parallelism} - Thread pool size for parallel execution</li>
     * </ul>
     *
     * @return the configuration properties; never {@code null}, may be empty
     * @see Configuration
     * @see Configuration#RUNNER_PARALLELISM
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the listener that receives execution notifications.
     *
     * <p>The listener receives callbacks during action execution, including:
     * <ul>
     *   <li>{@link Listener#runStarted(Runner, Action)} - Before execution begins</li>
     *   <li>{@link Listener#beforeAction(Context, Action)} - Before each action executes</li>
     *   <li>{@link Listener#afterAction(Context, Action, Result)} - After each action completes</li>
     *   <li>{@link Listener#actionThrowable(Context, Action, Throwable)} - On unexpected exceptions</li>
     *   <li>{@link Listener#runCompleted(Runner, Action)} - After execution completes</li>
     * </ul>
     *
     * <p>Actions can invoke listener methods directly to trigger notifications at custom points.
     * The returned listener is typically wrapped in {@link org.paramixel.core.listener.SafeListener}
     * to prevent listener exceptions from interrupting execution.</p>
     *
     * <p>The listener is shared across all contexts in the same execution. All actions notify
     * the same listener instance.</p>
     *
     * @return the listener; never {@code null}
     * @see Listener
     * @see org.paramixel.core.listener.SafeListener
     */
    Listener getListener();

    /**
     * Returns the executor service for parallel execution.
     *
     * <p>The executor service is used by {@link org.paramixel.core.action.Parallel} and other
     * action types that support concurrent execution of child actions. Actions can also use
     * this executor directly for custom parallel work.</p>
     *
     * <p>The executor is typically managed by the {@link Runner}. The thread pool size is
     * controlled by the {@code paramixel.parallelism} configuration property, defaulting to
     * {@code Runtime.getRuntime().availableProcessors() * 2}.</p>
     *
     * <p><strong>Thread Safety:</strong> The executor service is thread-safe and can be used
     * concurrently from multiple actions. Submit tasks via {@link java.util.concurrent.ExecutorService#submit(Runnable)}
     * or {@link java.util.concurrent.ExecutorService#execute(Runnable)}.</p>
     *
     * <p><strong>Lifecycle:</strong> If the runner creates the executor (i.e., no custom executor
     * was provided to {@link Runner.Builder#executorService(ExecutorService)}), the runner
     * will shut it down after execution completes. If a custom executor is provided, its
     * lifecycle is managed by the caller.</p>
     *
     * @return the executor service; never {@code null}
     * @see org.paramixel.core.action.Parallel
     * @see Configuration#RUNNER_PARALLELISM
     * @see Runner.Builder#executorService(ExecutorService)
     */
    ExecutorService getExecutorService();

    /**
     * Finds this context or one of its ancestors by level.
     *
     * <p>The {@code level} parameter specifies how many levels up the context hierarchy
     * to navigate:
     * <ul>
     *   <li>{@code 0} - Returns this context</li>
     *   <li>{@code 1} - Returns the parent context</li>
     *   <li>{@code 2} - Returns the grandparent context</li>
     *   <li>{@code 3} - Returns the great-grandparent context</li>
     *   <li>etc.</li>
     * </ul>
     *
     * <p>This method is useful for navigating the context hierarchy to access ancestor state
     * or attachments. For accessing ancestor attachments, consider using {@link #findAttachment(int)}
     * which combines context navigation with attachment retrieval.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * // Access grandparent context
     * Optional<Context> grandparent = ctx.findContext(2);
     *
     * // Check if root context (no parent)
     * boolean isRoot = ctx.findContext(1).isEmpty();
     * }</pre>
     *
     * @param level the number of levels up the hierarchy; must be non-negative
     * @return an {@link Optional} containing the requested context
     * @throws IllegalArgumentException if {@code level} is negative
     * @throws java.util.NoSuchElementException if the ancestor at the given level does not exist
     * @see #getParent()
     * @see #findAttachment(int)
     */
    Optional<Context> findContext(int level);

    /**
     * Sets an attachment on this context, replacing any existing attachment.
     *
     * <p>Attachments provide a mechanism for storing typed data within a context's scope.
     * Each context can hold at most one attachment at a time. Setting a new attachment
     * replaces any existing attachment.</p>
     *
     * <p><strong>Scoping Rules:</strong></p>
     * <ul>
     *   <li>Attachments are scoped to their context</li>
     *   <li>Child contexts can access parent attachments via {@link #findAttachment(int)}</li>
     *   <li>Sibling contexts cannot access each other's attachments</li>
     *   <li>Parent contexts cannot access child attachments</li>
     * </ul>
     *
     * <p>Attachments support any type including {@code null}. When {@code null} is set,
     * {@link #getAttachment()} will return an empty {@link Optional}.</p>
     *
     * <p>This method returns {@code this} context for method chaining, enabling fluent
     * attachment setup:</p>
     * <pre>{@code
     * ctx.setAttachment(dataSource)
     *    .setAttachment(transaction)
     *    .setAttachment(config);
     * }</pre>
     *
     * <p><strong>Thread Safety:</strong> Attachment storage is not synchronized. In parallel
     * execution, each action receives its own context, ensuring isolation. Do not modify
     * attachments concurrently from multiple threads.</p>
     *
     * @param <T> the attachment type
     * @param attachment the attachment to set; may be {@code null}
     * @return this context for method chaining
     * @see #getAttachment()
     * @see #findAttachment(int)
     * @see Attachment
     */
    <T> Context setAttachment(T attachment);

    /**
     * Returns the current attachment wrapped as an {@link Attachment} view.
     *
     * <p>This method retrieves the attachment set on this context only. To access ancestor
     * attachments, use {@link #findAttachment(int)}.</p>
     *
     * <p>The returned {@link Attachment} provides type-safe access to the underlying value:
     * <ul>
     *   <li>{@link Attachment#getType()} - Returns the runtime type</li>
     *   <li>{@link Attachment#to(Class)} - Casts to a specific type</li>
     * </ul>
     *
     * <p>If no attachment is present, returns an empty {@link Optional}. This includes the
     * case where {@code null} was set via {@link #setAttachment(Object)}.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * ctx.setAttachment("myString");
     * Optional<Attachment> attachment = ctx.getAttachment();
     * if (attachment.isPresent()) {
     *     String value = attachment.get().to(String.class).orElseThrow();
     * }
     * }</pre>
     *
     * @return an {@link Optional} containing the attachment wrapper, or empty if no attachment is present
     * @see #setAttachment(Object)
     * @see #findAttachment(int)
     * @see Attachment
     */
    Optional<Attachment> getAttachment();

    /**
     * Removes and returns the current attachment wrapped as an {@link Attachment} view.
     *
     * <p>This method atomically removes the attachment and returns it in one operation.
     * After removal, {@link #getAttachment()} will return an empty {@link Optional}.</p>
     *
     * <p>Removing attachments is useful for implementing one-time resource consumption patterns
     * where you want to ensure an attachment is only accessed once:</p>
     * <pre>{@code
     * Optional<Attachment> resource = ctx.removeAttachment();
     * if (resource.isPresent()) {
     *     try {
     *         // Use the resource
     *         resource.get().to(Resource.class).ifPresent(this::consume);
     *     } finally {
     *         // Resource already removed, no cleanup needed
     *     }
     * }
     * }</pre>
     *
     * <p>If no attachment is present, returns an empty {@link Optional}. This includes the
     * case where {@code null} was set via {@link #setAttachment(Object)}.</p>
     *
     * @return an {@link Optional} containing the removed attachment wrapper, or empty if no attachment is present
     * @see #getAttachment()
     * @see #setAttachment(Object)
     */
    Optional<Attachment> removeAttachment();

    /**
     * Returns this context's attachment or an ancestor's attachment.
     *
     * <p>The {@code level} parameter specifies how many levels up the context hierarchy
     * to navigate to find an attachment:
     * <ul>
     *   <li>{@code 0} - Returns this context's attachment (same as {@link #getAttachment()})</li>
     *   <li>{@code 1} - Returns the parent context's attachment</li>
     *   <li>{@code 2} - Returns the grandparent context's attachment</li>
     *   <li>etc.</li>
     * </ul>
     *
     * <p>This method is useful for implementing dependency injection patterns where parent
     * actions set up resources that child actions consume. Child contexts can access parent
     * attachments without needing to know the exact ancestor level.</p>
     *
     * <p><strong>Shadowing:</strong> If a child context sets its own attachment, it shadows
     * the parent attachment for that context. Use {@code level > 0} to explicitly access
     * ancestor attachments when shadowing is in effect.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * // Parent sets up database connection
     * ctx.setAttachment(connection);
     *
     * // Child action accesses parent attachment
     * Optional<Attachment> db = ctx.findAttachment(1);
     * db.flatMap(a -> a.to(Connection.class)).ifPresent(conn -> {
     *     // Use the connection
     * });
     * }</pre>
     *
     * @param level the number of levels up the hierarchy; must be non-negative
     * @return an {@link Optional} containing the attachment wrapper, or empty if the requested context has no attachment
     * @throws IllegalArgumentException if {@code level} is negative
     * @throws java.util.NoSuchElementException if the ancestor at the given level does not exist
     * @see #findContext(int)
     * @see #getAttachment()
     * @see Attachment
     */
    Optional<Attachment> findAttachment(int level);
}
