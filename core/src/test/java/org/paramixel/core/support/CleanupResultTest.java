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

package org.paramixel.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CleanupResult")
class CleanupResultTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null exceptions list")
        void rejectsNullExceptionsList() {
            assertThatThrownBy(() -> new CleanupResult(1, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("exceptions must not be null");
        }
    }

    @Nested
    @DisplayName("getThrowableRunnableCount")
    class GetThrowableRunnableCount {

        @Test
        @DisplayName("returns executable count")
        void returnsThrowableRunnableCount() {
            CleanupResult result = new CleanupResult(3, Arrays.asList(null, null, null));
            assertThat(result.getThrowableRunnableCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("returns zero for empty result")
        void returnsZeroForEmptyResult() {
            CleanupResult result = new CleanupResult(0, List.of());
            assertThat(result.getThrowableRunnableCount()).isZero();
        }
    }

    @Nested
    @DisplayName("hasExceptions")
    class HasExceptions {

        @Test
        @DisplayName("returns false when no exceptions present")
        void returnsFalseWhenNoExceptionsPresent() {
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, null));
            assertThat(result.hasExceptions()).isFalse();
        }

        @Test
        @DisplayName("returns true when exceptions present")
        void returnsTrueWhenExceptionsPresent() {
            RuntimeException exception = new RuntimeException("error");
            CleanupResult result = new CleanupResult(2, Arrays.asList(exception, null));
            assertThat(result.hasExceptions()).isTrue();
        }

        @Test
        @DisplayName("returns false for empty result")
        void returnsFalseForEmptyResult() {
            CleanupResult result = new CleanupResult(0, List.of());
            assertThat(result.hasExceptions()).isFalse();
        }
    }

    @Nested
    @DisplayName("getException")
    class GetException {

        @Test
        @DisplayName("returns exception for failed index")
        void returnsExceptionForFailedIndex() {
            RuntimeException exception = new RuntimeException("error");
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, exception));
            assertThat(result.getException(1)).isPresent().containsSame(exception);
        }

        @Test
        @DisplayName("returns empty for successful index")
        void returnsEmptyForSuccessfulIndex() {
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, null));
            assertThat(result.getException(0)).isEmpty();
        }

        @Test
        @DisplayName("returns empty for negative index")
        void returnsEmptyForNegativeIndex() {
            CleanupResult result = new CleanupResult(1, Arrays.asList((Throwable) null));
            assertThat(result.getException(-1)).isEmpty();
        }

        @Test
        @DisplayName("returns empty for out-of-range index")
        void returnsEmptyForOutOfRangeIndex() {
            CleanupResult result = new CleanupResult(1, Arrays.asList((Throwable) null));
            assertThat(result.getException(1)).isEmpty();
            assertThat(result.getException(100)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isSuccess")
    class IsSuccess {

        @Test
        @DisplayName("returns true for successful index")
        void returnsTrueForSuccessfulIndex() {
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, null));
            assertThat(result.isSuccess(0)).isTrue();
            assertThat(result.isSuccess(1)).isTrue();
        }

        @Test
        @DisplayName("returns false for failed index")
        void returnsFalseForFailedIndex() {
            RuntimeException exception = new RuntimeException("error");
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, exception));
            assertThat(result.isSuccess(0)).isTrue();
            assertThat(result.isSuccess(1)).isFalse();
        }

        @Test
        @DisplayName("returns false for negative index")
        void returnsFalseForNegativeIndex() {
            CleanupResult result = new CleanupResult(1, Arrays.asList((Throwable) null));
            assertThat(result.isSuccess(-1)).isFalse();
        }

        @Test
        @DisplayName("returns false for out-of-range index")
        void returnsFalseForOutOfRangeIndex() {
            CleanupResult result = new CleanupResult(1, Arrays.asList((Throwable) null));
            assertThat(result.isSuccess(1)).isFalse();
            assertThat(result.isSuccess(100)).isFalse();
        }
    }

    @Nested
    @DisplayName("getExceptionsByIndex")
    class GetExceptionsByIndex {

        @Test
        @DisplayName("returns only failures keyed by index")
        void returnsOnlyFailuresKeyedByIndex() {
            RuntimeException exception1 = new RuntimeException("error 1");
            RuntimeException exception2 = new RuntimeException("error 2");
            CleanupResult result = new CleanupResult(4, Arrays.asList(null, exception1, null, exception2));
            var failures = result.getExceptionsByIndex();
            assertThat(failures).hasSize(2);
            assertThat(failures.get(1)).isSameAs(exception1);
            assertThat(failures.get(3)).isSameAs(exception2);
        }

        @Test
        @DisplayName("returns empty map when no failures")
        void returnsEmptyMapWhenNoFailures() {
            CleanupResult result = new CleanupResult(2, Arrays.asList(null, null));
            assertThat(result.getExceptionsByIndex()).isEmpty();
        }

        @Test
        @DisplayName("returns immutable map")
        void returnsImmutableMap() {
            RuntimeException exception = new RuntimeException("error");
            CleanupResult result = new CleanupResult(1, List.of(exception));
            assertThatThrownBy(() -> result.getExceptionsByIndex().put(99, new RuntimeException()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
