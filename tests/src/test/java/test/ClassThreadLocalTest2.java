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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Exercises per-class instance state under high argument parallelism.
 *
 * <p>This test mirrors {@link ClassThreadLocalTest1} and uses a {@link ThreadLocal} field to
 * validate that initialization can safely interact with instance state when arguments execute in
 * parallel.
 */
public class ClassThreadLocalTest2 {

    /** Thread-local value used to validate initialization behavior. */
    private final ThreadLocal<Integer> threadLocalValue = new ThreadLocal<>();

    /**
     * Supplies arguments and configures a high argument parallelism.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(10);
        for (int i = 0; i < 10; i++) {
            collector.addArgument("String " + i);
        }
    }

    /**
     * Initializes class state and validates {@link ThreadLocal} access.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final ClassContext context) {
        System.out.println("initialize()");
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();

        synchronized (context.getTestInstance()) {
            assertThat(threadLocalValue.get()).isNull();
            threadLocalValue.set(1);
            assertThat(threadLocalValue.get()).isEqualTo(1);
        }
    }

    /**
     * Runs once per argument before tests for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        System.out.printf("beforeAll(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Runs before each test invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        System.out.printf("beforeEach(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * First test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        System.out.printf("test1(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Second test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        System.out.printf("test2(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Third test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test3(final @NonNull ArgumentContext context) {
        System.out.printf("test3(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Runs after each test invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        System.out.printf("afterEach(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Runs once per argument after tests for that argument.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        System.out.printf("afterAll(index=[%d])%n", context.getArgumentIndex());
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Finalizes and validates that the class context is still available.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        System.out.println("finalize()");
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }
}
