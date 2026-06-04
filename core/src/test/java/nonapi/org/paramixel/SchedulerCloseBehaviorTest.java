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

import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler close behavior")
class SchedulerCloseBehaviorTest {

    @Test
    @DisplayName("schedule returns failed future when closing is true")
    void scheduleReturnsFailedFutureWhenClosingIsTrue() throws Exception {
        var scheduler = new Scheduler(1);
        try {
            setClosingTrue(scheduler);

            Action leaf = Step.of("leaf", context -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            var future = scheduler.schedule(root, ExecutionMode.RUN, context);
            assertThat(future.isCompletedExceptionally()).isTrue();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule returns failed future after close() called")
    void scheduleReturnsFailedFutureAfterClose() {
        var scheduler = new Scheduler(1);
        scheduler.close();

        Action leaf = Step.of("leaf", context -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        var future = scheduler.schedule(root, ExecutionMode.RUN, context);
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    @DisplayName("schedule does not enqueue work when closing is true")
    void scheduleDoesNotEnqueueWorkWhenClosing() throws Exception {
        var scheduler = new Scheduler(1);
        try {
            setClosingTrue(scheduler);

            Action leaf = Step.of("leaf", context -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            scheduler.schedule(root, ExecutionMode.RUN, context);

            assertThat(scheduler.readyQueueSize()).isZero();
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("close shuts down global executor")
    void closeShutsDownGlobalExecutor() throws Exception {
        var scheduler = new Scheduler(1);

        Action leaf = Step.of("leaf", context -> sleep(100));
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(),
                Listener.defaultListener(),
                root,
                scheduler,
                new InstanceHolder());

        scheduler.schedule(root, ExecutionMode.RUN, context);
        scheduler.close();

        Field executorField = Scheduler.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        var executor = (ThreadPoolExecutor) executorField.get(scheduler);
        assertThat(executor.isShutdown()).isTrue();
    }

    private static void setClosingTrue(Scheduler scheduler) throws Exception {
        Field closingField = Scheduler.class.getDeclaredField("closing");
        closingField.setAccessible(true);
        closingField.setBoolean(scheduler, true);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
