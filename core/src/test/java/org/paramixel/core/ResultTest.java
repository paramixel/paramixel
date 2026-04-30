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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Result")
class ResultTest {

    @Test
    @DisplayName("creates staged result")
    void createsStagedResult() {
        var result = Result.staged();

        assertThat(result.getStatus().isStaged()).isTrue();
        assertThat(result.getStatus().isPass()).isFalse();
        assertThat(result.getStatus().isFailure()).isFalse();
        assertThat(result.getStatus().isSkip()).isFalse();
        assertThat(result.getElapsedTime()).isEqualTo(Duration.ZERO);
        assertThat(result.getStatus().getDisplayName()).isEqualTo("STAGED");
    }

    @Test
    @DisplayName("creates passing result via pass factory")
    void createsPassingResultViaPassFactory() {
        var timing = Duration.ofMillis(100);

        var result = Result.pass(timing);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("creates failing result via fail factory with throwable")
    void createsFailingResultViaFailFactoryWithThrowable() {
        var timing = Duration.ofMillis(150);
        Throwable failure = new RuntimeException("test failure");

        var result = Result.fail(timing, failure);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).contains(failure);
        assertThat(result.getStatus().getMessage()).contains("test failure");
    }

    @Test
    @DisplayName("creates failing result with message only")
    void createsFailingResultWithMessageOnly() {
        var timing = Duration.ofMillis(150);

        var result = Result.fail(timing, "failure message");

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).contains("failure message");
    }

    @Test
    @DisplayName("creates skipped result")
    void createsSkippedResult() {
        var timing = Duration.ZERO;

        var result = Result.skip(timing);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("creates skipped result with reason")
    void createsSkippedResultWithReason() {
        var timing = Duration.ZERO;

        var result = Result.skip(timing, "skipped for reason");

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).contains("skipped for reason");
    }

    @Test
    @DisplayName("creates result via of factory with all parameters")
    void createsResultViaOfFactoryWithAllParameters() {
        var status = Status.pass();
        var timing = Duration.ofMillis(123);

        var result = Result.of(status, timing);

        assertThat(result.getStatus()).isSameAs(status);
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("fails to create result with null status")
    void failsToCreateResultWithNullStatus() {
        var timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> Result.of(null, timing))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("fails to create result with null timing")
    void failsToCreateResultWithNullTiming() {
        var status = Status.pass();

        assertThatThrownBy(() -> Result.of(status, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("timing must not be null");
    }

    @Test
    @DisplayName("toString returns expected format")
    void toStringReturnsExpectedFormat() {
        var result = Result.pass(Duration.ofMillis(123));

        assertThat(result.toString()).isEqualTo("PASS | 123 ms");
    }
}
