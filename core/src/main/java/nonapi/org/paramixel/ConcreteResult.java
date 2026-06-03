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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;
import org.paramixel.api.Status;

/**
 * Concrete implementation of {@link Result} that applies configuration-based
 * status promotion to the root descriptor's already-aggregated status.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ConcreteResult implements Result {

    private final Descriptor root;
    private final Configuration configuration;
    private final Status statusOverride;

    /**
     * Creates a result for a run that produced a descriptor tree.
     *
     * @param root the root descriptor; may be {@code null} when no descriptor was discovered
     * @param configuration the run configuration; must not be {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public ConcreteResult(final Descriptor root, final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.root = root;
        this.statusOverride = null;
    }

    /**
     * Creates a result for a run that discovered no action.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public ConcreteResult(final Configuration configuration) {
        this(null, configuration);
    }

    /**
     * Creates a result with an explicit status override for when no descriptor is available.
     *
     * @param configuration the run configuration; must not be {@code null}
     * @param statusOverride the status to return; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    ConcreteResult(final Configuration configuration, final Status statusOverride) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");
        this.statusOverride = Objects.requireNonNull(statusOverride, "statusOverride is null");
        this.root = null;
    }

    @Override
    public Optional<Descriptor> descriptor() {
        return Optional.ofNullable(root);
    }

    @Override
    public boolean isPassed() {
        return status().isPassed();
    }

    @Override
    public boolean isFailed() {
        return status().isFailed();
    }

    @Override
    public boolean isSkipped() {
        return status().isSkipped();
    }

    @Override
    public boolean isAborted() {
        return status().isAborted();
    }

    @Override
    public Optional<Instant> startedAt() {
        return root == null ? Optional.empty() : earliestStartedAt(root);
    }

    @Override
    public Optional<Instant> completedAt() {
        return root == null ? Optional.empty() : latestCompletedAt(root);
    }

    /**
     * Returns the effective internal status for framework code.
     *
     * @return the effective status; never {@code null}
     */
    public Status status() {
        if (statusOverride != null) {
            return statusOverride;
        }
        if (root == null) {
            final var failIfNoTests =
                    configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(false);
            return failIfNoTests ? Status.FAILED : Status.SKIPPED;
        }
        return promote(statusOf(root));
    }

    private Status promote(final Status status) {
        if (status.isSkipped()) {
            final var failureOnSkip =
                    configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(false);
            if (failureOnSkip) {
                return Status.FAILED;
            }
        }
        if (status.isAborted()) {
            final var failureOnAbort =
                    configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true);
            if (failureOnAbort) {
                return Status.FAILED;
            }
        }
        return status;
    }

    private static Status statusOf(final Descriptor descriptor) {
        if (descriptor instanceof MutableDescriptor mutableDescriptor) {
            return mutableDescriptor.status();
        }
        if (descriptor.isFailed()) {
            return Status.FAILED;
        }
        if (descriptor.isAborted()) {
            return Status.ABORTED;
        }
        if (!descriptor.isCompleted()) {
            return Status.RUNNING;
        }
        if (descriptor.isSkipped()) {
            return Status.SKIPPED;
        }
        return Status.PASSED;
    }

    private static Optional<Instant> earliestStartedAt(final Descriptor descriptor) {
        var earliest = descriptor.startedAt();
        for (Descriptor child : structuralChildren(descriptor)) {
            earliest = min(earliest, earliestStartedAt(child));
        }
        return earliest;
    }

    private static Optional<Instant> latestCompletedAt(final Descriptor descriptor) {
        var latest = descriptor.completedAt();
        for (Descriptor child : structuralChildren(descriptor)) {
            latest = max(latest, latestCompletedAt(child));
        }
        return latest;
    }

    private static Iterable<Descriptor> structuralChildren(final Descriptor descriptor) {
        var children = new ArrayList<Descriptor>();
        descriptor.before().ifPresent(children::add);
        children.addAll(descriptor.children());
        descriptor.after().ifPresent(children::add);
        return children;
    }

    private static Optional<Instant> min(final Optional<Instant> left, final Optional<Instant> right) {
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left.orElseThrow().isAfter(right.orElseThrow()) ? right : left;
    }

    private static Optional<Instant> max(final Optional<Instant> left, final Optional<Instant> right) {
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left.orElseThrow().isBefore(right.orElseThrow()) ? right : left;
    }
}
