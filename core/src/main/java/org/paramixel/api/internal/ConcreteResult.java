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

package org.paramixel.api.internal;

import java.util.Objects;
import java.util.Optional;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Status;
import org.paramixel.api.action.Descriptor;

/**
 * Concrete implementation of {@link Result} that applies configuration-based
 * status promotion to the root descriptor's already-aggregated status.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ConcreteResult implements Result {

    private final Descriptor root;
    private final Configuration configuration;

    /**
     * Creates a result for a run that produced a descriptor tree.
     *
     * @param root the root descriptor; may be {@code null} when no descriptor was discovered
     * @param configuration the run configuration; must not be {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    public ConcreteResult(final Descriptor root, final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.root = root;
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

    @Override
    public Optional<Descriptor> descriptor() {
        return Optional.ofNullable(root);
    }

    @Override
    public Status status() {
        if (root == null) {
            final var failIfNoTests =
                    configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS).orElse(false);
            return failIfNoTests ? Status.FAILED : Status.SKIPPED;
        }
        return promote(root.metadata().status());
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
}
