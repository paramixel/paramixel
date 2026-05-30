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

package nonapi.org.paramixel;

import java.util.Objects;

/**
 * Internal exception wrapper used to propagate user-code failures through the framework.
 *
 * <p>Framework execution paths wrap thrown user exceptions in this type so
 * {@code Status.fromThrowable(...)} can reliably unwrap once and classify status
 * semantics from the underlying cause.
 */
public final class FrameworkException extends Exception {

    /**
     * The underlying cause of this framework exception.
     */
    private final Throwable cause;

    /**
     * Creates a new framework exception wrapping the given cause.
     *
     * @param cause the underlying cause; must not be {@code null}
     * @throws NullPointerException if {@code cause} is {@code null}
     */
    public FrameworkException(final Throwable cause) {
        super(Objects.requireNonNull(cause, "cause is null").getMessage());
        this.cause = cause;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }

    /**
     * Wraps a throwable in a {@link FrameworkException}.
     *
     * <p>If the throwable is already a {@link FrameworkException}, it is returned unchanged.
     * If the throwable is a {@link RuntimeException} with a cause, the runtime cause is used as
     * the wrapped value to preserve the original user exception.
     *
     * @param throwable the throwable to wrap; may be {@code null}
     * @return a framework wrapper, or {@code null} if input was {@code null}
     */
    public static Throwable wrap(final Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof FrameworkException) {
            return throwable;
        }
        if (throwable instanceof RuntimeException re && re.getCause() != null) {
            return new FrameworkException(re.getCause());
        }
        return new FrameworkException(throwable);
    }
}
