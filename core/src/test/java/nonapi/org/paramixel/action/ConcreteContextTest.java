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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import nonapi.org.paramixel.InstanceHolder;
import nonapi.org.paramixel.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteContext")
class ConcreteContextTest {

    private Scheduler scheduler;
    private InstanceHolder instanceHolder;
    private MutableDescriptor root;
    private Configuration configuration;
    private Listener listener;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(1);
        instanceHolder = new InstanceHolder();
        root = new ConcreteDescriptor(Step.of("root", context -> {}));
        configuration = Configuration.defaultConfiguration();
        listener = Listener.defaultListener();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null configuration")
        void rejectsNullConfiguration() {
            assertThatThrownBy(() -> new ConcreteContext(null, listener, root, scheduler, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("configuration is null");
        }

        @Test
        @DisplayName("rejects null listener")
        void rejectsNullListener() {
            assertThatThrownBy(() -> new ConcreteContext(configuration, null, root, scheduler, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("listener is null");
        }

        @Test
        @DisplayName("rejects null descriptor")
        void rejectsNullDescriptor() {
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, null, scheduler, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("descriptor is null");
        }

        @Test
        @DisplayName("rejects null scheduler")
        void rejectsNullScheduler() {
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, root, null, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("scheduler is null");
        }

        @Test
        @DisplayName("rejects null instanceHolder")
        void rejectsNullInstanceHolder() {
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, root, scheduler, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("instanceHolder is null");
        }
    }

    @Nested
    @DisplayName("require()")
    class Require {

        @Test
        @DisplayName("returns ConcreteContext for valid instance")
        void returnsConcreteContextForValidInstance() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);
            assertThat(ConcreteContext.require(context)).isSameAs(context);
        }

        @Test
        @DisplayName("throws NullPointerException for null context")
        void throwsNullPointerExceptionForNull() {
            assertThatThrownBy(() -> ConcreteContext.require(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("context is null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-ConcreteContext")
        void throwsIllegalArgumentExceptionForNonConcreteContext() {
            Context customContext = new Context() {
                @Override
                public Configuration configuration() {
                    return Configuration.of(Map.of());
                }

                @Override
                public <T> java.util.Optional<T> instance(Class<T> type) {
                    return java.util.Optional.empty();
                }
            };
            assertThatThrownBy(() -> ConcreteContext.require(customContext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("context must be a ConcreteContext");
        }
    }

    @Nested
    @DisplayName("runChildren()")
    class RunChildren {

        @Test
        @DisplayName("rejects null mode")
        void rejectsNullMode() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);
            nonapi.org.paramixel.ExecutionMode nullMode = null;

            assertThatThrownBy(() -> context.runChildren(nullMode))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("mode is null");
        }

        @Test
        @DisplayName("rejects null modeFactory")
        void rejectsNullModeFactory() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);
            java.util.function.Function<org.paramixel.api.Descriptor, nonapi.org.paramixel.ExecutionMode> nullFactory =
                    null;

            assertThatThrownBy(() -> context.runChildren(nullFactory))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("modeFactory is null");
        }
    }

    @Nested
    @DisplayName("getters")
    class Getters {

        @Test
        @DisplayName("configuration() returns stored configuration")
        void configurationReturnsStoredValue() {
            var config = Configuration.of(Map.of("key", "value"));
            var context = new ConcreteContext(config, listener, root, scheduler, instanceHolder);

            assertThat(context.configuration()).isSameAs(config);
        }

        @Test
        @DisplayName("listener() returns stored listener")
        void listenerReturnsStoredValue() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);

            assertThat(context.listener()).isSameAs(listener);
        }

        @Test
        @DisplayName("descriptor() returns stored descriptor")
        void descriptorReturnsStoredValue() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);

            assertThat(context.descriptor()).isSameAs(root);
        }

        @Test
        @DisplayName("scheduler() returns stored scheduler")
        void schedulerReturnsStoredValue() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);

            assertThat(context.scheduler()).isSameAs(scheduler);
        }

        @Test
        @DisplayName("instanceHolder() returns stored instanceHolder")
        void instanceHolderReturnsStoredValue() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);

            assertThat(context.instanceHolder()).isSameAs(instanceHolder);
        }
    }

    @Nested
    @DisplayName("withInstanceHolder()")
    class WithInstanceHolder {

        @Test
        @DisplayName("returns new context with different instance holder")
        void returnsNewContextWithDifferentInstanceHolder() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);
            var newHolder = new InstanceHolder();

            var newContext = context.withInstanceHolder(newHolder);

            assertThat(newContext).isNotSameAs(context);
            assertThat(newContext.instanceHolder()).isSameAs(newHolder);
            assertThat(context.instanceHolder()).isSameAs(instanceHolder);
        }

        @Test
        @DisplayName("preserves other state when replacing instance holder")
        void preservesOtherState() {
            var context = new ConcreteContext(configuration, listener, root, scheduler, instanceHolder);
            var newHolder = new InstanceHolder();

            var newContext = context.withInstanceHolder(newHolder);

            assertThat(newContext.configuration()).isSameAs(configuration);
            assertThat(newContext.listener()).isSameAs(listener);
            assertThat(newContext.descriptor()).isSameAs(root);
            assertThat(newContext.scheduler()).isSameAs(scheduler);
        }
    }
}
