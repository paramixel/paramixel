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
import org.paramixel.core.action.Noop;

@DisplayName("Exception handling tests")
class ExceptionHandlingTest {

    @Test
    @DisplayName("FAIL exceptions store throwable in Result")
    void failExceptionsStoreThrowableInResult() {
        var expectedException = new RuntimeException("test exception");
        Action action = Direct.of("test", context -> {
            throw expectedException;
        });

        Runner runner = Runner.builder().build();
        runner.run(action);
        Result result = action.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getThrowable().isPresent()).isTrue();
        assertThat(result.getStatus().getThrowable().get()).isSameAs(expectedException);
    }

    @Test
    @DisplayName("SKIP exceptions store message in Result")
    void skipExceptionsStoreMessageInResult() {
        var skipException = new SkipException("database not available");
        Action action = Direct.of("test", context -> {
            throw skipException;
        });

        Runner runner = Runner.builder().build();
        runner.run(action);
        Result result = action.getResult();

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("database not available");
        assertThat(result.getStatus().getThrowable()).isEmpty();
    }

    @Test
    @DisplayName("Lifecycle before SkipException stores reason in Result")
    void lifecycleBeforeSkipExceptionStoresReason() {
        var skipException = new SkipException("before condition not met");
        Action main = Direct.of("main", context -> {});
        Lifecycle lifecycle = Lifecycle.of(
                "test",
                Direct.of("before", context -> {
                    throw skipException;
                }),
                main,
                Noop.of("after"));

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before condition not met");
        assertThat(result.getStatus().getThrowable()).isEmpty();
    }

    @Test
    @DisplayName("FAIL exceptions include message in summary renderer")
    void failExceptionsIncludeMessageInSummary() {
        Action action = Direct.of("test", context -> {
            throw new RuntimeException("something went wrong");
        });

        Runner runner = Runner.builder().build();
        runner.run(action);
        Result result = action.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getThrowable()).isPresent();
        Throwable failure = result.getStatus().getThrowable().get();
        assertThat(failure.getClass().getSimpleName()).isEqualTo("RuntimeException");
        assertThat(failure.getMessage()).isEqualTo("something went wrong");
    }
}
