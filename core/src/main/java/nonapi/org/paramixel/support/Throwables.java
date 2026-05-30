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

package nonapi.org.paramixel.support;

import java.util.concurrent.CompletionException;

/**
 * Static utilities for throwable normalization.
 *
 * <p>Normalizes throwable chains produced by the JDK and executors — where user-code failures
 * are frequently wrapped in {@link CompletionException} or
 * {@link RuntimeException} — to expose the semantically meaningful underlying cause.
 *
 * <p>Peeling rules:
 * <ul>
 * <li>{@link CompletionException} is recursively unwrapped when it carries a
 *     non-null cause.
 * <li>{@link RuntimeException} is unwrapped when its cause is an unrecoverable {@link Error},
 *     an {@link Error} subclass (other than {@link StackOverflowError}, which is recoverable),
 *     or an {@link InterruptedException}. This preserves the interrupt semantics of cancellation
 *     paths while avoiding false peels of ordinary user-thrown runtime exceptions.
 * </ul>
 */
public final class Throwables {

    private Throwables() {
        // Intentionally empty
    }

    /**
     * Normalizes a throwable by peeling wrapper layers produced by the JDK and executor
     * frameworks, exposing the semantically meaningful underlying cause.
     *
     * @param throwable the throwable to normalize, or {@code null}
     * @return the normalized throwable, or {@code null} if input was {@code null}
     */
    public static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return unwrap(throwable.getCause());
        }
        if (throwable instanceof RuntimeException rt && rt.getCause() != null) {
            var cause = rt.getCause();
            if (UnrecoverableErrors.isUnrecoverable(cause)
                    || cause instanceof Error
                    || cause instanceof InterruptedException) {
                return cause;
            }
        }
        return throwable;
    }
}
