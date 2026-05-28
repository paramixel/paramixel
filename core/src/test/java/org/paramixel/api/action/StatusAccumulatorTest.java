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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.StatusAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;

@DisplayName("Status accumulator")
class StatusAccumulatorTest {

    @Test
    @DisplayName("empty accumulation defaults to PASSED")
    void emptyDefaultsToPassed() {
        var accumulator = new StatusAccumulator();

        assertThat(accumulator.status()).isSameAs(Status.PASSED);
    }

    @Test
    @DisplayName("accumulator matches Status.aggregate for representative status mixes")
    void matchesStatusAggregate() {
        assertMatchesStatusAggregate(List.of(Status.PASSED, Status.PASSED));
        assertMatchesStatusAggregate(List.of(Status.SKIPPED, Status.PASSED));
        assertMatchesStatusAggregate(List.of(Status.PENDING, Status.PASSED));
        assertMatchesStatusAggregate(List.of(Status.RUNNING, Status.SKIPPED));
        assertMatchesStatusAggregate(List.of(Status.ABORTED, Status.RUNNING, Status.SKIPPED));
        assertMatchesStatusAggregate(List.of(Status.SKIPPED, Status.ABORTED, Status.FAILED));
    }

    private static void assertMatchesStatusAggregate(final List<Status> statuses) {
        var descriptors = descriptorsWithStatuses(statuses);
        var expected = Status.aggregate(descriptors);
        var accumulator = new StatusAccumulator();

        for (Descriptor descriptor : descriptors) {
            accumulator.include(descriptor);
        }

        assertThat(accumulator.status()).isSameAs(expected);
    }

    private static List<Descriptor> descriptorsWithStatuses(final List<Status> statuses) {
        var descriptors = new ArrayList<Descriptor>(statuses.size());
        for (int i = 0; i < statuses.size(); i++) {
            var descriptor = new ConcreteDescriptor(Step.of("step-" + i, value -> {}));
            setStatus(descriptor, statuses.get(i));
            descriptors.add(descriptor);
        }
        return List.copyOf(descriptors);
    }

    private static void setStatus(final ConcreteDescriptor descriptor, final Status status) {
        if (status.isPending()) {
            return;
        }
        descriptor.setStatus(Status.RUNNING);
        if (!status.isRunning()) {
            descriptor.setStatus(status);
        }
    }
}
