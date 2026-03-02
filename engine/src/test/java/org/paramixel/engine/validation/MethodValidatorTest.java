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

package org.paramixel.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public class MethodValidatorTest {

    @Test
    public void validateTestClass_reportsTestMethodSignatureFailures() {
        final List<String> messages = MethodValidator.validateTestClass(BadTestMethods.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ArgumentContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));
    }

    @Test
    public void validateTestClass_reportsLifecycleSignatureFailures() {
        final List<String> messages = MethodValidator.validateTestClass(BadLifecycleMethods.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.BeforeEach method").contains("must not be static"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.AfterEach method").contains("must accept exactly one"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.Initialize method").contains("must accept exactly one"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.Finalize method").contains("must not be static"));
    }

    @Test
    public void validateTestClass_allowsStaticBeforeAllAndAfterAll() {
        assertThat(MethodValidator.validateTestClass(StaticBeforeAfterAllOk.class))
                .isEmpty();
    }

    @Test
    public void validateTestClass_reportsBeforeAllAndAfterAllSignatureFailures() {
        final List<String> messages = MethodValidator.validateTestClass(BadBeforeAfterAllMethods.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.BeforeAll method").contains("must accept exactly one"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.BeforeAll method").contains("must return void"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.AfterAll method").contains("must accept exactly one"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.AfterAll method").contains("must return void"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.BeforeAll method").contains("must be public"))
                .anySatisfy(m ->
                        assertThat(m).contains("@Paramixel.AfterAll method").contains("must be public"));
    }

    @Test
    public void validateTestClass_reportsArgumentsCollectorSignatureFailures() {
        final List<String> messages = MethodValidator.validateTestClass(BadCollectorSignature.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("@Paramixel.ArgumentsCollector method must be public"))
                .anySatisfy(m -> assertThat(m).contains("@Paramixel.ArgumentsCollector method must be static"))
                .anySatisfy(m -> assertThat(m).contains("Invalid @Paramixel.ArgumentsCollector method signature"));
    }

    @Test
    public void validateTestClass_validatesOrderAnnotationRules() {
        final List<String> messages = MethodValidator.validateTestClass(OrderRules.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(
                        m -> assertThat(m)
                                .contains(
                                        "@Paramixel.Order annotation is only allowed on @Paramixel.Test and lifecycle hook methods"))
                .anySatisfy(m -> assertThat(m).contains("@Paramixel.Order value must be greater than 0"));
    }

    static class BadTestMethods {

        @Paramixel.Test
        public int badReturn(final ArgumentContext context) {
            return 1;
        }

        @Paramixel.Test
        public static void staticTest(final ArgumentContext context) {}

        @Paramixel.Test
        public void badParam(final String notAContext) {}
    }

    static class BadLifecycleMethods {

        @Paramixel.BeforeEach
        public static void beforeEachStatic(final ArgumentContext context) {}

        @Paramixel.AfterEach
        public void afterEachWrongParam(final ClassContext context) {}

        @Paramixel.Initialize
        public void initializeWrongParam(final ArgumentContext context) {}

        @Paramixel.Finalize
        public static void finalizeStatic(final ClassContext context) {}
    }

    static class StaticBeforeAfterAllOk {

        @Paramixel.BeforeAll
        public static void beforeAll(final ArgumentContext context) {}

        @Paramixel.AfterAll
        public static void afterAll(final ArgumentContext context) {}
    }

    static class BadBeforeAfterAllMethods {

        @Paramixel.BeforeAll
        public int beforeAll(final ClassContext notAnArgumentContext) {
            return 1;
        }

        @Paramixel.AfterAll
        public int afterAll(final ClassContext notAnArgumentContext) {
            return 0;
        }

        @Paramixel.BeforeAll
        private void privateBeforeAll(final ArgumentContext context) {}

        @Paramixel.AfterAll
        private void privateAfterAll(final ArgumentContext context) {}
    }

    static class BadCollectorSignature {

        @Paramixel.ArgumentsCollector
        private Object bad() {
            return null;
        }

        @Paramixel.ArgumentsCollector
        public static Object bad2(final ArgumentsCollector collector) {
            return null;
        }
    }

    static class OrderRules {

        @Paramixel.Test
        @Paramixel.Order(0)
        public void badOrderValue(final ArgumentContext context) {}

        @Paramixel.BeforeEach
        @Paramixel.Order(1)
        public void allowedOnLifecycle(final ArgumentContext context) {}

        @Paramixel.Order(1)
        public void notAllowedWithoutHook(final ArgumentContext context) {}
    }
}
