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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class ClassThreadLocalTest2 {

    private final ThreadLocal<Integer> threadLocalValue = new ThreadLocal<>();

    @Paramixel.ArgumentSupplier(parallelism = 10)
    public static Collection<String> arguments() {
        Collection<String> collection = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            collection.add("String " + i);
        }
        return collection;
    }

    @Paramixel.Initialize
    public void initialize(final ClassContext classContext) {
        System.out.println("initialize()");
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();

        synchronized (classContext.getTestInstance()) {
            assertThat(threadLocalValue.get()).isNull();
            threadLocalValue.set(1);
            assertThat(threadLocalValue.get()).isEqualTo(1);
        }
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("beforeAll(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("beforeEach(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("test1(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("test2(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.Test
    public void test3(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("test3(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("afterEach(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("afterAll(index=[%d])%n", argumentContext.getArgumentIndex());
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        System.out.println("finalize()");
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
    }
}
