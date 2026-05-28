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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnnotationResolver arguments")
class AnnotationResolverArgumentsTest {

    @BeforeEach
    void clearFixtures() {
        AnnotationResolver.clearAllCache();
    }

    @Test
    @DisplayName("create rejects null type")
    void createRejectsNullType() {
        assertThatThrownBy(() -> AnnotationResolver.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type is null");
    }

    @Test
    @DisplayName("byId rejects null id")
    void byIdRejectsNullId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.ResolveFixture.class)
                        .byId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id is null");
    }

    @Test
    @DisplayName("byId rejects blank id")
    void byIdRejectsBlankId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.ResolveFixture.class)
                        .byId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id is blank");
    }

    @Test
    @DisplayName("byId rejects missing id")
    void byIdRejectsMissingId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.MissingFixture.class)
                        .byId("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no method annotated with @Paramixel.Id(\"missing\") was found on")
                .hasMessageContaining(AnnotationResolverTest.MissingFixture.class.getName());
    }

    @Test
    @DisplayName("byId rejects duplicate id")
    void byIdRejectsDuplicateId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.DuplicateFixture.class)
                        .byId("dup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple methods annotated with @Paramixel.Id(\"dup\") were found on")
                .hasMessageContaining(AnnotationResolverTest.DuplicateFixture.class.getName());
    }

    @Test
    @DisplayName("byId skips static method and reports not found")
    void byIdSkipsStaticMethod() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticFixture.class)
                        .byId("static"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no method annotated with @Paramixel.Id(\"static\") was found on");
    }

    @Test
    @DisplayName("byId rejects method with parameters")
    void byIdRejectsMethodWithParameters() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.ParamsFixture.class)
                        .byId("params"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected an instance no-argument void method");
    }

    @Test
    @DisplayName("staticById rejects null id")
    void staticByIdRejectsNullId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticResolveFixture.class)
                        .staticById(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id is null");
    }

    @Test
    @DisplayName("staticById rejects blank id")
    void staticByIdRejectsBlankId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticResolveFixture.class)
                        .staticById(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id is blank");
    }

    @Test
    @DisplayName("staticById rejects missing id")
    void staticByIdRejectsMissingId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.MissingFixture.class)
                        .staticById("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no static method annotated with @Paramixel.Id(\"missing\") was found on")
                .hasMessageContaining(AnnotationResolverTest.MissingFixture.class.getName());
    }

    @Test
    @DisplayName("staticById rejects duplicate id")
    void staticByIdRejectsDuplicateId() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticDuplicateFixture.class)
                        .staticById("staticDup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple methods annotated with @Paramixel.Id(\"staticDup\") were found on")
                .hasMessageContaining(AnnotationResolverTest.StaticDuplicateFixture.class.getName());
    }

    @Test
    @DisplayName("staticById skips instance method and reports not found")
    void staticByIdSkipsInstanceMethod() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.ResolveFixture.class)
                        .staticById("foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no static method annotated with @Paramixel.Id(\"foo\") was found on");
    }

    @Test
    @DisplayName("staticById rejects static method with parameters")
    void staticByIdRejectsStaticMethodWithParameters() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticParamsFixture.class)
                        .staticById("staticParams"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected a static no-argument void method");
    }

    @Test
    @DisplayName("staticById rejects static method with non-void return")
    void staticByIdRejectsStaticMethodWithNonVoidReturn() {
        assertThatThrownBy(() -> AnnotationResolver.create(AnnotationResolverTest.StaticNonVoidFixture.class)
                        .staticById("staticNonvoid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected a static no-argument void method");
    }
}
