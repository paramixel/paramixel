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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler PRIORITY_COMPARATOR type safety")
class SchedulerPriorityComparatorTypeCheckTest {

    private static final Scheduler SCHEDULER;
    private static final ConcreteContext CONTEXT;

    static {
        SCHEDULER = new Scheduler(1, 32);
        var config = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, "1",
                Configuration.SCHEDULER_QUEUE_CAPACITY, "32",
                Configuration.ANSI, "false"));
        var descriptor = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        CONTEXT = new ConcreteContext(config, new Listener() {}, descriptor, SCHEDULER, new InstanceHolder());
    }

    @Test
    @DisplayName("comparator accepts PrioritizedTask instances")
    void comparatorAcceptsPrioritizedTaskInstances() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        SchedulerPriorityKey leftKey = SchedulerPriorityKey.root().child(0).child(0);
        SchedulerPriorityKey rightKey = SchedulerPriorityKey.root().child(0).child(1);
        Runnable leftTask = createPrioritizedTask(leftKey, 1L);
        Runnable rightTask = createPrioritizedTask(rightKey, 2L);

        int result = comparator.compare(leftTask, rightTask);
        assertThat(result).isNegative();
    }

    @Test
    @DisplayName("comparator throws ClassCastException when left is not PrioritizedTask")
    void comparatorThrowsWhenLeftIsNotPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        Runnable leftNotPrioritized = () -> {};
        SchedulerPriorityKey rightKey = SchedulerPriorityKey.root().child(0);
        Runnable rightTask = createPrioritizedTask(rightKey, 1L);

        assertThatThrownBy(() -> comparator.compare(leftNotPrioritized, rightTask))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("comparator throws ClassCastException when right is not PrioritizedTask")
    void comparatorThrowsWhenRightIsNotPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        SchedulerPriorityKey leftKey = SchedulerPriorityKey.root().child(0);
        Runnable leftTask = createPrioritizedTask(leftKey, 1L);
        Runnable rightNotPrioritized = () -> {};

        assertThatThrownBy(() -> comparator.compare(leftTask, rightNotPrioritized))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("comparator handles both inputs as non-PrioritizedTask")
    void comparatorThrowsWhenBothAreNotPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        Runnable leftNotPrioritized = () -> {};
        Runnable rightNotPrioritized = () -> {};

        assertThatThrownBy(() -> comparator.compare(leftNotPrioritized, rightNotPrioritized))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("priority blocking queue with comparator rejects non-PrioritizedTask")
    void priorityBlockingQueueRejectsNonPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();
        var queue = new PriorityBlockingQueue<>(11, comparator);

        SchedulerPriorityKey key = SchedulerPriorityKey.root().child(0);
        Runnable prioritizedTask = createPrioritizedTask(key, 1L);
        queue.offer(prioritizedTask);
        assertThat(queue).hasSize(1);

        Runnable notPrioritized = () -> {};
        assertThatThrownBy(() -> queue.offer(notPrioritized)).isInstanceOf(ClassCastException.class);
    }

    @SuppressWarnings("unchecked")
    private static Comparator<Runnable> getPriorityComparator() throws Exception {
        Field comparatorField = Scheduler.class.getDeclaredField("PRIORITY_COMPARATOR");
        comparatorField.setAccessible(true);
        return (Comparator<Runnable>) comparatorField.get(null);
    }

    private static Runnable createPrioritizedTask(SchedulerPriorityKey priorityKey, long sequence) throws Exception {
        Class<?> prioritizedTaskClass =
                Class.forName("nonapi.org.paramixel.Scheduler$PrioritizedTask", true, Scheduler.class.getClassLoader());
        var constructor = prioritizedTaskClass.getDeclaredConstructor(
                SchedulerPriorityKey.class,
                long.class,
                MutableDescriptor.class,
                ConcreteContext.class,
                ExecutionMode.class,
                CompletableFuture.class,
                Scheduler.ExecutionCallback.class,
                Semaphore.class,
                Scheduler.class);
        constructor.setAccessible(true);
        return (Runnable) constructor.newInstance(
                priorityKey,
                sequence,
                CONTEXT.descriptor(),
                CONTEXT,
                ExecutionMode.RUN,
                new CompletableFuture<>(),
                null,
                new Semaphore(1),
                SCHEDULER);
    }
}
