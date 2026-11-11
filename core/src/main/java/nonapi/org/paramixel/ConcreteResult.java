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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;
import org.paramixel.api.Status;

/**
 * Concrete implementation of {@link Result}.
 *
 * <p>The tree is terminal when a result is created, so the effective status, its exit code, and the
 * run's start and completion instants are computed once in the constructor and never recomputed.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ConcreteResult implements Result {

    private final Descriptor root;
    private final Status status;
    private final int exitCode;
    private final Instant startedAt;
    private final Instant completedAt;
    private final List<Pools.PoolStats> poolStats;

    /**
     * Creates a result for a run that produced a descriptor tree.
     *
     * @param root the root descriptor; may be {@code null} when no descriptor was discovered
     * @param configuration the run configuration; must not be {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public ConcreteResult(final Descriptor root, final Configuration configuration) {
        this(root, configuration, List.of());
    }

    /**
     * Creates a result for a run that produced a descriptor tree and pool statistics.
     *
     * @param root the root descriptor; may be {@code null} when no descriptor was discovered
     * @param configuration the run configuration; must not be {@code null}
     * @param poolStats the pool sizes and peak usage observed by the run; must not be {@code null}
     * @throws NullPointerException if {@code configuration} or {@code poolStats} is {@code null}
     */
    public ConcreteResult(
            final Descriptor root, final Configuration configuration, final List<Pools.PoolStats> poolStats) {
        Objects.requireNonNull(configuration, "configuration is null");
        this.poolStats = List.copyOf(Objects.requireNonNull(poolStats, "poolStats is null"));
        this.root = root;
        this.status = effectiveStatus(root, configuration);
        this.exitCode = exitCodeFor(status);
        this.startedAt = root == null ? null : earliestStartedAt(root).orElse(null);
        this.completedAt = root == null ? null : latestCompletedAt(root).orElse(null);
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
        Objects.requireNonNull(configuration, "configuration is null");
        this.poolStats = List.of();
        this.root = null;
        this.status = Objects.requireNonNull(statusOverride, "statusOverride is null");
        this.exitCode = exitCodeFor(status);
        this.startedAt = null;
        this.completedAt = null;
    }

    /**
     * Returns the pool sizes and peak usage observed by this run.
     *
     * <p>Internal: the summary listener reads it; the public {@link Result} interface deliberately stays free of
     * framework-internal types.
     *
     * @return the pool statistics; never {@code null}
     */
    public List<Pools.PoolStats> poolStats() {
        return poolStats;
    }

    @Override
    public Optional<Descriptor> descriptor() {
        return Optional.ofNullable(root);
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public int exitCode() {
        return exitCode;
    }

    @Override
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    @Override
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    private static int exitCodeFor(final Status status) {
        return status.isFailed() ? 1 : 0;
    }

    private static Status effectiveStatus(final Descriptor root, final Configuration configuration) {
        if (root == null) {
            final var failIfNoTests =
                    configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(false);
            return failIfNoTests ? Status.failed("No Paramixel tests found") : Status.SKIPPED;
        }
        return promote(root.status(), configuration);
    }

    private static Status promote(final Status status, final Configuration configuration) {
        if (status.isSkipped()
                && configuration.getBoolean(Configuration.FAILURE_ON_SKIP).orElse(false)) {
            return promotedToFailed(status, "skipped");
        }
        if (status.isAborted()
                && configuration.getBoolean(Configuration.FAILURE_ON_ABORT).orElse(true)) {
            return promotedToFailed(status, "aborted");
        }
        return status;
    }

    private static Status promotedToFailed(final Status status, final String defaultMessage) {
        var message = status.message().orElse(defaultMessage);
        return status.throwable()
                .map(throwable -> Status.failed(message, throwable))
                .orElseGet(() -> Status.failed(message));
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
