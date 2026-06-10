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
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Step.step;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;

/**
 * Demonstrates {@link org.paramixel.api.action.Isolated} as a serialization boundary inside a
 * {@link org.paramixel.api.action.Parallel}. Two Isolated nodes share a lock name so their bodies
 * never execute concurrently, while unrelated actions run in parallel.
 *
 * <p>Each body wraps a {@link org.paramixel.api.action.Scope} with setup, test, and teardown.
 * State isolation is provided by composing {@link org.paramixel.api.action.Isolated} with
 * {@link org.paramixel.api.action.Instance}.
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
        return parallel(IsolatedTest.class.getName())
                .parallelism(4)
                .child(isolated("serialized-group-1", "db-lock")
                        .body(parallel("group-1-tests")
                                .parallelism(2)
                                .child(instance("test-1a", IsolatedTest::new)
                                        .body(scope("scenario")
                                                .before(step(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(step(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(step(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))))
                                .child(instance("test-1b", IsolatedTest::new)
                                        .body(scope("scenario")
                                                .before(step(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(step(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(step(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))))))
                .child(isolated("serialized-group-2", "db-lock")
                        .body(parallel("group-2-tests")
                                .parallelism(2)
                                .child(instance("test-2a", IsolatedTest::new)
                                        .body(scope("scenario")
                                                .before(step(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(step(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(step(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))))
                                .child(instance("test-2b", IsolatedTest::new)
                                        .body(scope("scenario")
                                                .before(step(
                                                        "setup()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::setup)))
                                                .body(step(
                                                        "run()", withInstance(IsolatedTest.class, IsolatedTest::run)))
                                                .after(step(
                                                        "teardown()",
                                                        withInstance(IsolatedTest.class, IsolatedTest::teardown)))))))
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
