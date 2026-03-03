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
import org.paramixel.api.Paramixel;

public class AnnotationUsageValidatorTagsTest {

    @Test
    public void validateTestClass_reportsTagsOnNonTestClass() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(TagsOnNonTestClass.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages).anySatisfy(m -> assertThat(m)
                .contains("@Paramixel.Tags can only be used on classes annotated with @Paramixel.TestClass"));
    }

    @Test
    public void validateTestClass_reportsMultipleTagsAnnotations() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(ChildWithTags.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages).anySatisfy(m -> assertThat(m)
                .contains("At most one @Paramixel.Tags annotation is allowed per class hierarchy"));
    }

    @Test
    public void validateTestClass_acceptsSingleTagsAnnotation() {
        assertThat(AnnotationUsageValidator.validateTestClass(SingleTagsOnTestClass.class))
                .isEmpty();
    }

    @Test
    public void validateTestClass_acceptsTagsOnTestClass() {
        assertThat(AnnotationUsageValidator.validateTestClass(TestClassWithTags.class))
                .isEmpty();
    }

    @Test
    public void validateTestClass_reportsEmptyTagsValue() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(TestClassWithEmptyTags.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("@Paramixel.Tags must have at least one tag value"));
    }

    @Test
    public void validateTestClass_reportsBlankTagValues() {
        final List<String> messages = AnnotationUsageValidator.validateTestClass(TestClassWithBlankTag.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("@Paramixel.Tags tag at index 0 must be non-empty"));
    }

    // Test classes for validation

    @Paramixel.Tags({"tag1", "tag2"})
    static class TagsOnNonTestClass {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"parent"})
    static class ParentWithTags {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.Tags({"child"})
    static class ChildWithTags extends ParentWithTags {

        @Paramixel.Test
        public void test2(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"single"})
    static class SingleTagsOnTestClass {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration", "database", "slow"})
    static class TestClassWithTags {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({})
    static class TestClassWithEmptyTags {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"  "})
    static class TestClassWithBlankTag {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }
}
