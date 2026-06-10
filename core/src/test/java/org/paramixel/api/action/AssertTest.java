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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.FailException;

@DisplayName("Assert")
@SuppressWarnings("removal")
class AssertTest {

    private static final Context context = new Context() {
        @Override
        public Configuration configuration() {
            return Configuration.of(Map.of());
        }

        @Override
        public <T> Optional<T> instance(final Class<T> type) {
            return Optional.empty();
        }
    };

    @Test
    @DisplayName("expected true and actual true passes")
    void expectedTrueActualTruePasses() {
        var action = Assert.of("assert-true", true, true);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("expected true and actual false fails")
    void expectedTrueActualFalseFails() {
        var action = Assert.of("assert-false", true, false);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).contains("failed");
    }

    @Test
    @DisplayName("expected false and actual false passes")
    void expectedFalseActualFalsePasses() {
        var action = Assert.of("assert-false", false, false);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("expected false and actual true fails")
    void expectedFalseActualTrueFails() {
        var action = Assert.of("assert-true", false, true);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).contains("failed");
    }

    @Test
    @DisplayName("message-bearing passing assertion ignores message")
    void messageBearingPassingAssertionIgnoresMessage() {
        var action = Assert.of("assert-true-msg", true, true, "should not appear");
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.message()).isEmpty();
    }

    @Test
    @DisplayName("message-bearing failing assertion includes message")
    void messageBearingFailingAssertionIncludesMessage() {
        var action = Assert.of("assert-false-msg", true, false, "expected true");
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).contains("expected true");
    }

    @Test
    @DisplayName("supplier assertion passes when actual equals expected")
    void supplierAssertionPassesWhenActualEqualsExpected() {
        var action = Assert.of("assert-supplier-true", true, () -> true);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
    }

    @Test
    @DisplayName("supplier assertion fails when actual differs from expected")
    void supplierAssertionFailsWhenActualDiffersFromExpected() {
        var action = Assert.of("assert-supplier-false", true, () -> false);
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).contains("failed");
    }

    @Test
    @DisplayName("supplier assertion with message passes and ignores message")
    void supplierAssertionWithMessagePassesAndIgnoresMessage() {
        var action = Assert.of("assert-supplier-true-msg", true, () -> true, "should not appear");
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.message()).isEmpty();
    }

    @Test
    @DisplayName("supplier assertion with message fails with message")
    void supplierAssertionWithMessageFailsWithMessage() {
        var action = Assert.of("assert-supplier-false-msg", true, () -> false, "expected true");
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.message()).contains("expected true");
    }

    @Test
    @DisplayName("throwing supplier fails with throwable")
    void throwingSupplierFailsWithThrowable() {
        var action = Assert.of("assert-throws", true, () -> {
            throw new RuntimeException("boom");
        });
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.throwable()).isPresent();
        assertThat(root.throwable().get()).isInstanceOf(RuntimeException.class);
        assertThat(root.throwable().get().getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("throwing supplier with message fails with throwable, message not used")
    void throwingSupplierWithMessageFailsWithThrowable() {
        var action = Assert.of(
                "assert-throws-msg",
                true,
                () -> {
                    throw new RuntimeException("boom");
                },
                "expected true");
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.throwable()).isPresent();
        assertThat(root.throwable().get()).isInstanceOf(RuntimeException.class);
        assertThat(root.throwable().get().getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("direct throwableConsumer fails without message")
    void directThrowableConsumerFailsWithoutMessage() {
        var action = Assert.of("assert-false-direct", true, false);

        assertThatThrownBy(() -> action.throwableConsumer().accept(context)).isInstanceOf(FailException.class);
    }

    @Test
    @DisplayName("direct throwableConsumer fails with message")
    void directThrowableConsumerFailsWithMessage() {
        var action = Assert.of("assert-false-msg-direct", true, false, "expected true");

        assertThatThrownBy(() -> action.throwableConsumer().accept(context))
                .isInstanceOf(FailException.class)
                .hasMessage("expected true");
    }

    @Test
    @DisplayName("non-RUN mode short-circuits without evaluating actual supplier")
    void nonRunModeSkipsAssertion() {
        var evaluated = new AtomicBoolean();
        var action = Static.builder("static")
                .before(Step.of("before", context -> FailException.fail("before failed")))
                .body(Assert.of("assert-skip", true, () -> {
                    evaluated.set(true);
                    return true;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
        assertThat(evaluated.get()).isFalse();
    }

    @Test
    @DisplayName("throwableConsumer with null context throws NullPointerException")
    void throwableConsumerWithNullContextThrows() {
        var action = Assert.of("assert-null-context", true, true);

        assertThatThrownBy(() -> action.throwableConsumer().accept(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getName returns the supplied name")
    void getNameReturnsSuppliedName() {
        var action = Assert.of("my-assert", true, true);

        assertThat(action.displayName()).isEqualTo("my-assert");
    }

    @Test
    @DisplayName("action is instance of Assert")
    void actionIsInstanceOfAssert() {
        var action = Assert.of("kind-test", true, true);

        assertThat(action).isInstanceOf(Assert.class);
    }
}
