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

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class LifecycleOrderInheritanceFlattenedOrderTest extends LifecycleOrderInheritanceFlattenedOrderBase {

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(8);
        collector.addArgument("argument[0]");
    }

    @Paramixel.BeforeEach
    @Paramixel.Order(ORDER_SUB_BEFORE_EACH)
    public void subBeforeEach(final @NonNull ArgumentContext context) {
        actual.add("subBeforeEach");
    }

    @Paramixel.BeforeAll
    @Paramixel.Order(ORDER_SUB_BEFORE_ALL)
    public void subBeforeAll(final @NonNull ArgumentContext context) {
        actual.add("subBeforeAll");
    }

    @Paramixel.Initialize
    @Paramixel.Order(ORDER_SUB_INITIALIZE)
    public void subInitialize(final @NonNull ClassContext context) {
        actual.add("subInitialize");
    }

    @Paramixel.Test
    @Paramixel.Order(ORDER_SUB_FOURTH)
    public void subOrdered4(final @NonNull ArgumentContext context) {
        recordSequential("subOrdered4", context);
    }

    @Paramixel.Test
    public void subUnorderedLast(final @NonNull ArgumentContext context) {
        recordSequential("subUnorderedLast", context);
    }

    @Paramixel.Test
    @Paramixel.Order(ORDER_SUB_SECOND)
    public void subOrdered1(final @NonNull ArgumentContext context) {
        recordSequential("subOrdered1", context);
    }

    @Paramixel.AfterAll
    @Paramixel.Order(ORDER_SUB_AFTER_ALL)
    public void subAfterAll(final @NonNull ArgumentContext context) {
        actual.add("subAfterAll");
    }

    @Paramixel.AfterEach
    @Paramixel.Order(ORDER_SUB_AFTER_EACH)
    public void subAfterEach(final @NonNull ArgumentContext context) {
        actual.add("subAfterEach");
    }

    @Paramixel.Finalize
    @Paramixel.Order(ORDER_SUB_FINALIZE)
    public void subFinalize(final @NonNull ClassContext context) {
        actual.add("subFinalize");
    }
}
