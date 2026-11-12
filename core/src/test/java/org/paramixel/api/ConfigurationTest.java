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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.task;

import java.util.Map;
import nonapi.org.paramixel.ConfigurationFactory;
import nonapi.org.paramixel.compiler.PlanCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.CompilationException;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Configuration defaults, precedence, pool resolution, and unknown-key reporting.
 */
class ConfigurationTest {

    @Test
    @DisplayName("defaults match the documented contract")
    void defaults() {
        final var configuration = ConfigurationFactory.defaults();

        assertThat(configuration.getBoolean(Configuration.FAILURE_ON_SKIP)).hasValue(true);
        assertThat(configuration.getBoolean(Configuration.FAILURE_ON_ABORT)).hasValue(true);
        assertThat(configuration.getBoolean(Configuration.FAIL_IF_NO_TESTS)).hasValue(true);
        assertThat(configuration.getBoolean(Configuration.FAIL_FAST)).hasValue(false);
        assertThat(configuration.getInteger(Configuration.RUNNER_PARALLELISM)).isPresent();
        assertThat(configuration.getInteger(Configuration.WORKERS)).isPresent();
        assertThat(configuration.getLong(Configuration.SHUTDOWN_GRACE_MILLIS)).hasValue(10_000L);
        assertThat(configuration.getInteger(Configuration.LISTENER_QUEUE_CAPACITY))
                .hasValue(8192);
        assertThat(configuration.getString(Configuration.ANSI)).hasValue("auto");
    }

    @Test
    @DisplayName("system properties override the built-in defaults")
    void systemPropertiesOverrideDefaults() {
        final var key = Configuration.FAIL_FAST;
        final var previous = System.getProperty(key);
        try {
            assertThat(ConfigurationFactory.defaults().getBoolean(key)).hasValue(false);

            System.setProperty(key, "true");

            assertThat(Configuration.defaultConfiguration().getBoolean(key)).hasValue(true);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    @DisplayName("typed accessors parse and reject values deterministically")
    void typedAccessors() {
        final var configuration = Configuration.of(Map.of(
                "paramixel.workers", "4",
                "paramixel.failFast", " TRUE ",
                "paramixel.shutdown.graceMillis", "500"));

        assertThat(configuration.getInteger("paramixel.workers")).hasValue(4);
        assertThat(configuration.getBoolean("paramixel.failFast")).hasValue(true);
        assertThat(configuration.getLong("paramixel.shutdown.graceMillis")).hasValue(500L);
        assertThat(configuration.getString("paramixel.missing")).isEmpty();
        assertThatThrownBy(() -> configuration.getInteger("paramixel.failFast"))
                .isInstanceOf(ConfigurationException.class);
        assertThatThrownBy(() -> Configuration.of(Map.of("k", "v")).getString(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("pool keys follow the documented naming rule")
    void poolKeys() {
        assertThat(Configuration.poolKey("environments")).isEqualTo("paramixel.parallelism.environments");
        assertThat(Configuration.poolKey(Pool.named("p").name())).isEqualTo("paramixel.parallelism.p");
    }

    @Test
    @DisplayName("runner pool overrides take precedence over configuration")
    void poolPrecedence() {
        final var definition =
                parallel("root", Pool.named("p")).step("work", () -> {}).build();
        final var configuration = Configuration.of(Map.of("paramixel.parallelism.p", "1"));

        final var fromConfiguration = new PlanCompiler(configuration, Map.of()).compile(definition);
        final var fromOverride = new PlanCompiler(configuration, Map.of(Pool.named("p"), 7)).compile(definition);

        assertThat(fromConfiguration.poolCapacities()).containsEntry("p", 1);
        assertThat(fromOverride.poolCapacities()).containsEntry("p", 7);
    }

    @Test
    @DisplayName("processor-relative pool expressions resolve to at least one")
    void coresExpressions() {
        final var definition =
                parallel("root", Pool.named("p")).step("work", () -> {}).build();
        for (String expression : new String[] {"@cores", "@cores*2", "@cores/2", "@cores*1000"}) {
            final var plan = new PlanCompiler(Configuration.of(Map.of("paramixel.parallelism.p", expression)), Map.of())
                    .compile(definition);
            assertThat(plan.poolCapacities().get("p")).isPositive();
        }
    }

    @Test
    @DisplayName("an invalid pool capacity fails compilation with a repair hint")
    void invalidPoolCapacity() {
        final var definition =
                parallel("root", Pool.named("p")).step("work", () -> {}).build();

        assertThatThrownBy(() -> new PlanCompiler(Configuration.of(Map.of("paramixel.parallelism.p", "zero")), Map.of())
                        .compile(definition))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("POOL_INVALID")
                .hasMessageContaining("@cores");
    }

    @Test
    @DisplayName("an unknown Paramixel key is reported as a warning, not ignored silently")
    void unknownKeyWarns() {
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .configuration(Configuration.of(Map.of("paramixel.unexpectedSetting", "2")))
                .build()
                .run(task("root").step("work", () -> {}).build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic)
                        .contains("Unknown Paramixel configuration key")
                        .contains("paramixel.unexpectedSetting"));
    }
}
