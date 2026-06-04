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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.StatusAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;

@DisplayName("Status accumulator")
class StatusAccumulatorTest {

    @Test
    @DisplayName("empty accumulation defaults to PASSED")
    void emptyDefaultsToPassed() {
        var accumulator = new StatusAccumulator();

        assertThat(accumulator.status().isPassed()).isTrue();
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

    @Nested
    @DisplayName("include(Status)")
    class IncludeStatus {

        @Test
        @DisplayName("rejects null status")
        void rejectsNullStatus() {
            var accumulator = new StatusAccumulator();
            assertThatThrownBy(() -> accumulator.include((Status) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("status is null");
        }

        @Test
        @DisplayName("PASSED alone returns PASSED")
        void passedAloneReturnsPassed() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            assertThat(accumulator.status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("SKIPPED alone returns SKIPPED")
        void skippedAloneReturnsSkipped() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.SKIPPED);
            assertThat(accumulator.status()).isSameAs(Status.SKIPPED);
        }

        @Test
        @DisplayName("PENDING alone returns RUNNING")
        void pendingAloneReturnsRunning() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PENDING);
            assertThat(accumulator.status()).isSameAs(Status.RUNNING);
        }

        @Test
        @DisplayName("RUNNING alone returns RUNNING")
        void runningAloneReturnsRunning() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.RUNNING);
            assertThat(accumulator.status()).isSameAs(Status.RUNNING);
        }

        @Test
        @DisplayName("ABORTED alone returns ABORTED")
        void abortedAloneReturnsAborted() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.ABORTED);
            assertThat(accumulator.status()).isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("FAILED alone returns FAILED")
        void failedAloneReturnsFailed() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.FAILED);
            assertThat(accumulator.status()).isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("FAILED dominates all other statuses")
        void failedDominatesAllOthers() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            accumulator.include(Status.SKIPPED);
            accumulator.include(Status.ABORTED);
            accumulator.include(Status.RUNNING);
            accumulator.include(Status.FAILED);
            assertThat(accumulator.status()).isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("ABORTED dominates RUNNING, SKIPPED, and PASSED")
        void abortedDominatesRunningSkippedPassed() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            accumulator.include(Status.SKIPPED);
            accumulator.include(Status.RUNNING);
            accumulator.include(Status.ABORTED);
            assertThat(accumulator.status()).isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("RUNNING dominates SKIPPED and PASSED")
        void runningDominatesSkippedPassed() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            accumulator.include(Status.SKIPPED);
            accumulator.include(Status.PENDING);
            assertThat(accumulator.status()).isSameAs(Status.RUNNING);
        }

        @Test
        @DisplayName("SKIPPED dominates PASSED")
        void skippedDominatesPassed() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            accumulator.include(Status.SKIPPED);
            assertThat(accumulator.status()).isSameAs(Status.SKIPPED);
        }

        @Test
        @DisplayName("multiple include(Status) calls accumulate correctly")
        void multipleIncludeCallsAccumulateCorrectly() {
            var accumulator = new StatusAccumulator();
            accumulator.include(Status.PASSED);
            accumulator.include(Status.PASSED);
            accumulator.include(Status.PASSED);
            assertThat(accumulator.status()).isSameAs(Status.PASSED);
            accumulator.include(Status.SKIPPED);
            assertThat(accumulator.status()).isSameAs(Status.SKIPPED);
        }

        @Test
        @DisplayName("matches Status.aggregate for representative status mixes")
        void matchesStatusAggregateForStatuses() {
            assertAggregateMatches(List.of(Status.PASSED, Status.PASSED));
            assertAggregateMatches(List.of(Status.SKIPPED, Status.PASSED));
            assertAggregateMatches(List.of(Status.PENDING, Status.PASSED));
            assertAggregateMatches(List.of(Status.RUNNING, Status.SKIPPED));
            assertAggregateMatches(List.of(Status.ABORTED, Status.RUNNING, Status.SKIPPED));
            assertAggregateMatches(List.of(Status.SKIPPED, Status.ABORTED, Status.FAILED));
        }

        private void assertAggregateMatches(final List<Status> statuses) {
            var descriptors = descriptorsWithStatuses(statuses);
            var expected = Status.aggregate(descriptors);
            var accumulator = new StatusAccumulator();
            for (Status s : statuses) {
                accumulator.include(s);
            }
            assertThat(accumulator.status()).isSameAs(expected);
        }
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
