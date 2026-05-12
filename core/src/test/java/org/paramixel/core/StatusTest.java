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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Status")
class StatusTest {

    @Nested
    @DisplayName("static factory methods reject null arguments")
    class StaticFactoryMethodsRejectNullArguments {

        @Test
        @DisplayName("skip(String) rejects null message")
        void skipStringRejectsNullMessage() {
            assertThatThrownBy(() -> Status.skip((String) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("message must not be null");
        }

        @Test
        @DisplayName("failure(Throwable) rejects null throwable")
        void failureThrowableRejectsNullThrowable() {
            assertThatThrownBy(() -> Status.failure((Throwable) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("throwable must not be null");
        }

        @Test
        @DisplayName("failure(String) rejects null message")
        void failureStringRejectsNullMessage() {
            assertThatThrownBy(() -> Status.failure((String) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("message must not be null");
        }
    }

    @Nested
    @DisplayName("static factory methods create correct statuses")
    class StaticFactoryMethodsCreateCorrectStatuses {

        @Test
        @DisplayName("skip(String) creates skip status with message")
        void skipStringCreatesSkipStatusWithMessage() {
            Status status = Status.skip("reason");
            assertThat(status.isSkip()).isTrue();
            assertThat(status.getMessage()).contains("reason");
        }

        @Test
        @DisplayName("failure(Throwable) creates failure status with throwable")
        void failureThrowableCreatesFailureStatusWithThrowable() {
            Throwable t = new RuntimeException("boom");
            Status status = Status.failure(t);
            assertThat(status.isFailure()).isTrue();
            assertThat(status.getThrowable()).contains(t);
        }

        @Test
        @DisplayName("failure(String) creates failure status with message")
        void failureStringCreatesFailureStatusWithMessage() {
            Status status = Status.failure("error");
            assertThat(status.isFailure()).isTrue();
            assertThat(status.getMessage()).contains("error");
        }
    }
}
