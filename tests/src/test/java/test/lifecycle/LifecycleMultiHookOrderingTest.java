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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class LifecycleMultiHookOrderingTest {

    private static final List<String> actual = Collections.synchronizedList(new ArrayList<>());

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(1);
        collector.addArgument("argument[0]");
    }

    @Paramixel.Initialize
    @Paramixel.Order(2)
    public void initB(final @NonNull ClassContext context) {
        actual.add("initB");
    }

    @Paramixel.Initialize
    @Paramixel.Order(1)
    public void initA(final @NonNull ClassContext context) {
        actual.clear();
        actual.add("initA");
    }

    @Paramixel.Initialize
    public void initUnordered(final @NonNull ClassContext context) {
        actual.add("initUnordered");
    }

    @Paramixel.BeforeAll
    @Paramixel.Order(10)
    public void beforeAllB(final @NonNull ArgumentContext context) {
        actual.add("beforeAllB");
    }

    @Paramixel.BeforeAll
    @Paramixel.Order(10)
    public void beforeAllA(final @NonNull ArgumentContext context) {
        actual.add("beforeAllA");
    }

    @Paramixel.BeforeEach
    @Paramixel.Order(2)
    public void beforeEachB(final @NonNull ArgumentContext context) {
        actual.add("beforeEachB");
    }

    @Paramixel.BeforeEach
    @Paramixel.Order(1)
    public void beforeEachA(final @NonNull ArgumentContext context) {
        actual.add("beforeEachA");
    }

    @Paramixel.Test
    @Paramixel.Order(10)
    public void testB(final @NonNull ArgumentContext context) {
        actual.add("testB");
    }

    @Paramixel.Test
    @Paramixel.Order(10)
    public void testA(final @NonNull ArgumentContext context) {
        actual.add("testA");
    }

    @Paramixel.Test
    public void testUnordered(final @NonNull ArgumentContext context) {
        actual.add("testUnordered");
    }

    @Paramixel.AfterEach
    @Paramixel.Order(2)
    public void afterEachB(final @NonNull ArgumentContext context) {
        actual.add("afterEachB");
    }

    @Paramixel.AfterEach
    @Paramixel.Order(1)
    public void afterEachA(final @NonNull ArgumentContext context) {
        actual.add("afterEachA");
    }

    @Paramixel.AfterAll
    @Paramixel.Order(1)
    public void afterAllA(final @NonNull ArgumentContext context) {
        actual.add("afterAllA");
    }

    @Paramixel.AfterAll
    public void afterAllUnordered(final @NonNull ArgumentContext context) {
        actual.add("afterAllUnordered");
    }

    @Paramixel.Finalize
    @Paramixel.Order(1)
    public void finalizeA(final @NonNull ClassContext context) {
        actual.add("finalizeA");
    }

    @Paramixel.Finalize
    public void zzFinalizeAssert(final @NonNull ClassContext context) {
        actual.add("zzFinalizeAssert");

        final List<String> expected = new ArrayList<>();
        expected.add("initA");
        expected.add("initB");
        expected.add("initUnordered");

        expected.add("beforeAllA");
        expected.add("beforeAllB");

        expected.add("beforeEachA");
        expected.add("beforeEachB");
        expected.add("testA");
        expected.add("afterEachA");
        expected.add("afterEachB");

        expected.add("beforeEachA");
        expected.add("beforeEachB");
        expected.add("testB");
        expected.add("afterEachA");
        expected.add("afterEachB");

        expected.add("beforeEachA");
        expected.add("beforeEachB");
        expected.add("testUnordered");
        expected.add("afterEachA");
        expected.add("afterEachB");

        expected.add("afterAllA");
        expected.add("afterAllUnordered");

        expected.add("finalizeA");
        expected.add("zzFinalizeAssert");

        assertThat(actual).containsExactlyElementsOf(expected);
    }
}
