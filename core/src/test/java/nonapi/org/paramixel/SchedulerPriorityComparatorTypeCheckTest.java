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

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.DescriptorOrder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler PRIORITY_COMPARATOR type safety")
class SchedulerPriorityComparatorTypeCheckTest {

    private static final Scheduler SCHEDULER;
    private static final ConcreteContext CONTEXT;

    /**
     * Two sibling descriptors of one compiled tree: the earlier child has the lower rank, so a
     * task built for it must outrank a task built for the later child regardless of the order the
     * tasks themselves were submitted in.
     */
    private static final MutableDescriptor FIRST_CHILD;

    private static final MutableDescriptor SECOND_CHILD;

    static {
        SCHEDULER = new Scheduler(1, 32);
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM, "1",
                Configuration.SCHEDULER_QUEUE_CAPACITY, "32",
                Configuration.ANSI, "false"));
        var descriptor = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        CONTEXT = new ConcreteContext(configuration, new Listener() {}, descriptor, SCHEDULER, new InstanceHolder());

        var root = new ConcreteDescriptor(Step.of("root", ctx -> {}));
        FIRST_CHILD = new ConcreteDescriptor(Step.of("first", ctx -> {}));
        SECOND_CHILD = new ConcreteDescriptor(Step.of("second", ctx -> {}));
        root.addChild(FIRST_CHILD);
        root.addChild(SECOND_CHILD);
        DescriptorOrder.compile(root);
    }

    @Test
    @DisplayName("comparator accepts PrioritizedTask instances")
    void comparatorAcceptsPrioritizedTaskInstances() throws Exception {
        var comparator = getPriorityComparator();

        // The later child is submitted first: rank must win over submission sequence.
        var leftTask = createPrioritizedTask(FIRST_CHILD, 5L);
        var rightTask = createPrioritizedTask(SECOND_CHILD, 1L);

        int result = comparator.compare(leftTask, rightTask);
        assertThat(result).isNegative();
    }

    @Test
    @DisplayName("comparator throws ClassCastException when left is not PrioritizedTask")
    void comparatorThrowsWhenLeftIsNotPrioritizedTask() throws Exception {
        var comparator = getPriorityComparator();

        Runnable leftNotPrioritized = () -> {};
        var rightTask = createPrioritizedTask(FIRST_CHILD, 1L);

        assertThatThrownBy(() -> comparator.compare(leftNotPrioritized, rightTask))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("comparator throws ClassCastException when right is not PrioritizedTask")
    void comparatorThrowsWhenRightIsNotPrioritizedTask() throws Exception {
        var comparator = getPriorityComparator();

        var leftTask = createPrioritizedTask(FIRST_CHILD, 1L);
        Runnable rightNotPrioritized = () -> {};

        assertThatThrownBy(() -> comparator.compare(leftTask, rightNotPrioritized))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("comparator handles both inputs as non-PrioritizedTask")
    void comparatorThrowsWhenBothAreNotPrioritizedTask() throws Exception {
        var comparator = getPriorityComparator();

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
        var comparator = getPriorityComparator();
        var queue = new PriorityBlockingQueue<>(11, comparator);

        var prioritizedTask = createPrioritizedTask(FIRST_CHILD, 1L);
        queue.offer(prioritizedTask);
        assertThat(queue).hasSize(1);

        Runnable notPrioritized = () -> {};
        assertThatThrownBy(() -> queue.offer(notPrioritized)).isInstanceOf(ClassCastException.class);
    }

    @SuppressWarnings("unchecked")
    private static Comparator<Runnable> getPriorityComparator() throws Exception {
        var comparatorField = Scheduler.class.getDeclaredField("PRIORITY_COMPARATOR");
        comparatorField.setAccessible(true);
        return (Comparator<Runnable>) comparatorField.get(null);
    }

    private static Runnable createPrioritizedTask(MutableDescriptor descriptor, long sequence) throws Exception {
        var prioritizedTaskClass =
                Class.forName("nonapi.org.paramixel.Scheduler$PrioritizedTask", true, Scheduler.class.getClassLoader());
        var constructor = prioritizedTaskClass.getDeclaredConstructor(
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
                sequence,
                descriptor,
                CONTEXT,
                ExecutionMode.RUN,
                new CompletableFuture<>(),
                null,
                new Semaphore(1),
                SCHEDULER);
    }
}
