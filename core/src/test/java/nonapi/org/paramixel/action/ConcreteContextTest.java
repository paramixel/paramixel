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

import java.util.Optional;
import nonapi.org.paramixel.InstanceHolder;
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

    private InstanceHolder instanceHolder;
    private MutableDescriptor root;
    private Configuration configuration;
    private Listener listener;

    @BeforeEach
    void setUp() {
        instanceHolder = new InstanceHolder();
        root = new ConcreteDescriptor(Step.of("root", context -> {}));
        configuration = Configuration.defaultConfiguration();
        listener = Listener.defaultListener();
    }

    private ConcreteContext context() {
        return new ConcreteContext(configuration, listener, root, instanceHolder);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNullArguments() {
            assertThatThrownBy(() -> new ConcreteContext(null, listener, root, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("configuration is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, null, root, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("listener is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, null, instanceHolder))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("descriptor is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, root, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("instanceHolder is null");
        }
    }

    @Test
    @DisplayName("exposes configuration, listener, and descriptor")
    void exposesRunData() {
        var context = context();

        assertThat(context.configuration()).isSameAs(configuration);
        assertThat(context.listener()).isSameAs(listener);
        assertThat(context.descriptor()).isSameAs(root);
    }

    @Test
    @DisplayName("instance lookup reads the scoped holder")
    void instanceLookupReadsHolder() {
        var context = context();
        assertThat(context.instance(String.class)).isEmpty();

        instanceHolder.set("value");

        assertThat(context.instance(String.class)).contains("value");
        assertThat(context.instance(Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("withInstanceHolder keeps the descriptor and swaps the holder")
    void withInstanceHolderSwapsHolder() {
        var replacement = new InstanceHolder();
        replacement.set("replacement");

        var scoped = context().withInstanceHolder(replacement);

        assertThat(scoped.descriptor()).isSameAs(root);
        assertThat(scoped.instanceHolder()).isSameAs(replacement);
        assertThat(scoped.instance(String.class)).contains("replacement");
    }

    @Test
    @DisplayName("require accepts concrete contexts and rejects foreign implementations")
    void requireChecksType() {
        var concrete = context();
        assertThat(ConcreteContext.require(concrete)).isSameAs(concrete);

        Context foreign = new Context() {
            @Override
            public Configuration configuration() {
                return configuration;
            }

            @Override
            public <T> Optional<T> instance(final Class<T> type) {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() -> ConcreteContext.require(foreign))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context must be a ConcreteContext");
    }
}
