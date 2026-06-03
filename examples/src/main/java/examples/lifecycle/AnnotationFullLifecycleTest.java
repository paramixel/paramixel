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

import java.util.ArrayList;
import java.util.List;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;

public class AnnotationFullLifecycleTest {

    private static final List<String> callOrder = new ArrayList<>();

    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(factory());
    }

    @Paramixel.Factory
    public static Action factory() {
        var annotationResolver = AnnotationResolver.create(AnnotationFullLifecycleTest.class);

        return Static.builder(AnnotationFullLifecycleTest.class.getName())
                .before(annotationResolver.staticById("staticSetUp"))
                .body(Instance.builder(AnnotationFullLifecycleTest.class)
                        .body(Scope.builder("[scenario]")
                                .before(annotationResolver.byId("setUp"))
                                .body(Sequence.builder("tests")
                                        .child(Scope.builder("testOne")
                                                .before(annotationResolver.byId("beforeEach"))
                                                .body(annotationResolver.byId("testOne"))
                                                .after(annotationResolver.byId("afterEach"))
                                                .build())
                                        .child(Scope.builder("testTwo")
                                                .before(annotationResolver.byId("beforeEach"))
                                                .body(annotationResolver.byId("testTwo"))
                                                .after(annotationResolver.byId("afterEach"))
                                                .build())
                                        .build())
                                .after(annotationResolver.byId("tearDown"))
                                .build())
                        .build())
                .after(annotationResolver.staticById("staticTearDown"))
                .build();
    }

    public AnnotationFullLifecycleTest() {
        // Intentionally empty
    }

    @Paramixel.Id("staticSetUp")
    public static void staticSetUp() {
        callOrder.clear();
        callOrder.add("staticSetUp");
    }

    @Paramixel.Id("setUp")
    public void setUp() {
        callOrder.add("setUp");
    }

    @Paramixel.Id("beforeEach")
    public void beforeEach() {
        callOrder.add("beforeEach");
    }

    @Paramixel.Id("testOne")
    public void testOne() {
        callOrder.add("testOne");
    }

    @Paramixel.Id("testTwo")
    public void testTwo() {
        callOrder.add("testTwo");
    }

    @Paramixel.Id("afterEach")
    public void afterEach() {
        callOrder.add("afterEach");
    }

    @Paramixel.Id("tearDown")
    public void tearDown() {
        callOrder.add("tearDown");
    }

    @Paramixel.Id("staticTearDown")
    public static void staticTearDown() {
        callOrder.add("staticTearDown");

        assertThat(callOrder)
                .containsExactly(
                        "staticSetUp",
                        "setUp",
                        "beforeEach",
                        "testOne",
                        "afterEach",
                        "beforeEach",
                        "testTwo",
                        "afterEach",
                        "tearDown",
                        "staticTearDown");
    }
}
