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

package org.paramixel.api.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.support.Retry.Policy;

@DisplayName("Retry arguments")
class RetryArgumentsTest {

    @Test
    @DisplayName("of rejects null policy")
    void ofRejectsNullPolicy() {
        assertThatThrownBy(() -> Retry.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy must not be null");
    }

    @Test
    @DisplayName("retryOn rejects null predicate")
    void retryOnRejectsNullPredicate() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.retryOn(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate must not be null");
    }

    @Test
    @DisplayName("onRetry rejects null callback")
    void onRetryRejectsNullCallback() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.onRetry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback must not be null");
    }

    @Test
    @DisplayName("onRetry throws IllegalStateException after run")
    void onRetryThrowsIllegalStateExceptionAfterRun() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));
        retry.run(() -> {});

        assertThatThrownBy(() -> retry.onRetry((attempt, cause) -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("onRetry must not be called after run");
    }

    @Test
    @DisplayName("run rejects null executable")
    void runRejectsNullThrowableRunnable() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.run(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable must not be null");
    }

    @Test
    @DisplayName("Policy.fixed rejects null initialDelay")
    void policyFixedRejectsNullInitialDelay() {
        assertThatThrownBy(() -> Policy.fixed(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialDelay must not be null");
    }

    @Test
    @DisplayName("Policy.fixed rejects null maximumDuration")
    void policyFixedRejectsNullMaximumDuration() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofMillis(100), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maximumDuration must not be null");
    }

    @Test
    @DisplayName("Policy.fixed rejects negative initialDelay")
    void policyFixedRejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofMillis(-1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be negative");
    }

    @Test
    @DisplayName("Policy.fixed rejects negative maximumDuration")
    void policyFixedRejectsNegativeMaximumDuration() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ZERO, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximumDuration must not be negative");
    }

    @Test
    @DisplayName("Policy.fixed rejects initialDelay greater than maximumDuration")
    void policyFixedRejectsInitialGreaterThanMax() {
        assertThatThrownBy(() -> Policy.fixed(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be greater than maximumDuration");
    }

    @Test
    @DisplayName("Policy.exponential rejects null initialDelay")
    void policyExponentialRejectsNullInitialDelay() {
        assertThatThrownBy(() -> Policy.exponential(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialDelay must not be null");
    }

    @Test
    @DisplayName("Policy.exponential rejects null maximumDuration")
    void policyExponentialRejectsNullMaximumDuration() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofMillis(100), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maximumDuration must not be null");
    }

    @Test
    @DisplayName("Policy.exponential rejects negative initialDelay")
    void policyExponentialRejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofMillis(-1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be negative");
    }

    @Test
    @DisplayName("Policy.exponential rejects negative maximumDuration")
    void policyExponentialRejectsNegativeMaximumDuration() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ZERO, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maximumDuration must not be negative");
    }

    @Test
    @DisplayName("Policy.exponential rejects initialDelay greater than maximumDuration")
    void policyExponentialRejectsInitialGreaterThanMax() {
        assertThatThrownBy(() -> Policy.exponential(Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialDelay must not be greater than maximumDuration");
    }

    @Test
    @DisplayName("run throws on second call")
    void runThrowsOnSecondCall() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ofSeconds(10)));

        retry.run(() -> {});

        assertThatThrownBy(() -> retry.run(() -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Retry has already run");
    }

    @Test
    @DisplayName("runAndThrow rejects null executable")
    void runAndThrowRejectsNullThrowableRunnable() {
        Retry retry = Retry.of(Policy.fixed(Duration.ZERO, Duration.ZERO));

        assertThatThrownBy(() -> retry.runAndThrow(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable must not be null");
    }
}
