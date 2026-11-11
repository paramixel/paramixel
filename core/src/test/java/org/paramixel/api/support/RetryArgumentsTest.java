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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retry arguments")
class RetryArgumentsTest {

    @Test
    @DisplayName("of rejects a null backoff")
    void ofRejectsNullBackoff() {
        assertThatThrownBy(() -> Retry.of(null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("backoff is null");
    }

    @Test
    @DisplayName("of rejects a null budget")
    void ofRejectsNullBudget() {
        assertThatThrownBy(() -> Retry.of(Backoff.fixed(Duration.ZERO), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maximumDuration is null");
    }

    @Test
    @DisplayName("of rejects a negative budget")
    void ofRejectsNegativeBudget() {
        assertThatThrownBy(() -> Retry.of(Backoff.fixed(Duration.ZERO), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumDuration is negative");
    }

    @Test
    @DisplayName("retryOn rejects null")
    void retryOnRejectsNull() {
        var retry = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ZERO);

        assertThatThrownBy(() -> retry.retryOn(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate is null");
    }

    @Test
    @DisplayName("onRetry rejects null")
    void onRetryRejectsNull() {
        var retry = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ZERO);

        assertThatThrownBy(() -> retry.onRetry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("callback is null");
    }

    @Test
    @DisplayName("run rejects a null runnable")
    void runRejectsNullRunnable() {
        var retry = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ZERO);

        assertThatThrownBy(() -> retry.run(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable is null");
    }

    @Test
    @DisplayName("runAndThrow rejects a null runnable")
    void runAndThrowRejectsNullRunnable() {
        var retry = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ZERO);

        assertThatThrownBy(() -> retry.runAndThrow(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("throwableRunnable is null");
    }

    @Test
    @DisplayName("configuration is immutable: retryOn and onRetry return a new instance")
    void configurationIsImmutable() {
        var base = Retry.of(Backoff.fixed(Duration.ZERO), Duration.ofSeconds(1));

        var configured = base.retryOn(t -> false).onRetry((attempt, cause) -> {});

        assertThat(configured).isNotSameAs(base);
        // The configured instance stops after the first failure; the base instance keeps the default predicate.
        assertThat(configured
                        .run(() -> {
                            throw new IllegalStateException("once");
                        })
                        .attemptCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a configuration can be run more than once")
    void configurationIsReusable() {
        var retry =
                Retry.of(Backoff.fixed(Duration.ZERO), Duration.ofSeconds(1)).retryOn(t -> false);

        var first = retry.run(() -> {
            throw new IllegalStateException("first");
        });
        var second = retry.run(() -> {
            throw new IllegalStateException("second");
        });

        assertThat(first.successful()).isFalse();
        assertThat(second.successful()).isFalse();
        assertThat(first.exceptions().get(0)).hasMessage("first");
        assertThat(second.exceptions().get(0)).hasMessage("second");
    }
}
