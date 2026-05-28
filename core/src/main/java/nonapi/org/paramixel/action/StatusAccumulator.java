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

import java.util.Objects;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;

/**
 * Incrementally computes an aggregate descriptor status.
 *
 * <p>Aggregation precedence matches {@link Status#aggregate(java.util.List)}:
 * FAILED &gt; ABORTED &gt; RUNNING/PENDING &gt; SKIPPED &gt; PASSED.
 *
 * <p>This class is not thread-safe.
 */
public final class StatusAccumulator {

    private boolean hasFailed;
    private boolean hasAborted;
    private boolean hasNonTerminal;
    private boolean hasSkipped;

    /**
     * Creates an empty status accumulator.
     *
     * <p>Before any inputs are included, {{@link #status()}} returns {@link Status#PASSED}.
     */
    public StatusAccumulator() {
        // Intentionally empty
    }

    /**
     * Includes a descriptor's current status in the aggregate.
     *
     * @param descriptor descriptor to include; must not be {@code null}
     */
    public void include(final Descriptor descriptor) {
        var nonNullDescriptor = Objects.requireNonNull(descriptor, "descriptor is null");
        var metadata = Objects.requireNonNull(nonNullDescriptor.metadata(), "descriptor metadata is null");
        var status = Objects.requireNonNull(metadata.status(), "descriptor status is null");
        include(status);
    }

    /**
     * Includes a status in the aggregate.
     *
     * @param status status to include; must not be {@code null}
     */
    public void include(final Status status) {
        var current = Objects.requireNonNull(status, "status is null");
        if (current.isFailed()) {
            hasFailed = true;
        }
        if (current.isAborted()) {
            hasAborted = true;
        }
        if (!current.isTerminal()) {
            hasNonTerminal = true;
        }
        if (current.isSkipped()) {
            hasSkipped = true;
        }
    }

    /**
     * Returns the aggregate status for all included inputs.
     *
     * @return aggregate status
     */
    public Status status() {
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
}
