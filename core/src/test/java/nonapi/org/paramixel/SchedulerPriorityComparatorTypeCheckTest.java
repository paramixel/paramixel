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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scheduler PRIORITY_COMPARATOR type safety")
class SchedulerPriorityComparatorTypeCheckTest {

    @Test
    @DisplayName("comparator accepts PrioritizedTask instances")
    void comparatorAcceptsPrioritizedTaskInstances() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        Runnable left = () -> {};
        Runnable right = () -> {};
        SchedulerPriorityKey leftKey = SchedulerPriorityKey.root().child(0).child(0);
        SchedulerPriorityKey rightKey = SchedulerPriorityKey.root().child(0).child(1);
        Runnable leftTask = createPrioritizedTask(left, leftKey, 1L);
        Runnable rightTask = createPrioritizedTask(right, rightKey, 2L);

        int result = comparator.compare(leftTask, rightTask);
        assertThat(result).isNegative();
    }

    @Test
    @DisplayName("comparator throws ClassCastException when left is not PrioritizedTask")
    void comparatorThrowsWhenLeftIsNotPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        Runnable leftNotPrioritized = () -> {};
        Runnable right = () -> {};
        SchedulerPriorityKey rightKey = SchedulerPriorityKey.root().child(0);
        Runnable rightTask = createPrioritizedTask(right, rightKey, 1L);

        assertThatThrownBy(() -> comparator.compare(leftNotPrioritized, rightTask))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("PRIORITY_COMPARATOR requires PrioritizedTask")
                .hasMessageContaining("SchedulerPriorityComparatorTypeCheckTest");
    }

    @Test
    @DisplayName("comparator throws ClassCastException when right is not PrioritizedTask")
    void comparatorThrowsWhenRightIsNotPrioritizedTask() throws Exception {
        Comparator<Runnable> comparator = getPriorityComparator();

        Runnable left = () -> {};
        SchedulerPriorityKey leftKey = SchedulerPriorityKey.root().child(0);
        Runnable leftTask = createPrioritizedTask(left, leftKey, 1L);
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

        Runnable delegate = () -> {};
        SchedulerPriorityKey key = SchedulerPriorityKey.root().child(0);
        Runnable prioritizedTask = createPrioritizedTask(delegate, key, 1L);
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

    private static Runnable createPrioritizedTask(Runnable delegate, SchedulerPriorityKey priorityKey, long sequence)
            throws Exception {
        Class<?> prioritizedTaskClass =
                Class.forName("nonapi.org.paramixel.Scheduler$PrioritizedTask", true, Scheduler.class.getClassLoader());
        Constructor<?> constructor =
                prioritizedTaskClass.getDeclaredConstructor(Runnable.class, SchedulerPriorityKey.class, long.class);
        constructor.setAccessible(true);
        return (Runnable) constructor.newInstance(delegate, priorityKey, sequence);
    }
}
