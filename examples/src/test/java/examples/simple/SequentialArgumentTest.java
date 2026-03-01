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
import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates sequential argument execution with Paramixel.
 */
@Paramixel.TestClass
public class SequentialArgumentTest {

    /**
     * Logger for lifecycle output.
     */
    private static final Logger LOGGER = Logger.createLogger(SequentialArgumentTest.class);

    /**
     * Supplies a collection of string arguments.
     *
     * @return the argument collection
     */
    @Paramixel.ArgumentSupplier
    public static Object arguments() {
        Collection<String> collection = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            collection.add("string-" + i);
        }

        return collection;
    }

    /**
     * Initializes class-level resources.
     *
     * @param classContext the class context
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext classContext) {
        LOGGER.info("initialize()");
    }

    /**
     * Executes before all tests for each argument.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("beforeAll() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes before each test invocation.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("beforeEach() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes the first test method.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("test1() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes the second test method.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("test2() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes the third test method.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void test3(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("test3() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes after each test invocation.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("afterEach() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Executes after all tests for each argument.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        LOGGER.info("afterAll() argument [%s]", argumentContext.getArgument());
    }

    /**
     * Finalizes class-level resources.
     *
     * @param classContext the class context
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        LOGGER.info("finalize()");
    }
}
