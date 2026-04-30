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

import java.util.Optional;

/**
 * A wrapper view of an attachment stored on a {@link Context}.
 *
 * <p>Attachment provides type-safe access to values stored on contexts via
 * {@link Context#setAttachment(Object)}. Instead of working with raw {@code Object}
 * values, Attachment wraps the value and provides type inspection and safe casting.</p>
 *
 * <h3>Key Characteristics</h3>
 * <ul>
 *   <li>Type-safe casting via {@link #to(Class)}</li>
 *   <li>Runtime type inspection via {@link #getType()}</li>
 *   <li>Null-safe operations (handles null values)</li>
 *   <li>Immutable wrapper (attachment value cannot be modified)</li>
 *   <li>Optional-based API for safe absence handling</li>
 * </ul>
 *
 * <h3>Attachment Lifecycle</h3>
 * <ol>
 *   <li>Set via {@code ctx.setAttachment(value)}</li>
 *   <li>Retrieved via {@code ctx.getAttachment()} (returns Optional&lt;Attachment&gt;)</li>
 *   <li>Access value via {@code attachment.get().to(MyType.class)}</li>
 *   <li>Removed via {@code ctx.removeAttachment()}</li>
 * </ol>
 *
 * <h3>Type Safety</h3>
 * <p>Attachment uses runtime type checking to ensure type safety:</p>
 * <pre>{@code
 * // Set attachment
 * ctx.setAttachment(dataSource);
 *
 * // Retrieve and cast
 * Optional<Attachment> attachment = ctx.getAttachment();
 * Optional<DataSource> ds = attachment.flatMap(a -> a.to(DataSource.class));
 *
 * // Wrong type throws ClassCastException
 * Optional<String> wrong = attachment.flatMap(a -> a.to(String.class));
 * // Throws: ClassCastException: DataSource cannot be cast to String
 * }</pre>
 *
 * <h3>Null Handling</h3>
 * <p>Attachments can be null. When null is set:
 * <ul>
 *   <li>{@code ctx.setAttachment(null)} is valid</li>
 *   <li>{@code ctx.getAttachment()} returns empty Optional</li>
 *   <li>Null values are treated as "no attachment"</li>
 * </ul>
 *
 * <h3>Why Attachment Interface?</h3>
 * <p>Without Attachment, you would need to handle raw Objects:
 * <pre>{@code
 * // Without Attachment
 * Object value = ctx.getAttachmentRaw();
 * String str = (String) value; // Unsafe cast
 * }</pre>
 *
 * <p>With Attachment, you get type safety:
 * <pre>{@code
 * // With Attachment
 * Optional<String> str = ctx.getAttachment()
 *     .flatMap(a -> a.to(String.class));
 * }</pre>
 *
 * <h3>Usage Examples</h3>
 * <p><strong>Setting and Retrieving:</strong></p>
 * <pre>{@code
 * // Set attachment
 * Connection conn = createConnection();
 * ctx.setAttachment(conn);
 *
 * // Retrieve with type safety
 * Optional<Connection> connection = ctx.getAttachment()
 *     .flatMap(a -> a.to(Connection.class));
 *
 * // Use with Optional orElseThrow
 * Connection db = connection.orElseThrow(() ->
 *     new IllegalStateException("No connection attached"));
 * }</pre>
 *
 * <p><strong>Pattern Matching (Java 16+):</strong></p>
 * <pre>{@code
 * ctx.getAttachment().ifPresent(attachment -> {
 *     if (attachment.to(Connection.class).isPresent()) {
 *         Connection conn = attachment.to(Connection.class).get();
 *         // Use connection
 *     } else if (attachment.to(Session.class).isPresent()) {
 *         Session session = attachment.to(Session.class).get();
 *         // Use session
 *     }
 * });
 * }</pre>
 *
 * <p><strong>Type Inspection:</strong></p>
 * <pre>{@code
 * ctx.getAttachment().ifPresent(attachment -> {
 *     Class<?> type = attachment.getType();
 *     System.out.println("Attachment type: " + type.getName());
 * });
 * }</pre>
 *
 * <p><strong>Ancestor Attachment Access:</strong></p>
 * <pre>{@code
 * // Access parent's attachment
 * Optional<Attachment> parentAttachment = ctx.findAttachment(1);
 * Optional<Database> db = parentAttachment.flatMap(a -> a.to(Database.class));
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Attachment instances are immutable and thread-safe. However, the wrapped
 * value may not be thread-safe. Synchronize access to non-thread-safe attachment
 * values if accessed from multiple threads.</p>
 *
 * <h3>Generic Type Erasure</h3>
 * <p>Due to Java's type erasure, runtime type checking uses the actual object's class.
 * Generic type information is not preserved at runtime:</p>
 * <pre>{@code
 * List<String> strings = new ArrayList<>();
 * ctx.setAttachment(strings);
 *
 * // This works because type is ArrayList, not List<String>
 * Optional<ArrayList> list = ctx.getAttachment().flatMap(a -> a.to(ArrayList.class));
 * }</pre>
 *
 * @see Context#setAttachment(Object)
 * @see Context#getAttachment()
 * @see Context#findAttachment(int)
 */
public interface Attachment {

