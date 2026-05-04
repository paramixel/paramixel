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

package org.paramixel.core.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultStatus")
class DefaultStatusTest {

    @Nested
    @DisplayName("constructor with kind")
    class ConstructorKind {

        @Test
        @DisplayName("rejects null kind")
        void rejectsNullKind() {
            assertThatThrownBy(() -> new DefaultStatus(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("creates STAGED status")
        void createsStagedStatus() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.STAGED);
            assertThat(status.kind()).isEqualTo(DefaultStatus.Kind.STAGED);
            assertThat(status.isStaged()).isTrue();
            assertThat(status.isPass()).isFalse();
            assertThat(status.isFailure()).isFalse();
            assertThat(status.isSkip()).isFalse();
        }

        @Test
        @DisplayName("creates PASS status")
        void createsPassStatus() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.PASS);
            assertThat(status.kind()).isEqualTo(DefaultStatus.Kind.PASS);
            assertThat(status.isStaged()).isFalse();
            assertThat(status.isPass()).isTrue();
            assertThat(status.isFailure()).isFalse();
            assertThat(status.isSkip()).isFalse();
        }

        @Test
        @DisplayName("creates FAILURE status")
        void createsFailureStatus() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE);
            assertThat(status.kind()).isEqualTo(DefaultStatus.Kind.FAILURE);
            assertThat(status.isStaged()).isFalse();
            assertThat(status.isPass()).isFalse();
            assertThat(status.isFailure()).isTrue();
            assertThat(status.isSkip()).isFalse();
        }

        @Test
        @DisplayName("creates SKIP status")
        void createsSkipStatus() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.SKIP);
            assertThat(status.kind()).isEqualTo(DefaultStatus.Kind.SKIP);
            assertThat(status.isStaged()).isFalse();
            assertThat(status.isPass()).isFalse();
            assertThat(status.isFailure()).isFalse();
            assertThat(status.isSkip()).isTrue();
        }
    }

    @Nested
    @DisplayName("constructor with kind and message")
    class ConstructorKindMessage {

        @Test
        @DisplayName("rejects null kind")
        void rejectsNullKind() {
            assertThatThrownBy(() -> new DefaultStatus(null, "message")).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("creates status with message")
        void createsStatusWithMessage() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, "something failed");
            assertThat(status.getMessage()).isPresent().contains("something failed");
            assertThat(status.getThrowable()).isEmpty();
        }

        @Test
        @DisplayName("creates status with null message")
        void createsStatusWithNullMessage() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.PASS, (String) null);
            assertThat(status.getMessage()).isEmpty();
        }
    }

    @Nested
    @DisplayName("constructor with kind and throwable")
    class ConstructorKindThrowable {

        @Test
        @DisplayName("rejects null kind")
        void rejectsNullKind() {
            assertThatThrownBy(() -> new DefaultStatus(null, new RuntimeException()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("creates status with throwable")
        void createsStatusWithThrowable() {
            RuntimeException exception = new RuntimeException("error detail");
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, exception);
            assertThat(status.getThrowable()).isPresent().containsSame(exception);
        }

        @Test
        @DisplayName("message falls back to throwable message when direct message is null")
        void messageFallsBackToThrowableMessage() {
            RuntimeException exception = new RuntimeException("throwable message");
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, exception);
            assertThat(status.getMessage()).isPresent().contains("throwable message");
        }

        @Test
        @DisplayName("returns empty message when throwable is null")
        void returnsEmptyMessageWhenThrowableIsNull() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.PASS, (Throwable) null);
            assertThat(status.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("returns empty message when throwable has null message")
        void returnsEmptyMessageWhenThrowableHasNullMessage() {
            RuntimeException exception = new RuntimeException((String) null);
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, exception);
            assertThat(status.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("returns empty throwable when throwable is null")
        void returnsEmptyThrowableWhenNull() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.PASS, (Throwable) null);
            assertThat(status.getThrowable()).isEmpty();
        }
    }

    @Nested
    @DisplayName("constructor with kind, message, and throwable")
    class ConstructorKindMessageThrowable {

        @Test
        @DisplayName("rejects null kind")
        void rejectsNullKind() {
            assertThatThrownBy(() -> new DefaultStatus(null, "msg", new RuntimeException()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("creates status with message and throwable")
        void createsStatusWithMessageAndThrowable() {
            RuntimeException exception = new RuntimeException("throwable detail");
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, "failure msg", exception);
            assertThat(status.getMessage()).isPresent().contains("failure msg");
            assertThat(status.getThrowable()).isPresent().containsSame(exception);
        }

        @Test
        @DisplayName("prefers direct message over throwable message")
        void prefersDirectMessageOverThrowableMessage() {
            RuntimeException exception = new RuntimeException("throwable message");
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE, "direct message", exception);
            assertThat(status.getMessage()).isPresent().contains("direct message");
        }
    }

    @Nested
    @DisplayName("static instances")
    class StaticInstances {

        @Test
        @DisplayName("STAGED instance")
        void stagedInstance() {
            assertThat(DefaultStatus.STAGED.isStaged()).isTrue();
            assertThat(DefaultStatus.STAGED.kind()).isEqualTo(DefaultStatus.Kind.STAGED);
            assertThat(DefaultStatus.STAGED.getMessage()).isEmpty();
            assertThat(DefaultStatus.STAGED.getThrowable()).isEmpty();
        }

        @Test
        @DisplayName("PASS instance")
        void passInstance() {
            assertThat(DefaultStatus.PASS.isPass()).isTrue();
            assertThat(DefaultStatus.PASS.kind()).isEqualTo(DefaultStatus.Kind.PASS);
        }

        @Test
        @DisplayName("FAILURE instance")
        void failureInstance() {
            assertThat(DefaultStatus.FAILURE.isFailure()).isTrue();
            assertThat(DefaultStatus.FAILURE.kind()).isEqualTo(DefaultStatus.Kind.FAILURE);
        }

        @Test
        @DisplayName("SKIP instance")
        void skipInstance() {
            assertThat(DefaultStatus.SKIP.isSkip()).isTrue();
            assertThat(DefaultStatus.SKIP.kind()).isEqualTo(DefaultStatus.Kind.SKIP);
        }
    }

    @Nested
    @DisplayName("getDisplayName")
    class GetDisplayName {

        @Test
        @DisplayName("STAGED displays as STAGED")
        void stagedDisplayName() {
            assertThat(new DefaultStatus(DefaultStatus.Kind.STAGED).getDisplayName())
                    .isEqualTo("STAGED");
        }

        @Test
        @DisplayName("PASS displays as PASS")
        void passDisplayName() {
            assertThat(new DefaultStatus(DefaultStatus.Kind.PASS).getDisplayName())
                    .isEqualTo("PASS");
        }

        @Test
        @DisplayName("FAILURE displays as FAIL")
        void failureDisplayName() {
            assertThat(new DefaultStatus(DefaultStatus.Kind.FAILURE).getDisplayName())
                    .isEqualTo("FAIL");
        }

        @Test
        @DisplayName("SKIP displays as SKIP")
        void skipDisplayName() {
            assertThat(new DefaultStatus(DefaultStatus.Kind.SKIP).getDisplayName())
                    .isEqualTo("SKIP");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("delegates to getDisplayName")
        void delegatesToGetDisplayName() {
            DefaultStatus status = new DefaultStatus(DefaultStatus.Kind.FAILURE);
            assertThat(status.toString()).isEqualTo(status.getDisplayName());
        }
    }
}
