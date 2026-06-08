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

package examples.isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Context.withInstance;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;

/**
 * Demonstrates {@link Isolated} as a serialization boundary inside a
 * {@link Parallel}. Two Isolated nodes share a lock name so their bodies
 * never execute concurrently, while unrelated actions run in parallel.
 *
 * <p>Each body wraps a {@link Scope} with setup, test, and teardown.
 * State isolation is provided by composing {@link Isolated} with
 * {@link Instance}.
 */
public class IsolatedTest {

    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds an action tree that exercises Isolated serialization.
     *
     * @return the action tree for this test
     */
    @Paramixel.Factory
    public static Action factory() {
        return Parallel.builder(IsolatedTest.class.getName())
                .parallelism(4)
                .child(Isolated.builder("serialized-group-1", "db-lock")
                        .body(Parallel.builder("group-1-tests")
                                .parallelism(2)
                                .child(Instance.builder("test-1a", IsolatedTest::new)
                                        .body(Scope.<IsolatedTest>builder("scenario")
                                                .before(Step.of(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(Step.of(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(Step.of(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))
                                                .build())
                                        .build())
                                .child(Instance.builder("test-1b", IsolatedTest::new)
                                        .body(Scope.<IsolatedTest>builder("scenario")
                                                .before(Step.of(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(Step.of(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(Step.of(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .child(Isolated.builder("serialized-group-2", "db-lock")
                        .body(Parallel.builder("group-2-tests")
                                .parallelism(2)
                                .child(Instance.builder("test-2a", IsolatedTest::new)
                                        .body(Scope.<IsolatedTest>builder("scenario")
                                                .before(Step.of(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(Step.of(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(Step.of(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))
                                                .build())
                                        .build())
                                .child(Instance.builder("test-2b", IsolatedTest::new)
                                        .body(Scope.<IsolatedTest>builder("scenario")
                                                .before(Step.of(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(Step.of(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(Step.of(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    public IsolatedTest() {}

    public void setup() {
        counter.set(0);
    }

    public void run() {
        var value = counter.incrementAndGet();
        assertThat(value).isEqualTo(1);
    }

    public void teardown() {
        assertThat(counter.get()).isEqualTo(1);
    }
}
