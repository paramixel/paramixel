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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.FailException;

@DisplayName("Assert actions")
class AssertTest {

    @Nested
    @DisplayName("AssertTrue")
    class AssertTrueTests {

        @Test
        @DisplayName("of(String, boolean) with true passes")
        void ofBooleanPasses() {
            var action = AssertTrue.of("assert-true", true);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("of(String, boolean) with false fails")
        void ofBooleanFails() {
            var action = AssertTrue.of("assert-false", false);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.FAILED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, boolean, String) with true passes and ignores message")
        void ofBooleanWithMessagePasses() {
            var action = AssertTrue.of("assert-true-msg", true, "should not appear");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, boolean, String) with false fails with message")
        void ofBooleanWithMessageFails() {
            var action = AssertTrue.of("assert-false-msg", false, "expected true");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().message()).contains("expected true");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with true passes")
        void ofSupplierPasses() {
            var action = AssertTrue.of("assert-supplier-true", () -> true);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with false fails")
        void ofSupplierFails() {
            var action = AssertTrue.of("assert-supplier-false", () -> false);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.FAILED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) with true passes and ignores message")
        void ofSupplierWithMessagePasses() {
            var action = AssertTrue.of("assert-supplier-true-msg", () -> true, "should not appear");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) with false fails with message")
        void ofSupplierWithMessageFails() {
            var action = AssertTrue.of("assert-supplier-false-msg", () -> false, "expected true");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().message()).contains("expected true");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with throwing supplier fails with throwable")
        void ofSupplierThrows() {
            var action = AssertTrue.of("assert-throws", () -> {
                throw new RuntimeException("boom");
            });
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().throwable()).isPresent();
            assertThat(root.metadata().throwable().get()).isInstanceOf(RuntimeException.class);
            assertThat(root.metadata().throwable().get().getMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName(
                "of(String, BooleanSupplier, String) with throwing supplier fails with throwable, message not used")
        void ofSupplierWithMessageThrows() {
            var action = AssertTrue.of(
                    "assert-throws-msg",
                    () -> {
                        throw new RuntimeException("boom");
                    },
                    "expected true");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().throwable()).isPresent();
            assertThat(root.metadata().throwable().get()).isInstanceOf(RuntimeException.class);
            assertThat(root.metadata().throwable().get().getMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName("non-RUN mode short-circuits without evaluating condition")
        void nonRunModeSkipsAssertion() {
            var evaluated = new AtomicBoolean();
            var action = Static.of("static")
                    .child("before", () -> FailException.fail("before failed"))
                    .child(AssertTrue.of("assert-skip", () -> {
                        evaluated.set(true);
                        return true;
                    }))
                    .resolve();
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();
            var assertChild = root.children().get(1);

            assertThat(assertChild.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(evaluated.get()).isFalse();
        }

        @Test
        @DisplayName("execute with null context throws NullPointerException")
        void executeWithNullContextThrows() {
            var action = AssertTrue.of("assert-null-context", true);

            assertThatThrownBy(() -> action.execute(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getName returns the supplied name")
        void getNameReturnsSuppliedName() {
            var action = AssertTrue.of("my-assert-true", true);

            assertThat(action.name()).isEqualTo("my-assert-true");
        }

        @Test
        @DisplayName("getKind returns AssertTrue")
        void getKindReturnsAssertTrue() {
            var action = AssertTrue.of("kind-test", true);

            assertThat(action.kind()).isEqualTo("AssertTrue");
        }
    }

    @Nested
    @DisplayName("AssertFalse")
    class AssertFalseTests {

        @Test
        @DisplayName("of(String, boolean) with false passes")
        void ofBooleanPasses() {
            var action = AssertFalse.of("assert-false", false);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("of(String, boolean) with true fails")
        void ofBooleanFails() {
            var action = AssertFalse.of("assert-true", true);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.FAILED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, boolean, String) with false passes and ignores message")
        void ofBooleanWithMessagePasses() {
            var action = AssertFalse.of("assert-false-msg", false, "should not appear");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, boolean, String) with true fails with message")
        void ofBooleanWithMessageFails() {
            var action = AssertFalse.of("assert-true-msg", true, "expected false");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().message()).contains("expected false");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with false passes")
        void ofSupplierPasses() {
            var action = AssertFalse.of("assert-supplier-false", () -> false);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with true fails")
        void ofSupplierFails() {
            var action = AssertFalse.of("assert-supplier-true", () -> true);
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.FAILED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) with false passes and ignores message")
        void ofSupplierWithMessagePasses() {
            var action = AssertFalse.of("assert-supplier-false-msg", () -> false, "should not appear");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status()).isSameAs(Status.PASSED);
            assertThat(root.metadata().message()).isEmpty();
        }

        @Test
        @DisplayName("of(String, BooleanSupplier, String) with true fails with message")
        void ofSupplierWithMessageFails() {
            var action = AssertFalse.of("assert-supplier-true-msg", () -> true, "expected false");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().message()).contains("expected false");
        }

        @Test
        @DisplayName("of(String, BooleanSupplier) with throwing supplier fails with throwable")
        void ofSupplierThrows() {
            var action = AssertFalse.of("assert-throws", () -> {
                throw new RuntimeException("boom");
            });
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().throwable()).isPresent();
            assertThat(root.metadata().throwable().get()).isInstanceOf(RuntimeException.class);
            assertThat(root.metadata().throwable().get().getMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName(
                "of(String, BooleanSupplier, String) with throwing supplier fails with throwable, message not used")
        void ofSupplierWithMessageThrows() {
            var action = AssertFalse.of(
                    "assert-throws-msg",
                    () -> {
                        throw new RuntimeException("boom");
                    },
                    "expected false");
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(root.metadata().throwable()).isPresent();
            assertThat(root.metadata().throwable().get()).isInstanceOf(RuntimeException.class);
            assertThat(root.metadata().throwable().get().getMessage()).isEqualTo("boom");
        }

        @Test
        @DisplayName("non-RUN mode short-circuits without evaluating condition")
        void nonRunModeSkipsAssertion() {
            var evaluated = new AtomicBoolean();
            var action = Static.of("static")
                    .child("before", () -> FailException.fail("before failed"))
                    .child(AssertFalse.of("assert-skip", () -> {
                        evaluated.set(true);
                        return false;
                    }))
                    .resolve();
            var result = Runner.builder().build().run(action);
            var root = result.descriptor().orElseThrow();
            var assertChild = root.children().get(1);

            assertThat(assertChild.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(evaluated.get()).isFalse();
        }

        @Test
        @DisplayName("execute with null context throws NullPointerException")
        void executeWithNullContextThrows() {
            var action = AssertFalse.of("assert-null-context", false);

            assertThatThrownBy(() -> action.execute(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getName returns the supplied name")
        void getNameReturnsSuppliedName() {
            var action = AssertFalse.of("my-assert-false", false);

            assertThat(action.name()).isEqualTo("my-assert-false");
        }

        @Test
        @DisplayName("getKind returns AssertFalse")
        void getKindReturnsAssertFalse() {
            var action = AssertFalse.of("kind-test", false);

            assertThat(action.kind()).isEqualTo("AssertFalse");
        }
    }
}
