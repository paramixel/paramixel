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

package examples.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import org.paramixel.api.Paramixel;
import org.paramixel.api.action.Action;

/**
 * The fixture-per-environment shape a real suite uses, built on the 7.0 API.
 *
 * <p>One {@code Parallel} names the pool that bounds the live fixtures, {@code each} maps every environment to one
 * typed fixture, and each step names only the method it calls: the fixture type appears once, in
 * {@code Instance.of}, instead of on every step.
 *
 * <p>{@code paramixel.parallelism.docker=2} bounds how many fixtures are alive at once. An environment that is not
 * supported is filtered out before the tree is built, and a failing step does not stop the other environments
 * because each fixture is its own branch of the {@code Parallel}.
 */
public final class MetricsTest {

    private MetricsTest() {
        // Intentionally empty
    }

    /**
     * Builds the action tree.
     *
     * @return the action tree
     */
    @Paramixel.Factory
    public static Action factory() {
        return parallel("MetricsTest", "docker")
                .each(
                        Environment.supported(),
                        environment -> of(
                                        environment.name(), MetricsFixture.class, () -> new MetricsFixture(environment))
                                .before("setUp()", MetricsFixture::setUp)
                                .body(sequential("tests")
                                        .child(step(
                                                "testHealthy()",
                                                MetricsFixture.class,
                                                fixture -> fixture.check("healthy")))
                                        .child(step(
                                                "testDefaultTextMetrics()",
                                                MetricsFixture.class,
                                                fixture -> fixture.check("text-metrics")))
                                        .child(step(
                                                "testNamedMetrics()",
                                                MetricsFixture.class,
                                                fixture -> fixture.check("named")))
                                        .child(step(
                                                "verifyLifecycle()",
                                                MetricsFixture.class,
                                                MetricsTest::verifyLifecycle)))
                                .after("tearDown()", MetricsFixture::tearDown))
                .build();
    }

    /**
     * Verifies the fixture recorded the expected call order.
     *
     * @param fixture the fixture to verify
     */
    private static void verifyLifecycle(final MetricsFixture fixture) {
        assertThat(fixture.calls()).containsExactly("setUp", "healthy", "text-metrics", "named");
        assertThat(fixture.environment().enabled()).isTrue();
    }
}
