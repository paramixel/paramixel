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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.paramixel.api.AnnotationResolver;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Spec;

/**
 * Verifies that {@code @Paramixel.Disabled} prevents the annotated action factory
 * from being invoked by the runner. A separate verification factory asserts the
 * disabled factory was never called. Uses annotation-based method references.
 */
public class AnnotationDisabledTest {

    private static final AtomicBoolean factoryInvoked = new AtomicBoolean();

    /**
     * Runs the verification factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(final String[] args) {
        Runner.defaultRunner().runAndExit(disabledVerificationFactory());
    }

    /**
     * Verification factory that asserts the disabled factory was never invoked.
     *
     * @return a single-step action that checks the disabled flag
     */
    @Paramixel.Factory
    public static Spec<?> disabledVerificationFactory() {
        var annotationResolver = AnnotationResolver.create(AnnotationDisabledTest.class);

        return Instance.of(AnnotationDisabledTest.class).child(annotationResolver.byId("verify"));
    }

    /**
     * Action factory annotated with {@code @Paramixel.Disabled} — must not be
     * invoked by the runner.
     *
     * @return a flow that would fail if ever executed
     */
    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.Factory
    public static Spec<?> factory() {
        factoryInvoked.set(true);

        var annotationResolver = AnnotationResolver.create(AnnotationDisabledTest.class);

        return Instance.of(AnnotationDisabledTest.class)
                .child(Lifecycle.of("lifecycle")
                        .before(annotationResolver.byId("before"))
                        .child(annotationResolver.byId("disabledLeaf"))
                        .after(annotationResolver.byId("after"))
                        .resolve());
    }

    public AnnotationDisabledTest() {
        // Intentionally empty
    }

    /**
     * Verifies that the disabled factory was never invoked.
     */
    @Paramixel.Id("verify")
    public void verify() {
        assertThat(factoryInvoked.get())
                .as("@Paramixel.Disabled must prevent the action factory from being invoked")
                .isFalse();
    }

    /**
     * Should never execute — throws if called.
     */
    @Paramixel.Id("before")
    public void before() {
        throw new AssertionError("Disabled action must not execute");
    }

    /**
     * Should never execute — throws if called.
     */
    @Paramixel.Id("disabledLeaf")
    public void disabledLeaf() {
        throw new AssertionError("Disabled action must not execute");
    }

    /**
     * Should never execute — throws if called.
     */
    @Paramixel.Id("after")
    public void after() {
        throw new AssertionError("Disabled action must not execute");
    }
}
