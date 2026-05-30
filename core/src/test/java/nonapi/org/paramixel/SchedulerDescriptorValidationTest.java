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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Mode;
import org.paramixel.api.action.Step;

@DisplayName("Scheduler descriptor validation")
class SchedulerDescriptorValidationTest {

    @Test
    @DisplayName("schedule rejects null descriptor")
    void scheduleRejectsNullDescriptor() {
        var scheduler = new Scheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            assertThatThrownBy(() -> scheduler.schedule(null, Mode.RUN, context))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("descriptor is null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule rejects null mode")
    void scheduleRejectsNullMode() {
        var scheduler = new Scheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            assertThatThrownBy(() -> scheduler.schedule(root, null, context))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("mode is null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule rejects null parent context")
    void scheduleRejectsNullParentContext() {
        var scheduler = new Scheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);

            assertThatThrownBy(() -> scheduler.schedule(root, Mode.RUN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("parentContext is null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("duplicate scheduling of same descriptor is rejected")
    void duplicateSchedulingIsRejected() {
        var scheduler = new Scheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder().discover(leaf);
            var context = new ConcreteContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            root.markScheduled(Mode.RUN);

            assertThatThrownBy(() -> root.markScheduled(Mode.RUN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already scheduled");
        } finally {
            scheduler.close();
        }
    }
}
