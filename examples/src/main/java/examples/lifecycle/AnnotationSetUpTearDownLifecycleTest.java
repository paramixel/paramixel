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

package examples.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Spec;

public class AnnotationSetUpTearDownLifecycleTest {

    private static final int EXPECTED_TEST_COUNT = 2;

    private static final AtomicInteger setUpCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger tearDownCount = new AtomicInteger();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Spec<?> factory() {
        resetCounts();

        var annotationResolver = AnnotationResolver.create(AnnotationSetUpTearDownLifecycleTest.class);

        return Instance.of(AnnotationSetUpTearDownLifecycleTest.class)
                .child(Lifecycle.of("lifecycle")
                        .before(annotationResolver.byId("setUp"))
                        .child(annotationResolver.byId("testOne"))
                        .child(annotationResolver.byId("testTwo"))
                        .after(annotationResolver.byId("tearDown")));
    }

    public AnnotationSetUpTearDownLifecycleTest() {
        // Intentionally empty
    }

    @Paramixel.Id("setUp")
    public void setUp() {
        setUpCount.incrementAndGet();
    }

    @Paramixel.Id("testOne")
    public void testOne() {
        testCount.incrementAndGet();
    }

    @Paramixel.Id("testTwo")
    public void testTwo() {
        testCount.incrementAndGet();
    }

    @Paramixel.Id("tearDown")
    public void tearDown() {
        tearDownCount.incrementAndGet();

        assertThat(setUpCount.get()).isEqualTo(1);
        assertThat(testCount.get()).isEqualTo(EXPECTED_TEST_COUNT);
        assertThat(tearDownCount.get()).isEqualTo(1);
    }

    private static void resetCounts() {
        setUpCount.set(0);
        testCount.set(0);
        tearDownCount.set(0);
    }
}
