/*
 * Copyright (c) 2026-present Douglas Hoard
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

package org.paramixel.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Selector")
class SelectorTest {

    @Nested
    @DisplayName("byPackageName(String)")
    class ByPackageNameString {

        @Test
        @DisplayName("matches exact package name")
        void matchesExactPackageName() {
            Selector selector = Selector.byPackageName("com.example");
            assertThat("com.example").matches(selector.getRegex());
        }

        @Test
        @DisplayName("matches subpackages")
        void matchesSubpackages() {
            Selector selector = Selector.byPackageName("com.example");
            assertThat("com.example.foo").matches(selector.getRegex());
            assertThat("com.example.foo.bar").matches(selector.getRegex());
        }

        @Test
        @DisplayName("does not match unrelated packages sharing a prefix")
        void doesNotMatchPrefixPartial() {
            Selector selector = Selector.byPackageName("com.example");
            assertThat("com.examples").doesNotMatch(selector.getRegex());
            assertThat("com.exampleother").doesNotMatch(selector.getRegex());
        }

        @Test
        @DisplayName("single segment package name")
        void singleSegmentPackageName() {
            Selector selector = Selector.byPackageName("foo");
            assertThat(selector.getRegex()).isEqualTo("^foo(\\..*)?$");
            assertThat("foo").matches(selector.getRegex());
            assertThat("foo.bar").matches(selector.getRegex());
            assertThat("foobar").doesNotMatch(selector.getRegex());
        }

        @Test
        @DisplayName("escapes dots in package name")
        void escapesDotsInPackageName() {
            Selector selector = Selector.byPackageName("com.example");
            assertThat(selector.getRegex()).startsWith("^com\\.example");
        }
    }

    @Nested
    @DisplayName("byPackageName(Class<?>)")
    class ByPackageNameClass {

        @Test
        @DisplayName("produces same regex as string overload")
        void producesSameRegexAsStringOverload() {
            Selector fromClass = Selector.byPackageName(SelectorTest.class);
            Selector fromString = Selector.byPackageName(SelectorTest.class.getPackageName());
            assertThat(fromClass.getRegex()).isEqualTo(fromString.getRegex());
        }

        @Test
        @DisplayName("primitive class has java.lang package name")
        void primitiveClassHasJavaLangPackageName() {
            Selector selector = Selector.byPackageName(int.class);
            assertThat(selector.getRegex()).contains("java\\.lang");
        }
    }

    @Nested
    @DisplayName("byClassName(String)")
    class ByClassNameString {

        @Test
        @DisplayName("produces exact match regex for class name")
        void producesExactMatchRegex() {
            Selector selector = Selector.byClassName("com.example.MyClass");
            assertThat(selector.getRegex()).isEqualTo("^com\\.example\\.MyClass$");
        }

        @Test
        @DisplayName("matches only the exact class name")
        void matchesOnlyExactClassName() {
            Selector selector = Selector.byClassName("com.example.MyClass");
            assertThat("com.example.MyClass").matches(selector.getRegex());
            assertThat("com.example.MyClassExtra").doesNotMatch(selector.getRegex());
        }

        @Test
        @DisplayName("does not match subpackages")
        void doesNotMatchSubpackages() {
            Selector selector = Selector.byClassName("com.example.MyClass");
            assertThat("com.example.MyClass.sub").doesNotMatch(selector.getRegex());
        }

        @Test
        @DisplayName("empty string matches only empty string")
        void emptyStringMatchesOnlyEmpty() {
            Selector selector = Selector.byClassName("");
            assertThat(selector.getRegex()).isEqualTo("^$");
            assertThat("").matches(selector.getRegex());
        }
    }

    @Nested
    @DisplayName("byClassName(Class<?>)")
    class ByClassNameClass {

        @Test
        @DisplayName("produces same regex as string overload")
        void producesSameRegexAsStringOverload() {
            Selector fromClass = Selector.byClassName(SelectorTest.class);
            Selector fromString = Selector.byClassName(SelectorTest.class.getName());
            assertThat(fromClass.getRegex()).isEqualTo(fromString.getRegex());
        }

        @Test
        @DisplayName("inner class dollar sign is included in regex")
        void innerClassDollarSignIncludedInRegex() {
            Selector selector = Selector.byClassName("com.example.Outer$Inner");
            assertThat(selector.getRegex()).contains("Outer$Inner");
        }
    }

    @Nested
    @DisplayName("getRegex()")
    class GetRegex {

        @Test
        @DisplayName("returns the constructed regex string")
        void returnsConstructedRegex() {
            Selector selector = Selector.byPackageName("org.paramixel");
            assertThat(selector.getRegex()).isNotBlank();
            assertThat(selector.getRegex()).startsWith("^");
            assertThat(selector.getRegex()).endsWith("$");
        }
    }

    @Nested
    @DisplayName("null input handling")
    class NullInputHandling {

        @Test
        @DisplayName("byPackageName(String) throws NullPointerException for null")
        void byPackageNameStringThrowsForNull() {
            assertThatThrownBy(() -> Selector.byPackageName((String) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("byPackageName(Class<?>) throws NullPointerException for null")
        void byPackageNameClassThrowsForNull() {
            assertThatThrownBy(() -> Selector.byPackageName((Class<?>) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("byClassName(String) throws NullPointerException for null")
        void byClassNameStringThrowsForNull() {
            assertThatThrownBy(() -> Selector.byClassName((String) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("byClassName(Class<?>) throws NullPointerException for null")
        void byClassNameClassThrowsForNull() {
            assertThatThrownBy(() -> Selector.byClassName((Class<?>) null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("immutability and identity")
    class ImmutabilityAndIdentity {

        @Test
        @DisplayName("distinct calls produce distinct instances")
        void distinctCallsProduceDistinctInstances() {
            Selector first = Selector.byPackageName("com.example");
            Selector second = Selector.byPackageName("com.example");
            assertThat(first).isNotSameAs(second);
            assertThat(first.getRegex()).isEqualTo(second.getRegex());
        }

        @Test
        @DisplayName("getRegex always returns same value on repeated calls")
        void getRegexReturnsSameValueOnRepeatedCalls() {
            Selector selector = Selector.byPackageName("com.example");
            String first = selector.getRegex();
            String second = selector.getRegex();
            assertThat(first).isSameAs(second);
        }
    }
}
