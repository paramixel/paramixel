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
import org.paramixel.core.action.Noop;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;

@DisplayName("Result")
class ResultTest {

    @Test
    @DisplayName("creates staged result")
    void createsStagedResult() {
        Action action = Noop.of("test");
        var result = new DefaultResult(action);

        assertThat(result.getStatus().isStaged()).isTrue();
        assertThat(result.getStatus().isPass()).isFalse();
        assertThat(result.getStatus().isFailure()).isFalse();
        assertThat(result.getStatus().isSkip()).isFalse();
        assertThat(result.getElapsedTime()).isEqualTo(Duration.ZERO);
        assertThat(result.getStatus().getDisplayName()).isEqualTo("STAGED");
    }

    @Test
    @DisplayName("creates passing result via status and elapsed time setters")
    void createsPassingResultViaSetters() {
        Action action = Noop.of("test");
        var timing = Duration.ofMillis(100);

        DefaultResult result = new DefaultResult(action);
        result.setStatus(DefaultStatus.PASS);
        result.setElapsedTime(timing);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("creates failing result with throwable")
    void createsFailingResultWithThrowable() {
        Action action = Noop.of("test");
        var timing = Duration.ofMillis(150);
        Throwable failure = new RuntimeException("test failure");

        DefaultResult result = new DefaultResult(action);
        result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, failure));
        result.setElapsedTime(timing);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).contains(failure);
        assertThat(result.getStatus().getMessage()).contains("test failure");
    }

    @Test
    @DisplayName("creates failing result with message only")
    void createsFailingResultWithMessageOnly() {
        Action action = Noop.of("test");
        var timing = Duration.ofMillis(150);

        DefaultResult result = new DefaultResult(action);
        result.setStatus(new DefaultStatus(DefaultStatus.Kind.FAILURE, "failure message"));
        result.setElapsedTime(timing);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).contains("failure message");
    }

    @Test
    @DisplayName("creates skipped result")
    void createsSkippedResult() {
        Action action = Noop.of("test");
        var timing = Duration.ZERO;

        DefaultResult result = new DefaultResult(action);
        result.setStatus(DefaultStatus.SKIP);
        result.setElapsedTime(timing);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("creates skipped result with reason")
    void createsSkippedResultWithReason() {
        Action action = Noop.of("test");
        var timing = Duration.ZERO;

        DefaultResult result = new DefaultResult(action);
        result.setStatus(new DefaultStatus(DefaultStatus.Kind.SKIP, "skipped for reason"));
        result.setElapsedTime(timing);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).contains("skipped for reason");
    }

    @Test
    @DisplayName("creates result via of factory with all parameters")
    void createsResultViaOfFactoryWithAllParameters() {
        Action action = Noop.of("test");
        var status = DefaultStatus.PASS;
        var timing = Duration.ofMillis(123);

        var result = new DefaultResult(action, status, timing);

        assertThat(result.getStatus()).isSameAs(status);
        assertThat(result.getElapsedTime()).isEqualTo(timing);
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result.getStatus().getMessage()).isEmpty();
    }

    @Test
    @DisplayName("of factory rejects null status")
    void ofFactoryRejectsNullStatus() {
        Action action = Noop.of("test");
        var timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> new DefaultResult(action, null, timing))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("of factory rejects null timing")
    void ofFactoryRejectsNullTiming() {
        Action action = Noop.of("test");
        var status = DefaultStatus.PASS;

        assertThatThrownBy(() -> new DefaultResult(action, status, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("elapsedTime must not be null");
    }

    @Test
    @DisplayName("of factory rejects null action")
    void ofFactoryRejectsNullAction() {
        var status = DefaultStatus.PASS;
        var timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> new DefaultResult(null, status, timing))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    @DisplayName("toString returns expected format")
    void toStringReturnsExpectedFormat() {
        Action action = Noop.of("test");
        DefaultResult result = new DefaultResult(action);
        result.setStatus(DefaultStatus.PASS);
        result.setElapsedTime(Duration.ofMillis(123));

        assertThat(result.toString()).isEqualTo("PASS | 123 ms");
    }
}
