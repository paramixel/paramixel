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
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;

import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Task;

/**
 * The fixture-per-environment shape a real suite uses.
 *
 * <p>One parallel task names the pool that bounds the live fixtures, and every enabled environment becomes one branch.
 * The environment is published with {@code bind}, the fixture is built by the branch's {@code before} hook and stored in
 * the branch's scope, and each step reads it back by name — no lambda captures it and no method reference carries it.
 *
 * <p>{@code Runner.builder().pool(Pool.named("docker"), 2)} bounds how many fixtures are alive at once. An environment
 * that is not supported is filtered out while the tree is built, and a failing step does not stop the other environments
 * because each fixture is its own branch of the parallel task.
 */
public final class MetricsTest {

    private static final String ID_SET_UP = "setUp";
    private static final String ID_TEST_HEALTHY = "testHealthy";
    private static final String ID_TEST_TEXT_METRICS = "testDefaultTextMetrics";
    private static final String ID_TEST_NAMED_METRICS = "testNamedMetrics";
    private static final String ID_VERIFY_LIFECYCLE = "verifyLifecycle";
    private static final String ID_TEAR_DOWN = "tearDown";

    private static final String ENVIRONMENT = "environment";
    private static final String FIXTURE = "fixture";

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
        final var root = parallel(MetricsTest.class, "MetricsTest", Pool.named("docker"));
        for (final var environment : Environment.supported()) {
            root.child(sequential(environment.name())
                    .bind(ENVIRONMENT, environment)
                    .before(ID_SET_UP)
                    .child(sequential("tests")
                            .step(ID_TEST_HEALTHY)
                            .step(ID_TEST_TEXT_METRICS)
                            .step(ID_TEST_NAMED_METRICS)
                            .step(ID_VERIFY_LIFECYCLE))
                    .after(ID_TEAR_DOWN));
        }
        return root.build();
    }

    /**
     * Builds this branch's fixture and runs its setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUpHook(final Context context) {
        final var fixture = new MetricsFixture(context.require(ENVIRONMENT, Environment.class));
        context.put(FIXTURE, fixture);
        fixture.setUp();
    }

    /**
     * Checks the healthy endpoint.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_HEALTHY)
    public static void testHealthy(final Context context) {
        context.require(FIXTURE, MetricsFixture.class).check("healthy");
    }

    /**
     * Checks the default text metrics.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_TEXT_METRICS)
    public static void testDefaultTextMetrics(final Context context) {
        context.require(FIXTURE, MetricsFixture.class).check("text-metrics");
    }

    /**
     * Checks the named metrics.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_NAMED_METRICS)
    public static void testNamedMetrics(final Context context) {
        context.require(FIXTURE, MetricsFixture.class).check("named");
    }

    /**
     * Verifies the lifecycle the fixture recorded.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VERIFY_LIFECYCLE)
    public static void verifyLifecycleHook(final Context context) {
        verifyLifecycle(context.require(FIXTURE, MetricsFixture.class));
    }

    /**
     * Tears the fixture down, if it was created.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDownHook(final Context context) {
        context.find(FIXTURE, MetricsFixture.class).ifPresent(MetricsFixture::tearDown);
    }

    private static void verifyLifecycle(final MetricsFixture fixture) {
        assertThat(fixture.calls()).containsExactly("setUp", "healthy", "text-metrics", "named");
        assertThat(fixture.environment().enabled()).isTrue();
    }
}
