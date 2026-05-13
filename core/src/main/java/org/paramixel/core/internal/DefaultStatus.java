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

package org.paramixel.core.internal;

import java.util.Objects;
import java.util.Optional;
import org.paramixel.core.Status;

/**
 * Represents the four standard Paramixel outcomes (staged, pass, failure, skip) with optional message and throwable detail.
 *
 * <p>This immutable implementation represents the four standard Paramixel outcomes and optionally carries a message or throwable for
 * reporting purposes.
 */
public final class DefaultStatus implements Status {

    /**
     * Enumerates the standard Paramixel status kinds.
     *
     * <p>Note: the enum constant names use the noun form (e.g. {@code FAILURE}) while the
     * corresponding {@link Status#getDisplayName()} values use a short verb form (e.g. {@code "FAIL"}).
     * This is an intentional design choice to keep report output concise. Aligning the two would
     * be a breaking API change.
     */
    public enum Kind {

        /**
         * Action has been staged but not yet executed. Display name: "STAGED".
         */
        STAGED,

        /**
         * Action completed successfully. Display name: "PASS".
         */
        PASS,

        /**
         * Action completed with a failure. Display name: "FAIL" (intentionally truncated for readability).
         */
        FAILURE,

        /**
         * Action was skipped. Display name: "SKIP".
         */
        SKIP
    }

    /**
     * Shared staged status instance without additional detail.
     */
    public static final DefaultStatus STAGED = new DefaultStatus(Kind.STAGED, null, null);

    /**
     * Shared passing status instance without additional detail.
     */
    public static final DefaultStatus PASS = new DefaultStatus(Kind.PASS, null, null);

    /**
     * Shared skipped status instance without additional detail.
     */
    public static final DefaultStatus SKIP = new DefaultStatus(Kind.SKIP, null, null);

    /**
     * Shared failure status instance without additional detail.
     */
    public static final DefaultStatus FAILURE = new DefaultStatus(Kind.FAILURE, null, null);

    private final Kind kind;
    private final String message;
    private final Throwable throwable;

    /**
     * Creates a status with the supplied kind and no message or throwable.
     *
     * @param kind the outcome category (staged, pass, failure, or skip)
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public DefaultStatus(Kind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        this.kind = kind;
        this.message = null;
        this.throwable = null;
    }

    /**
     * Creates a status with the supplied kind and message.
     *
     * @param kind the outcome category (staged, pass, failure, or skip)
     * @param message the associated message, or {@code null} when no message is needed
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public DefaultStatus(Kind kind, String message) {
        Objects.requireNonNull(kind, "kind must not be null");
        this.kind = kind;
        this.message = message;
        this.throwable = null;
    }

    /**
     * Creates a status with the supplied kind and throwable.
     *
     * @param kind the outcome category (staged, pass, failure, or skip)
     * @param throwable the associated throwable, or {@code null} when no throwable is needed
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public DefaultStatus(Kind kind, Throwable throwable) {
        Objects.requireNonNull(kind, "kind must not be null");
        this.kind = kind;
        this.message = null;
        this.throwable = throwable;
    }

    /**
     * Creates a status with the supplied kind, message, and throwable.
     *
     * @param kind the outcome category (staged, pass, failure, or skip)
     * @param message the associated message, or {@code null} when no message is needed
     * @param throwable the associated throwable, or {@code null} when no throwable is needed
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public DefaultStatus(Kind kind, String message, Throwable throwable) {
        Objects.requireNonNull(kind, "kind must not be null");
        this.kind = kind;
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Returns the underlying status kind.
     *
     * @return the status kind
     */
    public Kind kind() {
        return kind;
    }

    @Override
    public boolean isStaged() {
        return kind == Kind.STAGED;
    }

    @Override
    public boolean isPass() {
        return kind == Kind.PASS;
    }

    @Override
    public boolean isFailure() {
        return kind == Kind.FAILURE;
    }

    @Override
    public boolean isSkip() {
        return kind == Kind.SKIP;
    }

    @Override
    public String getDisplayName() {
        return switch (kind) {
            case STAGED -> "STAGED";
            case PASS -> "PASS";
            case FAILURE -> "FAIL";
            case SKIP -> "SKIP";
        };
    }

    @Override
    public Optional<String> getMessage() {
        if (message != null) {
            return Optional.of(message);
        }
        if (throwable != null) {
            return Optional.ofNullable(throwable.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Throwable> getThrowable() {
        return Optional.ofNullable(throwable);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DefaultStatus other)) {
            return false;
        }
        return kind == other.kind
                && Objects.equals(message, other.message)
                && Objects.equals(throwable, other.throwable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, message, throwable);
    }
}
