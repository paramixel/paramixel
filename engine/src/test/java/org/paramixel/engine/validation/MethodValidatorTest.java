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

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public class MethodValidatorTest {

    @Test
    public void constructor_isAccessible_forCoverage() {
        assertThat(new MethodValidator()).isNotNull();
    }

    @Test
    public void validateTestClass_reportsTestMethodSignatureFailures() {
        final List<MethodValidator.ValidationFailure> failures =
                MethodValidator.validateTestClass(BadTestMethods.class);

        final List<String> messages = failures.stream()
                .map(MethodValidator.ValidationFailure::getMessage)
                .toList();
        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ArgumentContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));
    }

    @Test
    public void validateTestClass_reportsLifecycleSignatureFailures() {
        final List<String> messages = MethodValidator.validateTestClass(BadLifecycleMethods.class).stream()
                .map(MethodValidator.ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("BeforeEach method").contains("must not be static"))
                .anySatisfy(m -> assertThat(m).contains("AfterEach method").contains("must accept exactly one"))
                .anySatisfy(m -> assertThat(m).contains("Initialize method").contains("must accept exactly one"))
                .anySatisfy(m -> assertThat(m).contains("Finalize method").contains("must not be static"));
    }

    @Test
    public void validateTestClass_allowsStaticBeforeAllAndAfterAll() {
        assertThat(MethodValidator.validateTestClass(StaticBeforeAfterAllOk.class))
                .isEmpty();
    }

    @Test
    public void validateTestClass_validatesOrderAnnotationRules() {
        final List<String> messages = MethodValidator.validateTestClass(OrderRules.class).stream()
                .map(MethodValidator.ValidationFailure::getMessage)
                .toList();

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("Order annotation is only allowed on @Test methods"))
                .anySatisfy(m -> assertThat(m).contains("Order value must be greater than 0"));
    }

    @Test
    public void validateTestClass_reportsBeforeAllAndAfterAllSignatureFailures() throws Exception {
        final List<String> messages = MethodValidator.validateTestClass(BadBeforeAfterAllMethods.class).stream()
                .map(MethodValidator.ValidationFailure::getMessage)
                .toList();

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("BeforeAll method").contains("must accept exactly one"))
                .anySatisfy(m -> assertThat(m).contains("BeforeAll method").contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("AfterAll method").contains("must accept exactly one"))
                .anySatisfy(m -> assertThat(m).contains("AfterAll method").contains("must return void"));

        final var validateBeforeAll = MethodValidator.class.getDeclaredMethod("validateBeforeAllMethod", Method.class);
        validateBeforeAll.setAccessible(true);
        final var privateBeforeAll =
                BadBeforeAfterAllMethods.class.getDeclaredMethod("privateBeforeAll", ArgumentContext.class);
        @SuppressWarnings("unchecked")
        final List<MethodValidator.ValidationFailure> beforeAllFailures =
                (List<MethodValidator.ValidationFailure>) validateBeforeAll.invoke(null, privateBeforeAll);
        assertThat(beforeAllFailures)
                .anySatisfy(f ->
                        assertThat(f.getMessage()).contains("BeforeAll method").contains("must be public"));

        final var validateAfterAll = MethodValidator.class.getDeclaredMethod("validateAfterAllMethod", Method.class);
        validateAfterAll.setAccessible(true);
        final var privateAfterAll =
                BadBeforeAfterAllMethods.class.getDeclaredMethod("privateAfterAll", ArgumentContext.class);
        @SuppressWarnings("unchecked")
        final List<MethodValidator.ValidationFailure> afterAllFailures =
                (List<MethodValidator.ValidationFailure>) validateAfterAll.invoke(null, privateAfterAll);
        assertThat(afterAllFailures)
                .anySatisfy(f ->
                        assertThat(f.getMessage()).contains("AfterAll method").contains("must be public"));
    }

    @Test
    public void privateValidationMethods_reportVisibilityParameterReturnAndStaticFailures() throws Exception {
        assertValidationFailures(
                        "validateTestMethod", PrivateInvalidMethods.class.getDeclaredMethod("badTest", String.class))
                .anySatisfy(m -> assertThat(m).contains("Test method").contains("must be public"))
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ArgumentContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));

        assertValidationFailures(
                        "validateBeforeEachMethod",
                        PrivateInvalidMethods.class.getDeclaredMethod("badBeforeEach", String.class))
                .anySatisfy(m -> assertThat(m).contains("BeforeEach method").contains("must be public"))
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ArgumentContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));

        assertValidationFailures(
                        "validateAfterEachMethod",
                        PrivateInvalidMethods.class.getDeclaredMethod("badAfterEach", String.class))
                .anySatisfy(m -> assertThat(m).contains("AfterEach method").contains("must be public"))
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ArgumentContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));

        assertValidationFailures(
                        "validateInitializeMethod",
                        PrivateInvalidMethods.class.getDeclaredMethod("badInitialize", String.class))
                .anySatisfy(m -> assertThat(m).contains("Initialize method").contains("must be public"))
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ClassContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));

        assertValidationFailures(
                        "validateFinalizeMethod",
                        PrivateInvalidMethods.class.getDeclaredMethod("badFinalize", String.class))
                .anySatisfy(m -> assertThat(m).contains("Finalize method").contains("must be public"))
                .anySatisfy(m -> assertThat(m).contains("must accept exactly one ClassContext"))
                .anySatisfy(m -> assertThat(m).contains("must return void"))
                .anySatisfy(m -> assertThat(m).contains("must not be static"));
    }

    private static ListAssert<String> assertValidationFailures(final String methodName, final Method method)
            throws Exception {
        final var m = MethodValidator.class.getDeclaredMethod(methodName, Method.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<MethodValidator.ValidationFailure> failures =
                (List<MethodValidator.ValidationFailure>) m.invoke(null, method);
        final List<String> messages = failures.stream()
                .map(MethodValidator.ValidationFailure::getMessage)
                .toList();
        return assertThat(messages);
    }

    public static class BadTestMethods {

        @Paramixel.Test
        public int badReturn(final ArgumentContext context) {
            return 1;
        }

        @Paramixel.Test
        public static void staticTest(final ArgumentContext context) {}

        @Paramixel.Test
        public void badParam(final String notAContext) {}
    }

    public static class BadLifecycleMethods {

        @Paramixel.BeforeEach
        public static void beforeEachStatic(final ArgumentContext context) {}

        @Paramixel.AfterEach
        public void afterEachWrongParam(final ClassContext context) {}

        @Paramixel.Initialize
        public void initializeWrongParam(final ArgumentContext context) {}

        @Paramixel.Finalize
        public static void finalizeStatic(final ClassContext context) {}
    }

    public static class StaticBeforeAfterAllOk {

        @Paramixel.BeforeAll
        public static void beforeAll(final ArgumentContext context) {}

        @Paramixel.AfterAll
        public static void afterAll(final ArgumentContext context) {}
    }

    public static class OrderRules {

        @Paramixel.Order(1)
        public void orderOnNonTest(final ArgumentContext context) {}

        @Paramixel.Test
        @Paramixel.Order(0)
        public void badOrderValue(final ArgumentContext context) {}
    }

    public static class BadBeforeAfterAllMethods {

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

    public static class PrivateInvalidMethods {

        @Paramixel.Test
        private static int badTest(final String notAContext) {
            return 1;
        }

        @Paramixel.BeforeEach
        private static int badBeforeEach(final String notAContext) {
            return 1;
        }

        @Paramixel.AfterEach
        private static int badAfterEach(final String notAContext) {
            return 1;
        }

        @Paramixel.Initialize
        private static int badInitialize(final String notAContext) {
            return 1;
        }

        @Paramixel.Finalize
        private static int badFinalize(final String notAContext) {
            return 1;
        }
    }
}
