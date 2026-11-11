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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Context;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteContext")
class ConcreteContextTest {

    private ScopeFrame frame;
    private MutableDescriptor root;
    private Configuration configuration;
    private Listener listener;

    @BeforeEach
    void setUp() {
        frame = ScopeFrame.root();
        root = new ConcreteDescriptor(Step.step("root", context -> {}));
        configuration = Configuration.defaultConfiguration();
        listener = Listener.defaultListener(configuration);
    }

    private ConcreteContext context() {
        return new ConcreteContext(configuration, listener, root, frame);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNullArguments() {
            assertThatThrownBy(() -> new ConcreteContext(null, listener, root, frame))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("configuration is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, null, root, frame))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("listener is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, null, frame))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("descriptor is null");
            assertThatThrownBy(() -> new ConcreteContext(configuration, listener, root, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("frame is null");
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
    @DisplayName("instance lookup reads the scope frame")
    void instanceLookupReadsFrame() {
        var context = context();
        assertThat(context.instance(String.class)).isEmpty();

        frame.bind(String.class, "value");

        assertThat(context.instance(String.class)).contains("value");
        assertThat(context.instance(Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("withFrame keeps the descriptor and swaps the frame")
    void withFrameSwapsFrame() {
        var replacement = ScopeFrame.root();
        replacement.bind(String.class, "replacement");

        var scoped = context().withFrame(replacement);

        assertThat(scoped.descriptor()).isSameAs(root);
        assertThat(scoped.frame()).isSameAs(replacement);
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
            public Descriptor descriptor() {
                throw new UnsupportedOperationException("not used");
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
