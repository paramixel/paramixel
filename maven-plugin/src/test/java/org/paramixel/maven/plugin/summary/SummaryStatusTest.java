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

package org.paramixel.maven.plugin.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.maven.plugin.internal.summary.SummaryStatus;

@DisplayName("SummaryStatus tests")
class SummaryStatusTest {

    @Nested
    @DisplayName("isTerminal tests")
    class IsTerminalTests {

        @Test
        @DisplayName("STARTED should not be terminal")
        void startedShouldNotBeTerminal() {
            assertThat(SummaryStatus.STARTED.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("SUCCESSFUL should be terminal")
        void successfulShouldBeTerminal() {
            assertThat(SummaryStatus.SUCCESSFUL.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("FAILED should be terminal")
        void failedShouldBeTerminal() {
            assertThat(SummaryStatus.FAILED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("ABORTED should be terminal")
        void abortedShouldBeTerminal() {
            assertThat(SummaryStatus.ABORTED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("SKIPPED should be terminal")
        void skippedShouldBeTerminal() {
            assertThat(SummaryStatus.SKIPPED.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("isFailure tests")
    class IsFailureTests {

        @Test
        @DisplayName("STARTED should not be a failure")
        void startedShouldNotBeFailure() {
            assertThat(SummaryStatus.STARTED.isFailure()).isFalse();
        }

        @Test
        @DisplayName("SUCCESSFUL should not be a failure")
        void successfulShouldNotBeFailure() {
            assertThat(SummaryStatus.SUCCESSFUL.isFailure()).isFalse();
        }

        @Test
        @DisplayName("FAILED should be a failure")
        void failedShouldBeFailure() {
            assertThat(SummaryStatus.FAILED.isFailure()).isTrue();
        }

        @Test
        @DisplayName("ABORTED should not be a failure")
        void abortedShouldNotBeFailure() {
            assertThat(SummaryStatus.ABORTED.isFailure()).isFalse();
        }

        @Test
        @DisplayName("SKIPPED should not be a failure")
        void skippedShouldNotBeFailure() {
            assertThat(SummaryStatus.SKIPPED.isFailure()).isFalse();
        }
    }
}
