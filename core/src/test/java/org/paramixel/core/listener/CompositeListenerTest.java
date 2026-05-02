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

package org.paramixel.core.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Noop;

@DisplayName("CompositeListener")
class CompositeListenerTest {

    @Test
    @DisplayName("of(List) rejects null list")
    void ofListRejectsNullList() {
        assertThatThrownBy(() -> CompositeListener.of((List<Listener>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners must not be null");
    }

    @Test
    @DisplayName("of(List) rejects empty list")
    void ofListRejectsEmptyList() {
        assertThatThrownBy(() -> CompositeListener.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners must not be empty");
    }

    @Test
    @DisplayName("of(List) rejects null element in list")
    void ofListRejectsNullElementInList() {
        List<Listener> listeners = new ArrayList<>();
        listeners.add(null);
        assertThatThrownBy(() -> CompositeListener.of(listeners))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners must not contain null elements");
    }

    @Test
    @DisplayName("of(List) creates defensive copy — adding to original does not affect listener")
    void ofListCreatesDefensiveCopyAddingDoesNotAffectListener() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");
        List<Listener> listeners = new ArrayList<>();
        listeners.add(first);
        listeners.add(second);

        CompositeListener composite = CompositeListener.of(listeners);
        listeners.add(new OrderTrackingListener("third"));

        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.runStartedCount).isEqualTo(1);
        assertThat(second.runStartedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("of(List) creates defensive copy — removing from original does not affect listener")
    void ofListCreatesDefensiveCopyRemovingDoesNotAffectListener() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");
        List<Listener> listeners = new ArrayList<>();
        listeners.add(first);
        listeners.add(second);

        CompositeListener composite = CompositeListener.of(listeners);
        listeners.remove(1);

        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.runStartedCount).isEqualTo(1);
        assertThat(second.runStartedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("of(List) creates defensive copy — clearing original does not affect listener")
    void ofListCreatesDefensiveCopyClearingDoesNotAffectListener() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");
        List<Listener> listeners = new ArrayList<>();
        listeners.add(first);
        listeners.add(second);

        CompositeListener composite = CompositeListener.of(listeners);
        listeners.clear();

        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.runStartedCount).isEqualTo(1);
        assertThat(second.runStartedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("of(varargs) rejects null array")
    void ofVarargsRejectsNullArray() {
        assertThatThrownBy(() -> CompositeListener.of((Listener[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners must not be null");
    }

    @Test
    @DisplayName("of(varargs) rejects zero listeners")
    void ofVarargsRejectsZeroListeners() {
        assertThatThrownBy(() -> CompositeListener.of())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("listeners must not be empty");
    }

    @Test
    @DisplayName("of(varargs) rejects null element")
    void ofVarargsRejectsNullElement() {
        assertThatThrownBy(() -> CompositeListener.of(new OrderTrackingListener("a"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listeners must not contain null elements");
    }

    @Test
    @DisplayName("delegates runStarted to all listeners in order")
    void delegatesRunStartedToAllListenersInOrder() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");
        OrderTrackingListener third = new OrderTrackingListener("third");

        CompositeListener composite = CompositeListener.of(first, second, third);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.runStartedCount).isEqualTo(1);
        assertThat(second.runStartedCount).isEqualTo(1);
        assertThat(third.runStartedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("delegates beforeAction to all listeners in order")
    void delegatesBeforeActionToAllListenersInOrder() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");

        CompositeListener composite = CompositeListener.of(first, second);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.beforeActionCount).isEqualTo(1);
        assertThat(second.beforeActionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("delegates actionThrowable to all listeners in order")
    void delegatesActionThrowableToAllListenersInOrder() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");

        CompositeListener composite = CompositeListener.of(first, second);
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(org.paramixel.core.action.Direct.of("fail", ctx -> {
            throw new RuntimeException("boom");
        }));

        assertThat(first.actionThrowableCount).isEqualTo(1);
        assertThat(second.actionThrowableCount).isEqualTo(1);
    }

    @Test
    @DisplayName("delegates afterAction to all listeners in order")
    void delegatesAfterActionToAllListenersInOrder() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");

        CompositeListener composite = CompositeListener.of(first, second);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.afterActionCount).isEqualTo(1);
        assertThat(second.afterActionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("delegates runCompleted to all listeners in order")
    void delegatesRunCompletedToAllListenersInOrder() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");

        CompositeListener composite = CompositeListener.of(first, second);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(first.runCompletedCount).isEqualTo(1);
        assertThat(second.runCompletedCount).isEqualTo(1);
    }

    private static class OrderTrackingListener implements Listener {
        final String name;
        int runStartedCount;
        int beforeActionCount;
        int actionThrowableCount;
        int afterActionCount;
        int runCompletedCount;
        final List<String> callOrder;

        OrderTrackingListener(String name) {
            this(name, new ArrayList<>());
        }

        OrderTrackingListener(String name, List<String> sharedOrder) {
            this.name = name;
            this.callOrder = sharedOrder;
        }

        @Override
        public void runStarted(Runner runner, Action action) {
            runStartedCount++;
            callOrder.add(name + ".runStarted");
        }

        @Override
        public void beforeAction(Context context, Action action) {
            beforeActionCount++;
            callOrder.add(name + ".beforeAction");
        }

        @Override
        public void actionThrowable(Context context, Action action, Throwable throwable) {
            actionThrowableCount++;
            callOrder.add(name + ".actionThrowable");
        }

        @Override
        public void afterAction(Context context, Action action, Result result) {
            afterActionCount++;
            callOrder.add(name + ".afterAction");
        }

        @Override
        public void runCompleted(Runner runner, Action action) {
            runCompletedCount++;
            callOrder.add(name + ".runCompleted");
        }
    }

    @Test
    @DisplayName("delegates runStarted in strict call order")
    void delegatesRunStartedInStrictCallOrder() {
        List<String> order = new ArrayList<>();
        OrderTrackingListener first = new OrderTrackingListener("first", order);
        OrderTrackingListener second = new OrderTrackingListener("second", order);

        CompositeListener composite = CompositeListener.of(first, second);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(order.indexOf("first.runStarted")).isNotEqualTo(-1);
        assertThat(order.indexOf("second.runStarted")).isNotEqualTo(-1);
        assertThat(order.indexOf("first.runStarted")).isLessThan(order.indexOf("second.runStarted"));
    }

    @Test
    @DisplayName("exception in first listener stops iteration")
    void exceptionInFirstListenerStopsIteration() {
        RuntimeException expected = new RuntimeException("stop here");
        OrderTrackingListener second = new OrderTrackingListener("second");
        Listener throwingListener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                throw expected;
            }
        };

        CompositeListener composite = CompositeListener.of(throwingListener, second);

        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();

        runner.run(noop);

        assertThat(second.beforeActionCount).isEqualTo(0);
    }

    @Test
    @DisplayName("single listener composite delegates all callbacks")
    void singleListenerCompositeDelegatesAllCallbacks() {
        OrderTrackingListener sole = new OrderTrackingListener("sole");

        CompositeListener composite = CompositeListener.of(sole);
        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(composite).build();
        runner.run(noop);

        assertThat(sole.runStartedCount).isEqualTo(1);
        assertThat(sole.beforeActionCount).isEqualTo(1);
        assertThat(sole.afterActionCount).isEqualTo(1);
        assertThat(sole.runCompletedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("composite of composite listener delegates to all nested delegates")
    void compositeOfCompositeDelegatesToAllNestedDelegates() {
        OrderTrackingListener first = new OrderTrackingListener("first");
        OrderTrackingListener second = new OrderTrackingListener("second");

        CompositeListener inner = CompositeListener.of(first, second);
        OrderTrackingListener third = new OrderTrackingListener("third");
        CompositeListener outer = CompositeListener.of(inner, third);

        Noop noop = Noop.of("test");
        Runner runner = Runner.builder().listener(outer).build();
        runner.run(noop);

        assertThat(first.runStartedCount).isEqualTo(1);
        assertThat(second.runStartedCount).isEqualTo(1);
        assertThat(third.runStartedCount).isEqualTo(1);
    }
}
