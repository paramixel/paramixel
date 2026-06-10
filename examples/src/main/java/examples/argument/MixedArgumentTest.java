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
import static org.paramixel.api.Context.withInstance;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequential.sequential;
import static org.paramixel.api.action.Step.step;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

public class MixedArgumentTest {

    sealed interface TestArgument permits StringArgument, IntegerArgument, PointArgument {

        String name();

        void test();
    }

    record StringArgument(String value) implements TestArgument {
        @Override
        public String name() {
            return "string:" + value;
        }

        @Override
        public void test() {
            assertThat(value).startsWith("param");
        }
    }

    record IntegerArgument(int value) implements TestArgument {
        @Override
        public String name() {
            return "integer:" + value;
        }

        @Override
        public void test() {
            assertThat(value).isPositive();
        }
    }

    record Point(int x, int y) {
        // Intentionally empty
    }

    record PointArgument(Point point) implements TestArgument {

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

    private static final int EXPECTED_ARGUMENT_COUNT = 3;
    private static final int EXPECTED_TEST_COUNT_PER_ARGUMENT = 1;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger beforeEachCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger afterEachCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

    private final TestArgument testArgument;

    private boolean suiteSetUp;
    private boolean setUp;

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        resetCounts();

        var arguments = sequential("MixedArgumentTest").independent();

        for (TestArgument testArgument :
                List.of(new StringArgument("paramixel"), new IntegerArgument(42), new PointArgument(new Point(3, 7)))) {
            arguments.child(instance(testArgument.name(), () -> new MixedArgumentTest(testArgument))
                    .body(scope("lifecycle")
                            .before(step("setUp()", withInstance(MixedArgumentTest.class, MixedArgumentTest::setUp)))
                            .body(scope("testOne")
                                    .before(step(
                                            "beforeEach()",
                                            withInstance(MixedArgumentTest.class, MixedArgumentTest::beforeEach)))
                                    .body(step(
                                            "testOne()",
                                            withInstance(MixedArgumentTest.class, MixedArgumentTest::testOne)))
                                    .after(step(
                                            "afterEach()",
                                            withInstance(MixedArgumentTest.class, MixedArgumentTest::afterEach))))
                            .after(step(
                                    "tearDown()",
                                    withInstance(MixedArgumentTest.class, MixedArgumentTest::tearDown)))));
        }

        return instance("MixedArgumentTest", MixedArgumentTest::new)
                .body(sequential("body")
                        .child(arguments)
                        .child(scope("lifecycle")
                                .body(step("[validate-ready]", context -> {}))
                                .after(step(
                                        "validate()",
                                        withInstance(MixedArgumentTest.class, MixedArgumentTest::validate)))))
                .build();
    }

    public MixedArgumentTest() {
        this.testArgument = null;
    }

    private MixedArgumentTest(final TestArgument testArgument) {
        this.testArgument = testArgument;
    }

    public void setUp() {
        setUpCount.incrementAndGet();
        suiteSetUp = true;
    }

    public void beforeEach() {
        beforeEachCount.incrementAndGet();
        assertThat(suiteSetUp).isTrue();
        setUp = true;
    }

    public void testOne() {
        testCount.incrementAndGet();
        assertThat(setUp).isTrue();
        testArgument.test();
    }

    public void afterEach() {
        afterEachCount.incrementAndGet();
        assertThat(suiteSetUp).isTrue();
        setUp = false;
    }

    public void tearDown() {
        tearDownCount.incrementAndGet();

        assertThat(suiteSetUp).isTrue();
        assertThat(setUp).isFalse();
    }

    public void validate() {
        assertThat(setUpCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
        assertThat(beforeEachCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_TEST_COUNT_PER_ARGUMENT);
        assertThat(testCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_TEST_COUNT_PER_ARGUMENT);
        assertThat(afterEachCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_TEST_COUNT_PER_ARGUMENT);
        assertThat(tearDownCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
    }

    private static void resetCounts() {
        setUpCount.set(0);
        beforeEachCount.set(0);
        testCount.set(0);
        afterEachCount.set(0);
        tearDownCount.set(0);
    }
}
