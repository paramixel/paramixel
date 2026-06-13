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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Step;

@DisplayName("AnnotationResolver")
class AnnotationResolverTest {

    @BeforeEach
    void clearFixtures() {
        AnnotationResolver.clearAllCache();
    }

    static class ResolveFixture {
        boolean ran;

        @Paramixel.Id("foo")
        public void foo() {
            ran = true;
        }
    }

    @Test
    @DisplayName("byId returns action for instance method")
    void byId_returnsActionForInstanceMethod() {
        var action = AnnotationResolver.create(ResolveFixture.class).byId("foo");
        assertThat(action).isNotNull();
        assertThat(action.displayName()).isEqualTo("foo");
    }

    @Test
    @DisplayName("byId returns action with name")
    void byId_returnsActionWithName() {
        var action = AnnotationResolver.create(ResolveFixture.class).byId("foo");
        assertThat(action).isNotNull();
        assertThat(action.displayName()).isEqualTo("foo");
        assertThat(action).isInstanceOf(Step.class);
    }

    static class MissingFixture {}

    static class DuplicateFixture {
        @Paramixel.Id("dup")
        public void first() {}

        @Paramixel.Id("dup")
        public void second() {}
    }

    static class StaticFixture {
        @Paramixel.Id("static")
        public static void staticMethod() {}
    }

    static class ParamsFixture {
        @Paramixel.Id("params")
        public void methodWithParams(final String s) {}
    }

    static class NonVoidFixture {
        @Paramixel.Id("nonvoid")
        public String nonVoidMethod() {
            return "x";
        }
    }

    private static class ParentWithAnnotatedMethod {
        boolean parentRan;

        @Paramixel.Id("parent")
        public void parentMethod() {
            parentRan = true;
        }
    }

    private static class ChildInheritsAnnotatedMethod extends ParentWithAnnotatedMethod {}

    @Test
    @DisplayName("byId returns action for inherited method")
    void byId_returnsActionForInheritedMethod() {
        var action =
                AnnotationResolver.create(ChildInheritsAnnotatedMethod.class).byId("parent");
        assertThat(action).isNotNull();
        assertThat(action.displayName()).isEqualTo("parent");
    }

    private static class ThrowingFixture {
        @Paramixel.Id("throw")
        public void throwingMethod() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    @DisplayName("byId unwraps InvocationTargetException")
    void byIdUnwrapsInvocationTargetException() {
        var descriptor = runById(ThrowingFixture.class, ThrowingFixture::new, "throw");

        assertThat(descriptor.isFailed()).isTrue();
        Throwable throwable = descriptor.throwable().orElseThrow();
        assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("boom");
        assertThat(throwable.getCause()).isNull();
    }

    @Test
    @DisplayName("byId uses cached discovery")
    void byId_usesCachedDiscovery() {
        AnnotationResolver.create(CacheFixture.class).byId("cache");
        AnnotationResolver.create(CacheFixture.class).byId("cache");
    }

    private static class CacheFixture {
        @Paramixel.Id("cache")
        public void cachedMethod() {}
    }

    @Test
    @DisplayName("clearCache removes specific type entry")
    void clearCacheRemovesSpecificTypeEntry() {
        AnnotationResolver.create(CacheClearFixture.class).byId("cached");
        AnnotationResolver.clearCache(CacheClearFixture.class);
        AnnotationResolver.create(CacheClearFixture.class).byId("cached");
    }

    @Test
    @DisplayName("clearAllCache removes all entries")
    void clearAllCacheRemovesAllEntries() {
        AnnotationResolver.create(CacheClearFixture.class).byId("cached");
        AnnotationResolver.clearAllCache();
        AnnotationResolver.create(CacheClearFixture.class).byId("cached");
    }

    private static class ErrorThrowingFixture {
        @Paramixel.Id("errorMethod")
        public void errorMethod() {
            throw new AssertionError("test error");
        }
    }

    @Test
    @DisplayName("byId unwraps Error from InvocationTargetException")
    void byIdUnwrapsErrorFromInvocationTargetException() {
        var descriptor = runById(ErrorThrowingFixture.class, ErrorThrowingFixture::new, "errorMethod");

        assertThat(descriptor.isFailed()).isTrue();
        Throwable throwable = descriptor.throwable().orElseThrow();
        assertThat(throwable).isInstanceOf(AssertionError.class).hasMessage("test error");
        assertThat(throwable.getCause()).isNull();
    }

    private static class CheckedThrowingFixture {
        @Paramixel.Id("checkedMethod")
        public void checkedMethod() throws Exception {
            throw new Exception("checked exception");
        }
    }

    @Test
    @DisplayName("byId wraps checked exception in RuntimeException")
    void byIdWrapsCheckedExceptionInRuntimeException() {
        var descriptor = runById(CheckedThrowingFixture.class, CheckedThrowingFixture::new, "checkedMethod");

        assertThat(descriptor.isFailed()).isTrue();
        assertThat(descriptor.throwable().orElseThrow())
                .isInstanceOf(Exception.class)
                .hasMessage("checked exception");
    }

