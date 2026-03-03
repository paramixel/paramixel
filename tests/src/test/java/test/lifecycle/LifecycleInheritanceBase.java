/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package test.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public abstract class LifecycleInheritanceBase {

    /**
     * Records the sequence of lifecycle and test method invocations.
     *
     * <p>This list is shared between the base class and {@link LifecycleInheritanceTest}.
     */
    protected static final List<String> actual = Collections.synchronizedList(new ArrayList<>());

    /**
     * Base-class initialize hook.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    @Paramixel.Order(1)
    public void baseClassInitialize(final @NonNull ClassContext context) {
        actual.clear();
        actual.add("baseClassInitialize");
    }

    /**
     * Base-class before-all hook.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    @Paramixel.Order(20)
    public void baseClassBeforeAll(final @NonNull ArgumentContext context) {
        actual.add("baseClassBeforeAll");
    }

    /**
     * Base-class before-each hook.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    @Paramixel.Order(10)
    public void baseClassBeforeEach(final @NonNull ArgumentContext context) {
        actual.add("baseClassBeforeEach");
    }

    /**
     * Base-class after-each hook.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    @Paramixel.Order(20)
    public void baseClassAfterEach(final @NonNull ArgumentContext context) {
        actual.add("baseClassAfterEach");
    }

    /**
     * Base-class after-all hook.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    @Paramixel.Order(20)
    public void baseClassAfterAll(final @NonNull ArgumentContext context) {
        actual.add("baseClassAfterAll");
    }

    /**
     * Base-class finalize hook.
     *
     * <p>This hook asserts that base- and subclass lifecycle methods were invoked in the expected
     * order.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    @Paramixel.Order(20)
    public void baseClassFinalize(final @NonNull ClassContext context) {
        actual.add("baseClassFinalize");

        List<String> expected = new ArrayList<>();

        expected.add("baseClassInitialize");
        expected.add("subClassInitialize");
        expected.add("subClassBeforeAll");
        expected.add("baseClassBeforeAll");
        expected.add("baseClassBeforeEach");
        expected.add("subClassBeforeEach");
        expected.add("test2");
        expected.add("subClassAfterEach");
        expected.add("baseClassAfterEach");
        expected.add("baseClassBeforeEach");
        expected.add("subClassBeforeEach");
        expected.add("test1");
        expected.add("subClassAfterEach");
        expected.add("baseClassAfterEach");
        expected.add("subClassAfterAll");
        expected.add("baseClassAfterAll");
        expected.add("subClassFinalize");
        expected.add("baseClassFinalize");

        assertThat(actual).containsExactlyElementsOf(expected);
    }
}
