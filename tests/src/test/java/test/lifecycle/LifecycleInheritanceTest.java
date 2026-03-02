/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Verifies lifecycle hook inheritance between a base class and a subclass.
 *
 * <p>The expected ordering is asserted in {@link LifecycleInheritanceBase#baseClassFinalize(ClassContext)}.
 */
@Paramixel.TestClass
public class LifecycleInheritanceTest extends LifecycleInheritanceBase {

    /**
     * Supplies a single argument to drive the lifecycle execution.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(1);
        collector.addArgument("argument[0]");
    }

    /**
     * Subclass initialize hook.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    @Paramixel.Order(2)
    public void subClassInitialize(final @NonNull ClassContext context) {
        actual.add("subClassInitialize");
    }

    /**
     * Subclass before-all hook.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    @Paramixel.Order(10)
    public void subClassBeforeAll(final @NonNull ArgumentContext context) {
        actual.add("subClassBeforeAll");
    }

    /**
     * Subclass before-each hook.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    @Paramixel.Order(20)
    public void subClassBeforeEach(final @NonNull ArgumentContext context) {
        actual.add("subClassBeforeEach");
    }

    /**
     * First test method used to validate lifecycle ordering.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void test1(final @NonNull ArgumentContext context) {
        actual.add("test1");
    }

    /**
     * Second test method used to validate lifecycle ordering.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void test2(final @NonNull ArgumentContext context) {
        actual.add("test2");
    }

    /**
     * Subclass after-each hook.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    @Paramixel.Order(10)
    public void subClassAfterEach(final @NonNull ArgumentContext context) {
        actual.add("subClassAfterEach");
    }

    /**
     * Subclass after-all hook.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    @Paramixel.Order(10)
    public void subClassAfterAll(final @NonNull ArgumentContext context) {
        actual.add("subClassAfterAll");
    }

    /**
     * Subclass finalize hook.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    @Paramixel.Order(10)
    public void subClassFinalize(final @NonNull ClassContext context) {
        actual.add("subClassFinalize");
    }
}
