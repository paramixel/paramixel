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

package examples.simple;

import examples.support.Logger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates parallel argument execution with Paramixel.
 */
@Paramixel.TestClass
public class ParallelArgumentTest {

    /**
     * Logger for lifecycle output.
     */
    private static final Logger LOGGER = Logger.createLogger(ParallelArgumentTest.class);

    /**
     * Supplies a collection of string arguments.
     *
     * @param argumentSupplierContext the argument supplier context
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.setParallelism(2);
        for (int i = 0; i < 10; i++) {
            argumentSupplierContext.addArgument("string-" + i);
        }
    }

    /**
     * Initializes class-level resources.
     *
     * @param context the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        LOGGER.info("initialize()");
    }

    /**
     * Executes before all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        LOGGER.info("beforeAll() argument [%s]", context.getArgument());
    }

    /**
     * Executes before each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        LOGGER.info("beforeEach() argument [%s]", context.getArgument());
    }

    /**
     * Executes the first test method.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        LOGGER.info("test1() argument [%s]", context.getArgument());
    }

    /**
     * Executes the second test method.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        LOGGER.info("test2() argument [%s]", context.getArgument());
    }

    /**
     * Executes the third test method.
     *
     * @param context the argument context
     */
    @Paramixel.Test
    public void test3(final @NonNull ArgumentContext context) {
        LOGGER.info("test3() argument [%s]", context.getArgument());
    }

    /**
     * Executes after each test invocation.
     *
     * @param context the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        LOGGER.info("afterEach() argument [%s]", context.getArgument());
    }

    /**
     * Executes after all tests for each argument.
     *
     * @param context the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        LOGGER.info("afterAll() argument [%s]", context.getArgument());
    }

    /**
     * Finalizes class-level resources.
     *
     * @param context the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        LOGGER.info("finalize()");
    }
}