    /**
     * Returns the runtime type of the attachment value.
     *
     * <p>This method returns the actual class of the wrapped value. The return type
     * is {@code Class<?>} (wildcard) because the specific type is not known
     * at compile time.</p>
     *
     * <p><strong>Null Handling:</strong></p>
     * <ul>
     *   <li>If the attachment value is {@code null}, returns {@code null}</li>
     *   <li>Otherwise returns the value's {@link Object#getClass()}</li>
     * </ul>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Type inspection and debugging</li>
     *   <li>Conditional logic based on attachment type</li>
     *   <li>Logging and monitoring</li>
     *   <li>Type validation before casting</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * ctx.getAttachment().ifPresent(attachment -> {
     *     Class<?> type = attachment.getType();
     *     if (type == String.class) {
     *         System.out.println("String attachment");
     *     } else if (type == Connection.class) {
     *         System.out.println("Database connection");
     *     } else {
     *         System.out.println("Unknown type: " + type.getName());
     *     }
     * });
     * }</pre>
     *
     * <p><strong>Type Erasure Note:</strong></p>
     * <p>For generic types, the runtime type is the raw class, not the parameterized type:
     * <pre>{@code
     * List<String> strings = new ArrayList<>();
     * ctx.setAttachment(strings);
     * // Returns ArrayList.class, not List<String>.class
     * Class<?> type = ctx.getAttachment().flatMap(a -> a.getType()).orElse(null);
     * }</pre>
     *
     * @return the class of the attachment value, or {@code null} if the attachment value is {@code null}
     * @see Object#getClass()
     * @see #to(Class)
     */
    Class<?> getType();

    /**
     * Attempts to cast the attachment to the requested type.
     *
     * <p>This method provides type-safe casting of the attachment value. It checks
     * the runtime type and returns the value if it matches, otherwise throws
     * {@link ClassCastException}.</p>
     *
     * <p><strong>Casting Behavior:</strong></p>
     * <ul>
     *   <li>If attachment value is {@code null} → returns empty Optional</li>
     *   <li>If attachment value is instance of requested type → returns Optional with value</li>
     *   <li>If attachment value is not instance of requested type → throws ClassCastException</li>
     * </ul>
     *
     * <p><strong>Allowed Casts:</strong></p>
     * <ul>
     *   <li>Exact type match: {@code String} → {@code String}</li>
     *   <li>Upcast: {@code ArrayList} → {@code List}</li>
     *   <li>Interface: {@code ArrayList} → {@code Collection}</li>
     *   <li>Primitive wrappers: {@code Integer} → {@code Number}</li>
     * </ul>
     *
     * <p><strong>Prohibited Casts:</strong></p>
     * <ul>
     *   <li>Downcast without check: {@code Object} → {@code String} (fails if not String)</li>
     *   <li>Incompatible types: {@code String} → {@code Integer} (throws ClassCastException)</li>
     *   <li>Primitive types: Cannot cast to {@code int}, use {@code Integer}</li>
     * </ul>
     *
     * <p><strong>Usage Patterns:</strong></p>
     *
     * <p><strong>1. Safe Retrieval:</strong></p>
     * <pre>{@code
     * Optional<Connection> conn = ctx.getAttachment()
     *     .flatMap(a -> a.to(Connection.class));
     * }</pre>
     *
     * <p><strong>2. With Default Value:</strong></p>
     * <pre>{@code
     * Connection conn = ctx.getAttachment()
     *     .flatMap(a -> a.to(Connection.class))
     *     .orElse(defaultConnection);
     * }</pre>
     *
     * <p><strong>3. Throw on Absence:</strong></p>
     * <pre>{@code
     * Connection conn = ctx.getAttachment()
     *     .flatMap(a -> a.to(Connection.class))
     *     .orElseThrow(() -> new IllegalStateException("No connection"));
     * }</pre>
     *
     * <p><strong>4. Conditional Logic:</strong></p>
     * <pre>{@code
     * if (ctx.getAttachment().flatMap(a -> a.to(Connection.class)).isPresent()) {
     *     // We have a connection
     * } else if (ctx.getAttachment().flatMap(a -> a.to(Session.class)).isPresent()) {
     *     // We have a session
     * }
     * }</pre>
     *
     * <p><strong>5. Multiple Possible Types:</strong></p>
     * <pre>{@code
     * ctx.getAttachment().ifPresent(attachment -> {
     *     if (attachment.to(Connection.class).isPresent()) {
     *         Connection conn = attachment.to(Connection.class).get();
     *         // Use connection
     *     } else if (attachment.to(Session.class).isPresent()) {
     *         Session session = attachment.to(Session.class).get();
     *         // Use session
     *     } else {
     *         throw new IllegalStateException("Unexpected attachment type");
     *     }
     * });
     * }</pre>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>If the cast fails, a {@link ClassCastException} is thrown:
     * <pre>{@code
     * try {
     *     String str = ctx.getAttachment()
     *         .flatMap(a -> a.to(String.class))
     *         .orElseThrow();
     * } catch (ClassCastException e) {
     *     System.err.println("Attachment is not a String: " + e.getMessage());
     * }
     * }</pre>
     *
     * @param <T> the requested type
     * @param type the class token for the requested type; must not be {@code null}
     * @return an {@link Optional} containing the attachment if present and matches the requested type,
     *         or empty if the attachment is null or the type doesn't match
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws ClassCastException if the attachment is present but cannot be cast to the requested type
     * @see #getType()
     * @see Class#isInstance(Object)
     */
    <T> Optional<T> to(Class<T> type);
}
