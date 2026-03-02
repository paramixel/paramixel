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

package test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that a custom argument can coordinate lifecycle execution using a shared {@link Lock}.
 */
public class CustomArgumentWithLockTest {

    /**
     * Supplies custom arguments that share a single fair lock and enables high parallelism.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.setParallelism(10);
        Lock lock = new ReentrantLock(true);
        for (int i = 0; i < 10; i++) {
            argumentSupplierContext.addArgument(new CustomArgument(lock, "String " + i));
        }
    }

    /**
     * Verifies that the class context and store are available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Acquires the shared lock once per argument before tests run.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        CustomArgument argument = (CustomArgument) context.getArgument();
        argument.lock();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Asserts that the argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Asserts that the argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Releases the shared lock once per argument after all tests run.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        CustomArgument argument = (CustomArgument) context.getArgument();
        argument.unlock();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Verifies that the class context and store remain available during finalize.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Custom argument that exposes lock/unlock operations.
     */
    public static final class CustomArgument implements Named {

        /** Shared lock used to coordinate lifecycle execution. */
        private final Lock lock;

        /** Value returned as the argument name. */
        private final String value;

        /**
         * Creates a new argument.
         *
         * @param lock shared lock
         * @param value value returned as the name
         */
        public CustomArgument(final @NonNull Lock lock, final @NonNull String value) {
            this.lock = lock;
            this.value = value;
        }

        @Override
        public String getName() {
            return value;
        }

        public void lock() {
            lock.lock();
        }

        /** Releases the shared lock. */
        public void unlock() {
            lock.unlock();
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
