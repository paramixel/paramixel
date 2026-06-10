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

package examples.annotation;

import static org.paramixel.api.action.Instance.instance;

import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Action;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.selector.Selector;

/**
 * Verifies that {@code @Paramixel.Disabled} prevents the annotated action factory
 * from being invoked by the runner. Uses annotation-based method references.
 */
public class AnnotationDisabledTest {

    /**
     * Discovers and executes all action factories in this package, then exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(Selector.packageTreeOf(AnnotationDisabledTest.class));
    }

    /**
     * Action factory annotated with {@code @Paramixel.Disabled} — must not be
     * invoked by the runner. If this factory is ever called, the test fails.
     * Uses an {@link AnnotationResolver} to prove the annotation-based path is
     * also guarded by {@code @Disabled}.
     *
     * @return a flow that would fail if ever executed
     */
    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.Factory
    public static Action factory() {
        var annotationResolver = AnnotationResolver.create(AnnotationDisabledTest.class);

        return instance(AnnotationDisabledTest.class)
                .body(annotationResolver.byId("shouldNotRun"))
                .build();
    }

    /**
     * Should never execute — uses {@link FailException} because if the factory
     * is incorrectly invoked, this step must fail the test suite.
     */
    @Paramixel.Id("shouldNotRun")
    public void shouldNotRun() {
        FailException.fail("@Paramixel.Disabled action factory was invoked");
    }
}
