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

package org.paramixel.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.Paramixel;

public class AnnotationUsageValidatorTest {

    @Test
    public void validateTestClass_reportsMultipleParamixelAnnotationsOnOneMethod() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(MultipleAnnotations.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("declares multiple Paramixel annotations"));
    }

    @Test
    public void validateTestClass_reportsMultipleArgumentsCollectorMethods() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(MultipleCollectors.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages).anySatisfy(m -> assertThat(m)
                .contains("At most one @Paramixel.ArgumentsCollector method is allowed per class"));
    }

    @Test
    public void validateTestClass_reportsBlankDisplayName() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(BlankDisplayName.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages).anySatisfy(m -> assertThat(m).contains("@DisplayName value must be non-blank"));
    }

    static class MultipleAnnotations {

        @Paramixel.Test
        @Paramixel.BeforeEach
        public void bad(final ArgumentContext context) {}
    }

    static class MultipleCollectors {

        @Paramixel.ArgumentsCollector
        public static void c1(final ArgumentsCollector collector) {}

        @Paramixel.ArgumentsCollector
        public static void c2(final ArgumentsCollector collector) {}
    }

    @Paramixel.DisplayName("   ")
    static class BlankDisplayName {

        @Paramixel.Test
        @Paramixel.DisplayName("\t")
        public void test(final ArgumentContext context) {}
    }
}
