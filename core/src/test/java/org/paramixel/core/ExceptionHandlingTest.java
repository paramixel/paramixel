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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;

@DisplayName("Exception handling tests")
class ExceptionHandlingTest {

    @Test
    @DisplayName("FAIL exceptions store throwable in Result")
    void failExceptionsStoreThrowableInResult() {
        var expectedException = new RuntimeException("test exception");
        Action action = Direct.of("test", context -> {
            throw expectedException;
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent().get().isSameAs(expectedException);
    }

    @Test
    @DisplayName("SKIP exceptions store message in Result")
    void skipExceptionsStoreMessageInResult() {
        var skipException = new SkipException("database not available");
        Action action = Direct.of("test", context -> {
            throw skipException;
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.failure()).isPresent();
        assertThat(result.failure().get().getMessage()).isEqualTo("database not available");
    }

    @Test
    @DisplayName("Lifecycle setup SkipException stores reason in Result")
    void lifecycleSetupSkipExceptionStoresReason() {
        var skipException = new SkipException("setup condition not met");
        Action body = Direct.of("body", context -> {});
        Lifecycle lifecycle = Lifecycle.of(
                "test",
                context -> {
                    throw skipException;
                },
                body);

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.failure()).isPresent().get().isSameAs(skipException);
        assertThat(result.failure().get().getMessage()).isEqualTo("setup condition not met");
    }

    @Test
    @DisplayName("FAIL exceptions include message in summary renderer")
    void failExceptionsIncludeMessageInSummary() {
        Action action = Direct.of("test", context -> {
            throw new RuntimeException("something went wrong");
        });

        Result result = Runner.builder().build().run(action);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent();
        Throwable failure = result.failure().get();
        assertThat(failure.getClass().getSimpleName()).isEqualTo("RuntimeException");
        assertThat(failure.getMessage()).isEqualTo("something went wrong");
    }
}
