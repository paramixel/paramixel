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

    @Test
    @DisplayName("setParent rejects null")
    void setParentRejectsNull() {
        DefaultResult result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.setParent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parent must not be null");
    }

    @Test
    @DisplayName("addChild with non-DefaultResult does not set parent")
    void addChildWithNonDefaultResultDoesNotSetParent() {
        DefaultResult parentResult = new DefaultResult(Noop.of("parent"));
        Result nonDefaultResult = new Result() {
            @Override
            public Status getStatus() {
                return DefaultStatus.PASS;
            }

            @Override
            public Duration getElapsedTime() {
                return Duration.ZERO;
            }

            @Override
            public Duration getCumulativeElapsedTime() {
                return Duration.ZERO;
            }

            @Override
            public Action getAction() {
                return Noop.of("child");
            }

            @Override
            public java.util.Optional<Result> getParent() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<Result> getChildren() {
                return java.util.List.of();
            }
        };

        parentResult.addChild(nonDefaultResult);

        assertThat(parentResult.getChildren()).hasSize(1);
        assertThat(nonDefaultResult.getParent()).isEmpty();
    }

    @Test
    @DisplayName("getCumulativeElapsedTime sums nested children")
    void getCumulativeElapsedTimeSumsNestedChildren() {
        Action parent = Noop.of("parent");
        Action child1 = Noop.of("child1");
        Action child2 = Noop.of("child2");

        DefaultResult parentResult = new DefaultResult(parent);
        DefaultResult childResult1 = new DefaultResult(child1);
        childResult1.setStatus(DefaultStatus.PASS);
        childResult1.setElapsedTime(Duration.ofMillis(100));
        DefaultResult childResult2 = new DefaultResult(child2);
        childResult2.setStatus(DefaultStatus.PASS);
        childResult2.setElapsedTime(Duration.ofMillis(200));

        parentResult.addChild(childResult1);
        parentResult.addChild(childResult2);
        parentResult.setStatus(DefaultStatus.PASS);
        parentResult.setElapsedTime(Duration.ofMillis(50));

        assertThat(parentResult.getCumulativeElapsedTime()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    @DisplayName("getCumulativeElapsedTime returns own time when leaf")
    void getCumulativeElapsedTimeReturnsOwnTimeWhenLeaf() {
        DefaultResult result = new DefaultResult(Noop.of("test"));
        result.setStatus(DefaultStatus.PASS);
        result.setElapsedTime(Duration.ofMillis(42));

        assertThat(result.getCumulativeElapsedTime()).isEqualTo(Duration.ofMillis(42));
    }

    @Test
    @DisplayName("single arg constructor rejects null action")
    void singleArgConstructorRejectsNullAction() {
        assertThatThrownBy(() -> new DefaultResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    @DisplayName("setStatus rejects null")
    void setStatusRejectsNull() {
        DefaultResult result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.setStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("setElapsedTime rejects null")
    void setElapsedTimeRejectsNull() {
        DefaultResult result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.setElapsedTime(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("elapsedTime must not be null");
    }
}
