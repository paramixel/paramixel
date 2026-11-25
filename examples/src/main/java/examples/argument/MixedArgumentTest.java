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

package examples.argument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.sequential;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Context;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Demonstrates a heterogeneous matrix: each item maps to a fixture whose type differs, without a cast and without
 * repeating a class token on any step.
 */
public class MixedArgumentTest {

    /**
     * A test argument that knows its own name and how to validate itself.
     */
    public sealed interface TestArgument permits StringArgument, IntegerArgument, PointArgument {

        /**
         * Returns the display name used for the fixture occurrence.
         *
         * @return the name; never {@code null}
         */
        String name();

        /**
         * Runs the argument-specific assertion.
         */
        void test();
    }

    /**
     * A string argument.
     *
     * @param value the value
     */
    public record StringArgument(String value) implements TestArgument {

        @Override
        public String name() {
            return "string:" + value;
        }

        @Override
        public void test() {
            assertThat(value).startsWith("param");
        }
    }

    /**
     * An integer argument.
     *
     * @param value the value
     */
    public record IntegerArgument(int value) implements TestArgument {

        @Override
        public String name() {
            return "integer:" + value;
        }

        @Override
        public void test() {
            assertThat(value).isPositive();
        }
    }

    /**
     * A point value.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public record Point(int x, int y) {
        // Intentionally empty
    }

    /**
     * A point argument.
     *
     * @param point the point
     */
    public record PointArgument(Point point) implements TestArgument {

        @Override
        public String name() {
            return "point:" + point.x() + "," + point.y();
        }

        @Override
        public void test() {
            assertThat(point.x()).isPositive();
            assertThat(point.y()).isPositive();
        }
    }

    private static final String ID_SET_UP = "setUp";
    private static final String ID_TEST_ONE = "testOne";
    private static final String ID_TEAR_DOWN = "tearDown";
    private static final String ID_VALIDATE = "validate";

    private static final String ARGUMENT = "argument";
    private static final String FIXTURE = "fixture";

    private static final int EXPECTED_ARGUMENT_COUNT = 3;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

    private final TestArgument testArgument;

    /**
     * Runs the factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        resetCounts();
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds the definition.
     *
     * @return the definition for this test
     */
    @Paramixel.Factory
    public static Task factory() {
        resetCounts();
        final var root = sequential(MixedArgumentTest.class, "MixedArgumentTest");
        for (final var testArgument :
                List.of(new StringArgument("paramixel"), new IntegerArgument(42), new PointArgument(new Point(3, 7)))) {
            root.child(sequential(testArgument.name())
                    .bind(ARGUMENT, testArgument)
                    .before(ID_SET_UP)
                    .step(ID_TEST_ONE)
                    .after(ID_TEAR_DOWN));
        }
        return root.after(ID_VALIDATE).build();
    }

    private MixedArgumentTest(final TestArgument testArgument) {
        this.testArgument = testArgument;
    }

    /**
     * Builds this branch's fixture and records setup.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_SET_UP)
    public static void setUpHook(final Context context) {
        final var fixture = new MixedArgumentTest(context.require(ARGUMENT, TestArgument.class));
        context.put(FIXTURE, fixture);
        fixture.setUp();
    }

    /**
     * Runs the argument-specific test.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEST_ONE)
    public static void testOneHook(final Context context) {
        context.require(FIXTURE, MixedArgumentTest.class).testOne();
    }

    /**
     * Records teardown, if the fixture was created.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_TEAR_DOWN)
    public static void tearDownHook(final Context context) {
        context.find(FIXTURE, MixedArgumentTest.class).ifPresent(MixedArgumentTest::tearDown);
    }

    /**
     * Verifies the aggregate expectations.
     *
     * @param context the execution context
     */
    @Paramixel.Id(ID_VALIDATE)
    public static void validateHook(final Context context) {
        validate();
    }

    /**
     * Records setup.
     */
    public void setUp() {
        setUpCount.incrementAndGet();
    }

    /**
     * Runs the argument-specific test.
     */
    public void testOne() {
        testCount.incrementAndGet();
        testArgument.test();
    }

    /**
     * Records teardown.
     */
    public void tearDown() {
        tearDownCount.incrementAndGet();
    }

    /**
     * Verifies the aggregate expectations.
     */
    public static void validate() {
        assertThat(setUpCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
        assertThat(testCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
        assertThat(tearDownCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
    }

    private static void resetCounts() {
        setUpCount.set(0);
        testCount.set(0);
        tearDownCount.set(0);
    }
}
