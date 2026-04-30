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

import java.time.Duration;
import java.util.Objects;
import org.paramixel.core.Result;
import org.paramixel.core.Status;

/**
 * The default implementation of {@link Result}.
 */
public final class DefaultResult implements Result {

    private final Status status;
    private final Duration timing;

    private DefaultResult(Status status, Duration timing) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.timing = Objects.requireNonNull(timing, "timing must not be null");
    }

    /**
     * Creates a result.
     *
     * @param status The status.
     * @param timing The timing.
     * @return A new DefaultResult.
     */
    public static DefaultResult of(Status status, Duration timing) {
        return new DefaultResult(status, timing);
    }

    /**
     * Creates a staged result.
     *
     * @return A new DefaultResult with STAGED status and zero duration.
     */
    public static DefaultResult staged() {
        return new DefaultResult(DefaultStatus.staged(), Duration.ZERO);
    }

    /**
     * Creates a passing result.
     *
     * @param timing The timing.
     * @return A new DefaultResult.
     */
    public static DefaultResult pass(Duration timing) {
        return new DefaultResult(DefaultStatus.pass(), timing);
    }

    /**
     * Creates a failing result.
     *
     * @param timing The timing.
     * @param failure The failure.
     * @return A new DefaultResult.
     */
    public static DefaultResult fail(Duration timing, Throwable failure) {
        return new DefaultResult(DefaultStatus.failure(failure), timing);
    }

    /**
     * Creates a failing result with a message.
     *
     * @param timing The timing.
     * @param failureMessage The failure message.
     * @return A new DefaultResult.
     */
    public static DefaultResult fail(Duration timing, String failureMessage) {
        return new DefaultResult(DefaultStatus.failure(failureMessage), timing);
    }

    /**
     * Creates a skipped result.
     *
     * @param timing The timing.
     * @return A new DefaultResult.
     */
    public static DefaultResult skip(Duration timing) {
        return new DefaultResult(DefaultStatus.skip(), timing);
    }

    /**
     * Creates a skipped result with a reason.
     *
     * @param timing The timing.
     * @param skipReason The reason for skipping.
     * @return A new DefaultResult.
     */
    public static DefaultResult skip(Duration timing, String skipReason) {
        return new DefaultResult(DefaultStatus.skip(skipReason), timing);
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public Duration getElapsedTime() {
        return timing;
    }

    @Override
    public String toString() {
        return status + " | " + timing.toMillis() + " ms";
    }
}
