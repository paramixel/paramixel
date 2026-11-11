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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;

import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Task;

/**
 * The fixture-per-environment shape a real suite uses.
 *
 * <p>One parallel task names the pool that bounds the live fixtures, {@code each} maps every enabled environment to one
 * typed fixture, and each step names only the method it calls: the fixture type appears once, in {@code forInstance},
 * instead of on every step.
 *
 * <p>{@code Runner.builder().pool(Pool.named("docker"), 2)} bounds how many fixtures are alive at once. An environment
 * that is not supported is filtered out before the tree is built, and a failing step does not stop the other
 * environments because each fixture is its own branch of the parallel task.
 */
public final class MetricsTest {

    private MetricsTest() {
        // Intentionally empty
    }

    /**
     * Builds the definition.
     *
     * @return the definition
     */
    @Paramixel.Factory
    public static Task factory() {
        return parallel("MetricsTest", Pool.named("docker"))
                .each(
                        Environment.supported(),
                        environment -> forInstance(environment.name(), () -> new MetricsFixture(environment))
                                .before(MetricsFixture::setUp)
                                .sequential(
                                        "tests",
                                        tests -> tests.step("testHealthy", fixture -> fixture.check("healthy"))
                                                .step(
                                                        "testDefaultTextMetrics",
                                                        fixture -> fixture.check("text-metrics"))
                                                .step("testNamedMetrics", fixture -> fixture.check("named"))
                                                .step("verifyLifecycle", MetricsTest::verifyLifecycle))
                                .after(MetricsFixture::tearDown))
                .build();
    }

    private static void verifyLifecycle(final MetricsFixture fixture) {
        assertThat(fixture.calls()).containsExactly("setUp", "healthy", "text-metrics", "named");
        assertThat(fixture.environment().enabled()).isTrue();
    }
}
