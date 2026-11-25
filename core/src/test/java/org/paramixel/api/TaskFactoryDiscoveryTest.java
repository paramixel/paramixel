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
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.discoveryfixtures.AlphaFixture;
import org.paramixel.api.discoveryfixtures.AmbiguousShapeFactoryFixture;
import org.paramixel.api.discoveryfixtures.BuilderFactoryFixture;
import org.paramixel.api.discoveryfixtures.ConfiguredBuilderFactoryFixture;
import org.paramixel.api.discoveryfixtures.ConfiguredTaskFactoryFixture;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;
import org.paramixel.api.discoveryfixtures.InvalidFactoryFixture;
import org.paramixel.api.discoveryfixtures.NonConfigurationParameterFactoryFixture;
import org.paramixel.api.discoveryfixtures.ShapeMismatchBaseFixture;
import org.paramixel.api.discoveryfixtures.ShapeMismatchSubFixture;
import org.paramixel.api.discoveryfixtures.SkippingFactoryFixture;
import org.paramixel.api.discoveryfixtures.TooManyParametersFactoryFixture;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * The {@link Paramixel.TaskFactory} contract: both supported return types are accepted with or without the optional
 * {@link Configuration} parameter, {@code null} is a recorded skip, and an unsupported shape fails discovery with
 * {@link ResolverException}.
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
    void bothFactoryReturnFormsRetainCompilationDiagnostics() {
        for (Class<?> fixture : List.of(InvalidBuiltTaskFactory.class, InvalidReturnedBuilderFactory.class)) {
            assertThatThrownBy(() -> discover(fixture))
                    .isInstanceOfSatisfying(
                            CompilationException.class,
                            exception -> assertThat(exception.diagnostics())
                                    .anySatisfy(
                                            diagnostic -> assertThat(diagnostic).contains("missing")));
        }
    }

    static final class InvalidBuiltTaskFactory {
        @Paramixel.TaskFactory
        public static Task factory() {
            return Sequential.builder("invalid").step("missing").build();
        }
    }

    static final class InvalidReturnedBuilderFactory {
        @Paramixel.TaskFactory
        public static TaskBuilder<?> factory() {
            return Sequential.builder("invalid").step("missing");
        }
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

    @Test
    @DisplayName("FACTORY-07: a factory may take the run Configuration and shape its definition from it")
    void configurationParameterDrivesStructure() {
        final var enabled = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(
                        Map.of(Configuration.ANSI, "false", ConfiguredBuilderFactoryFixture.EXTRA_STEP_KEY, "true")))
                .build()
                .run(Selector.builder()
                        .classOf(ConfiguredBuilderFactoryFixture.class)
                        .build());

        assertThat(enabled.status().isPassed()).isTrue();
        assertThat(allNames(enabled.root().orElseThrow())).contains("base", "extra");
        assertThat(DiscoveryFixtures.RECORDED).contains("base", "extra");

        DiscoveryFixtures.RECORDED.clear();
        final var disabled = discover(ConfiguredBuilderFactoryFixture.class);

        assertThat(disabled.status().isPassed()).isTrue();
        assertThat(allNames(disabled.root().orElseThrow())).contains("base").doesNotContain("extra");
        assertThat(DiscoveryFixtures.RECORDED).contains("base").doesNotContain("extra");
    }

    @Test
    @DisplayName("FACTORY-08: a factory taking Configuration may return a built Task")
    void configurationParameterAcceptsTaskReturnType() {
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(
                        Map.of(Configuration.ANSI, "false", ConfiguredTaskFactoryFixture.SUFFIX_KEY, "kafka")))
                .build()
                .run(Selector.builder()
                        .classOf(ConfiguredTaskFactoryFixture.class)
                        .build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(allNames(result.root().orElseThrow())).contains("configured-kafka");
        assertThat(DiscoveryFixtures.RECORDED).contains("configured-task");
    }

    @Test
    @DisplayName("FACTORY-09: a parameter that is not the run Configuration is rejected")
    void nonConfigurationParameterIsRejected() {
        assertThatThrownBy(() -> discover(NonConfigurationParameterFactoryFixture.class))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining(NonConfigurationParameterFactoryFixture.class.getName())
                .hasMessageContaining(
                        "must take no parameters or exactly one org.paramixel.api.Configuration parameter")
                .hasMessageContaining("java.lang.String");
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("FACTORY-10: more than the one supported parameter is rejected")
    void extraParametersAreRejected() {
        assertThatThrownBy(() -> discover(TooManyParametersFactoryFixture.class))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining(TooManyParametersFactoryFixture.class.getName())
                .hasMessageContaining(
                        "must take no parameters or exactly one org.paramixel.api.Configuration parameter");
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("FACTORY-11: two factory declarations in one class are rejected")
    void twoDeclarationsInOneClassAreRejected() {
        assertThatThrownBy(() -> discover(AmbiguousShapeFactoryFixture.class))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining(AmbiguousShapeFactoryFixture.class.getName())
                .hasMessageContaining("has more than one @Paramixel.TaskFactory method")
                .hasMessageContaining("repeat the same parameter shape");
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }

    @Test
    @DisplayName("FACTORY-12: a subclass that changes the inherited factory's shape is rejected")
    void changedInheritedShapeIsRejected() {
        final var selector = Selector.builder()
                .packageTreeOf(DiscoveryFixtures.class)
                .classPattern(Pattern.compile(".*ShapeMismatch(Base|Sub)Fixture"))
                .build();

        assertThatThrownBy(() -> Runner.builder()
                        .listener(Listener.NOOP)
                        .configuration(Configuration.of(Map.of(Configuration.ANSI, "false")))
                        .build()
                        .run(selector))
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("has more than one @Paramixel.TaskFactory method")
                .hasMessageContaining(ShapeMismatchBaseFixture.class.getName())
                .hasMessageContaining(ShapeMismatchSubFixture.class.getName());
        assertThat(DiscoveryFixtures.RECORDED).isEmpty();
    }
}
