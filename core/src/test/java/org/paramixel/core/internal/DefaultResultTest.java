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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Result;
import org.paramixel.core.action.Noop;

@DisplayName("DefaultResult")
class DefaultResultTest {

    @Test
    @DisplayName("creates passing result via pass factory")
    void createsPassingResultViaPassFactory() {
        var action = Noop.of("test");
        var timing = Duration.ofMillis(100);

        var result = DefaultResult.pass(action, timing);

        assertThat(result.action()).isSameAs(action);
        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.timing()).isEqualTo(timing);
        assertThat(result.failure()).isEmpty();
        assertThat(result.children()).isEmpty();
        assertThat(result.parent()).isEmpty();
    }

    @Test
    @DisplayName("creates passing result with children via pass factory")
    void createsPassingResultWithChildrenViaPassFactory() {
        var parentAction = Noop.of("parent");
        var child1Action = Noop.of("child1");
        var child2Action = Noop.of("child2");
        var parentTiming = Duration.ofMillis(200);
        var child1Timing = Duration.ofMillis(50);
        var child2Timing = Duration.ofMillis(100);

        var child1 = DefaultResult.pass(child1Action, child1Timing);
        var child2 = DefaultResult.pass(child2Action, child2Timing);

        var parent = DefaultResult.pass(parentAction, parentTiming, List.of(child1, child2));

        assertThat(parent.action()).isSameAs(parentAction);
        assertThat(parent.status()).isEqualTo(Result.Status.PASS);
        assertThat(parent.timing()).isEqualTo(parentTiming);
        assertThat(parent.failure()).isEmpty();
        assertThat(parent.children()).hasSize(2);
        assertThat(parent.children().get(0)).isSameAs(child1);
        assertThat(parent.children().get(1)).isSameAs(child2);
        assertThat(parent.parent()).isEmpty();

        assertThat(child1.parent()).contains(parent);
        assertThat(child2.parent()).contains(parent);
    }

    @Test
    @DisplayName("creates failing result via fail factory")
    void createsFailingResultViaFailFactory() {
        var action = Noop.of("test");
        var timing = Duration.ofMillis(150);
        Throwable failure = new RuntimeException("test failure");

        var result = DefaultResult.fail(action, timing, failure);

        assertThat(result.action()).isSameAs(action);
        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.timing()).isEqualTo(timing);
        assertThat(result.failure()).contains(failure);
        assertThat(result.children()).isEmpty();
        assertThat(result.parent()).isEmpty();
    }

    @Test
    @DisplayName("creates failing result with children via fail factory")
    void createsFailingResultWithChildrenViaFailFactory() {
        var parentAction = Noop.of("parent");
        var child1Action = Noop.of("child1");
        var child2Action = Noop.of("child2");
        var parentTiming = Duration.ofMillis(200);
        Throwable failure = new RuntimeException("parent failure");
        var child1Timing = Duration.ofMillis(50);
        var child2Timing = Duration.ofMillis(100);

        var child1 = DefaultResult.pass(child1Action, child1Timing);
        var child2 = DefaultResult.fail(child2Action, child2Timing, new RuntimeException("child failure"));

        var parent = DefaultResult.fail(parentAction, parentTiming, failure, List.of(child1, child2));

        assertThat(parent.action()).isSameAs(parentAction);
        assertThat(parent.status()).isEqualTo(Result.Status.FAIL);
        assertThat(parent.timing()).isEqualTo(parentTiming);
        assertThat(parent.failure()).contains(failure);
        assertThat(parent.children()).hasSize(2);
        assertThat(parent.children().get(0)).isSameAs(child1);
        assertThat(parent.children().get(1)).isSameAs(child2);
        assertThat(parent.parent()).isEmpty();

        assertThat(child1.parent()).contains(parent);
        assertThat(child2.parent()).contains(parent);
    }

    @Test
    @DisplayName("creates skipped result via skip factory")
    void createsSkippedResultViaSkipFactory() {
        var action = Noop.of("test");
        var timing = Duration.ZERO;

        var result = DefaultResult.skip(action, timing);

        assertThat(result.action()).isSameAs(action);
        assertThat(result.status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.timing()).isEqualTo(timing);
        assertThat(result.failure()).isEmpty();
        assertThat(result.children()).isEmpty();
        assertThat(result.parent()).isEmpty();
    }

    @Test
    @DisplayName("creates skipped result with children via skip factory")
    void createsSkippedResultWithChildrenViaSkipFactory() {
        var parentAction = Noop.of("parent");
        var child1Action = Noop.of("child1");
        var child2Action = Noop.of("child2");
        var parentTiming = Duration.ZERO;
        var child1Timing = Duration.ofMillis(50);
        var child2Timing = Duration.ZERO;

        var child1 = DefaultResult.pass(child1Action, child1Timing);
        var child2 = DefaultResult.skip(child2Action, child2Timing);

        var parent = DefaultResult.skip(parentAction, parentTiming, List.of(child1, child2));

        assertThat(parent.action()).isSameAs(parentAction);
        assertThat(parent.status()).isEqualTo(Result.Status.SKIP);
        assertThat(parent.timing()).isEqualTo(parentTiming);
        assertThat(parent.failure()).isEmpty();
        assertThat(parent.children()).hasSize(2);
        assertThat(parent.children().get(0)).isSameAs(child1);
        assertThat(parent.children().get(1)).isSameAs(child2);
        assertThat(parent.parent()).isEmpty();

        assertThat(child1.parent()).contains(parent);
        assertThat(child2.parent()).contains(parent);
    }

    @Test
    @DisplayName("creates result via of factory with all parameters")
    void createsResultViaOfFactoryWithAllParameters() {
        var action = Noop.of("test");
        var status = Result.Status.PASS;
        var timing = Duration.ofMillis(123);
        Throwable failure = null;
        List<Result> children = List.of();

        var result = DefaultResult.of(action, status, timing, failure, children);

        assertThat(result.action()).isSameAs(action);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.timing()).isEqualTo(timing);
        assertThat(result.failure()).isEmpty();
        assertThat(result.children()).isEmpty();
        assertThat(result.parent()).isEmpty();
    }

    @Test
    @DisplayName("fails to create result with null action")
    void failsToCreateResultWithNullAction() {
        Duration timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> DefaultResult.pass(null, timing))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    @DisplayName("fails to create result with null status")
    void failsToCreateResultWithNullStatus() {
        Noop action = Noop.of("test");
        Duration timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> DefaultResult.of(action, null, timing, null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("fails to create result with null timing")
    void failsToCreateResultWithNullTiming() {
        var action = Noop.of("test");

        assertThatThrownBy(() -> DefaultResult.pass(action, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("timing must not be null");
    }

    @Test
    @DisplayName("fails to create result with null children")
    void failsToCreateResultWithNullChildren() {
        var action = Noop.of("test");
        var timing = Duration.ofMillis(100);

        assertThatThrownBy(() -> DefaultResult.of(action, Result.Status.PASS, timing, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("children must not be null");
    }

    @Test
    @DisplayName("returns empty parent for root results")
    void returnsEmptyParentForRootResults() {
        var action = Noop.of("root");
        var root = DefaultResult.pass(action, Duration.ofMillis(100));

        assertThat(root.parent()).isEmpty();
    }

    @Test
    @DisplayName("returns parent for child results when created with children")
    void returnsParentForChildResultsWhenCreatedWithChildren() {
        var parentAction = Noop.of("parent");
        var childAction = Noop.of("child");
        var child = DefaultResult.pass(childAction, Duration.ofMillis(50));

        var parent = DefaultResult.pass(parentAction, Duration.ofMillis(200), List.of(child));

        assertThat(child.parent()).isPresent().contains(parent);
        assertThat(parent.parent()).isEmpty();
    }

    @Test
    @DisplayName("children list is unmodifiable")
    void childrenListIsUnmodifiable() {
        var action = Noop.of("test");
        var child1 = DefaultResult.pass(Noop.of("child1"), Duration.ofMillis(50));
        var child2 = DefaultResult.pass(Noop.of("child2"), Duration.ofMillis(100));
        var result = DefaultResult.pass(action, Duration.ofMillis(200), List.of(child1, child2));

        assertThatThrownBy(() -> result.children().add(child1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("multi-level result hierarchy maintains parent references")
    void multiLevelResultHierarchyMaintainsParentReferences() {
        var rootAction = Noop.of("root");
        var level1Action = Noop.of("level1");
        var level2Action = Noop.of("level2");
        var leafAction = Noop.of("leaf");

        var leaf = DefaultResult.pass(leafAction, Duration.ofMillis(10));
        var level2 = DefaultResult.pass(level2Action, Duration.ofMillis(50), List.of(leaf));
        var level1 = DefaultResult.pass(level1Action, Duration.ofMillis(100), List.of(level2));
        var root = DefaultResult.pass(rootAction, Duration.ofMillis(200), List.of(level1));

        assertThat(root.parent()).isEmpty();
        assertThat(level1.parent()).contains(root);
        assertThat(level2.parent()).contains(level1);
        assertThat(leaf.parent()).contains(level2);
    }

    @Test
    @DisplayName("toString returns expected format")
    void toStringReturnsExpectedFormat() {
        var action = Noop.of("test");
        var result = DefaultResult.pass(action, Duration.ofMillis(123));

        assertThat(result.toString()).isEqualTo("PASS | 123 ms");
    }
}
