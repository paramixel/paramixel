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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.discoveryfixtures.AlphaFixture;
import org.paramixel.api.discoveryfixtures.BuilderFactoryFixture;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;
import org.paramixel.api.discoveryfixtures.InvalidFactoryFixture;
import org.paramixel.api.discoveryfixtures.SkippingFactoryFixture;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * The {@link Paramixel.TaskFactory} contract: both supported return types are accepted, {@code null} is a recorded
 * skip, and an unsupported shape fails discovery with {@link ResolverException}.
 */
class TaskFactoryDiscoveryTest {

    @BeforeEach
    void resetFixtures() {
        DiscoveryFixtures.reset();
    }

    private static List<String> allNames(final TaskResult node) {
        final var names = new ArrayList<String>();
        names.add(node.name());
        for (TaskResult child : node.children()) {
            names.addAll(allNames(child));
        }
        return names;
    }

    private static Result discover(final Class<?> fixture) {
        return Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                .build()
                .run(Selector.builder().classOf(fixture).build());
    }

    @Test
    @DisplayName("FACTORY-01: a factory may return a built Task")
    void taskReturningFactoryIsAccepted() {
        final var result = discover(AlphaFixture.class);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(DiscoveryFixtures.RECORDED).contains("alpha");
        assertThat(allNames(result.root().orElseThrow())).contains("alpha");
    }

    @Test
    @DisplayName("FACTORY-02: a factory may return a builder, which the framework builds")
    void builderReturningFactoryIsAccepted() {
        final var result = discover(BuilderFactoryFixture.class);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(DiscoveryFixtures.RECORDED).contains("builder-factory");
        assertThat(allNames(result.root().orElseThrow())).contains("builder-factory");
    }

    @Test
    @DisplayName("FACTORY-03: a null factory is a recorded skip, not a failure")
    void nullFactoryIsASkip() {
        final var warnings = new ArrayList<String>();
        final var runner = Runner.builder()
                .listener(new Listener() {
                    @Override
                    public void onWarning(final String message) {
                        warnings.add(message);
                    }
                })
                .configuration(
                        Configuration.of(Map.of(Configuration.ANSI, "false", Configuration.FAIL_IF_NO_TESTS, "false")))
                .build();

        final var result = runner.run(
                Selector.builder().classOf(SkippingFactoryFixture.class).build());

        assertThat(warnings)
                .anySatisfy(warning -> assertThat(warning)
                        .contains(SkippingFactoryFixture.class.getName() + "#factory")
                        .contains("returned null"));
        assertThat(result.failures()).isEmpty();
    }

    @Test
    @DisplayName("FACTORY-04: an unsupported return type fails discovery with ResolverException")
    void invalidFactoryShapeIsRejected() {
        assertThatThrownBy(() -> discover(InvalidFactoryFixture.class))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining(InvalidFactoryFixture.class.getName())
                .hasMessageContaining("return type must be Task or TaskBuilder");
    }

    @Test
    @DisplayName("FACTORY-05: the annotation name is TaskFactory")
    void annotationIsNamedTaskFactory() {
        assertThat(Paramixel.TaskFactory.class.getSimpleName()).isEqualTo("TaskFactory");
        assertThatThrownBy(() -> Class.forName("org.paramixel.api.Paramixel$Factory"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("FACTORY-06: the legacy Tasks entry point is gone")
    void tasksEntryPointIsRemoved() {
        assertThatThrownBy(() -> Class.forName("org.paramixel.api.Tasks")).isInstanceOf(ClassNotFoundException.class);
    }
}
