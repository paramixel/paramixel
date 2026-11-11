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

package nonapi.org.paramixel.action;

import java.util.List;
import java.util.Objects;
import nonapi.org.paramixel.exception.UserCodeException;
import nonapi.org.paramixel.support.UnrecoverableErrors;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

/**
 * Engine-side status computation: mapping throwables to terminal statuses and aggregating child
 * statuses.
 *
 * <p>These helpers were part of the public {@link Status} type before 7.0.0. They carry engine
 * side effects (rethrowing unrecoverable errors, restoring the interrupt flag) and are not part of
 * the supported API.
 */
public final class Statuses {

    private Statuses() {
        // Intentionally empty
    }

    /**
     * Computes the aggregate status from a list of child descriptors.
     *
     * <p>Severity ordering: {@code FAILED} &gt; {@code ABORTED} &gt; {@code RUNNING}/{@code PENDING} &gt;
     * {@code SKIPPED} &gt; {@code PASSED}. Non-terminal child statuses (RUNNING or PENDING) cause the
     * aggregate to be {@link Status#RUNNING}, indicating that execution is incomplete.
     *
     * @param descriptors the child descriptors; must not be {@code null}
     * @return the aggregate status
     */
    public static Status aggregate(final List<Descriptor> descriptors) {
        boolean hasFailed = false;
        boolean hasAborted = false;
        boolean hasNonTerminal = false;
        boolean hasSkipped = false;
        for (Descriptor descriptor : descriptors) {
            switch (descriptor.status().kind()) {
                case FAILED -> hasFailed = true;
                case ABORTED -> hasAborted = true;
                case PENDING, RUNNING -> hasNonTerminal = true;
                case SKIPPED -> hasSkipped = true;
                case PASSED -> {
                    // A passed child does not change the aggregate.
                }
            }
        }
        if (hasFailed) {
            return Status.FAILED;
        }
        if (hasAborted) {
            return Status.ABORTED;
        }
        if (hasNonTerminal) {
            return Status.RUNNING;
        }
        if (hasSkipped) {
            return Status.SKIPPED;
        }
        return Status.PASSED;
    }

    /**
     * Maps a throwable to the corresponding terminal status.
     *
     * <p>If the throwable is a framework wrapper, its cause is unwrapped first.
     * Then {@link AbortException}, {@link SkipException}, and {@link FailException}
     * are mapped to their corresponding statuses. Unrecoverable errors are rethrown.
     * {@link InterruptedException} restores the interrupt flag.
     *
     * @param throwable the throwable to map; must not be {@code null}
     * @return the terminal status; never {@code null}
     * @throws Error if the throwable is an unrecoverable error
     */
    public static Status fromThrowable(final Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable is null");
        var t = throwable;
        if (t instanceof UserCodeException userCodeException) {
            t = userCodeException.getCause();
            if (t == null) {
                t = throwable;
            }
        }
        UnrecoverableErrors.rethrowIfUnrecoverable(t);
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof AbortException e) {
            return Status.aborted(e.getMessage(), e);
        }
        if (t instanceof SkipException e) {
            return Status.skipped(e.getMessage(), e);
        }
        if (t instanceof FailException e) {
            return Status.failed(e.getMessage(), e);
        }
        return Status.failed(t.getMessage() != null ? t.getMessage() : "failed", t);
    }
}
