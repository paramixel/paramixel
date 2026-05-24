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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.action.DescriptorBuilder;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.spi.action.Mode;

@DisplayName("Scheduler descriptor validation")
class SchedulerDescriptorValidationTest {

    @Test
    @DisplayName("schedule rejects null descriptor")
    void scheduleRejectsNullDescriptor() {
        var scheduler = new AsyncScheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(leaf);
            var context = new ConcreteExecutionContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            assertThatThrownBy(() -> scheduler.schedule(null, Mode.RUN, context))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("descriptor must not be null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule rejects null mode")
    void scheduleRejectsNullMode() {
        var scheduler = new AsyncScheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(leaf);
            var context = new ConcreteExecutionContext(
                    Configuration.defaultConfiguration(),
                    Listener.defaultListener(),
                    root,
                    scheduler,
                    new InstanceHolder());

            assertThatThrownBy(() -> scheduler.schedule(root, null, context))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("mode must not be null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("schedule rejects null parent context")
    void scheduleRejectsNullParentContext() {
        var scheduler = new AsyncScheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(leaf);

            assertThatThrownBy(() -> scheduler.schedule(root, Mode.RUN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("parentContext must not be null");
        } finally {
            scheduler.close();
        }
    }

    @Test
    @DisplayName("duplicate scheduling of same descriptor is rejected")
    void duplicateSchedulingIsRejected() {
        var scheduler = new AsyncScheduler(1);
        try {
            Action<?> leaf = Step.of("leaf", obj -> {});
            MutableDescriptor root = new DescriptorBuilder(Configuration.defaultConfiguration()).discover(leaf);
            var context = new ConcreteExecutionContext(
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