    private static class CacheClearFixture {
        @Paramixel.Id("cached")
        public void cachedMethod() {}
    }

    static class StaticResolveFixture {
        static boolean staticRan;

        @Paramixel.Id("staticFoo")
        public static void staticFoo() {
            staticRan = true;
        }
    }

    static class StaticDuplicateFixture {
        @Paramixel.Id("staticDup")
        public static void first() {}

        @Paramixel.Id("staticDup")
        public static void second() {}
    }

    static class StaticThrowingFixture {
        @Paramixel.Id("staticThrow")
        public static void throwingMethod() {
            throw new RuntimeException("static boom");
        }
    }

    @Test
    @DisplayName("staticById unwraps InvocationTargetException")
    void staticByIdUnwrapsInvocationTargetException() {
        var descriptor = runStaticById(StaticThrowingFixture.class, "staticThrow");

        assertThat(descriptor.isFailed()).isTrue();
        Throwable throwable = descriptor.throwable().orElseThrow();
        assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("static boom");
        assertThat(throwable.getCause()).isNull();
    }

    static class StaticErrorThrowingFixture {
        @Paramixel.Id("staticErrorMethod")
        public static void errorMethod() {
            throw new AssertionError("static test error");
        }
    }

    @Test
    @DisplayName("staticById unwraps Error from InvocationTargetException")
    void staticByIdUnwrapsErrorFromInvocationTargetException() {
        var descriptor = runStaticById(StaticErrorThrowingFixture.class, "staticErrorMethod");

        assertThat(descriptor.isFailed()).isTrue();
        Throwable throwable = descriptor.throwable().orElseThrow();
        assertThat(throwable).isInstanceOf(AssertionError.class).hasMessage("static test error");
        assertThat(throwable.getCause()).isNull();
    }

    static class StaticCheckedThrowingFixture {
        @Paramixel.Id("staticCheckedMethod")
        public static void checkedMethod() throws Exception {
            throw new Exception("static checked exception");
        }
    }

    @Test
    @DisplayName("staticById wraps checked exception in RuntimeException")
    void staticByIdWrapsCheckedExceptionInRuntimeException() {
        var descriptor = runStaticById(StaticCheckedThrowingFixture.class, "staticCheckedMethod");

        assertThat(descriptor.isFailed()).isTrue();
        assertThat(descriptor.throwable().orElseThrow())
                .isInstanceOf(Exception.class)
                .hasMessage("static checked exception");
    }

    static class StaticCacheFixture {
        @Paramixel.Id("staticCache")
        public static void cachedStaticMethod() {}
    }

    static class StaticParamsFixture {
        @Paramixel.Id("staticParams")
        public static void methodWithParams(final String s) {}
    }

    static class StaticNonVoidFixture {
        @Paramixel.Id("staticNonvoid")
        public static String nonVoidMethod() {
            return "x";
        }
    }

    @Test
    @DisplayName("staticById returns action for static method")
    void staticById_returnsActionForStaticMethod() {
        var action = AnnotationResolver.create(StaticResolveFixture.class).staticById("staticFoo");
        assertThat(action).isNotNull();
        assertThat(action.displayName()).isEqualTo("staticFoo");
    }

    @Test
    @DisplayName("staticById returns action with name")
    void staticById_returnsActionWithName() {
        var action = AnnotationResolver.create(StaticResolveFixture.class).staticById("staticFoo");
        assertThat(action).isNotNull();
        assertThat(action.displayName()).isEqualTo("staticFoo");
        assertThat(action).isInstanceOf(Step.class);
    }

    @Test
    @DisplayName("staticById uses cached discovery")
    void staticById_usesCachedDiscovery() {
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
    }

    @Test
    @DisplayName("clearCache removes static cache entry for type")
    void clearCacheRemovesStaticCacheEntry() {
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
        AnnotationResolver.clearCache(StaticCacheFixture.class);
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
    }

    @Test
    @DisplayName("clearAllCache removes static cache entries")
    void clearAllCacheRemovesStaticCacheEntries() {
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
        AnnotationResolver.clearAllCache();
        AnnotationResolver.create(StaticCacheFixture.class).staticById("staticCache");
    }

    private static Descriptor runById(final Class<?> type, final Supplier<?> supplier, final String id) {
        var action = AnnotationResolver.create(type).byId(id);
        var root = Runner.builder()
                .build()
                .run(Instance.builder("wrapper", supplier).body(action).build())
                .descriptor()
                .orElseThrow();
        return root.children().stream()
                .filter(child -> child.action().displayName().equals(id))
                .findFirst()
                .orElse(root);
    }

    private static Descriptor runStaticById(final Class<?> type, final String id) {
        var action = AnnotationResolver.create(type).staticById(id);
        return Runner.builder().build().run(action).descriptor().orElseThrow();
    }
}
