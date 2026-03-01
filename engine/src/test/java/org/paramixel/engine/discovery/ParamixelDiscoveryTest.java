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

package org.paramixel.engine.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.NestedClassSelector;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;

/**
 * Tests for {@link ParamixelDiscovery} selector handling.
 */
@Disabled
public class ParamixelDiscoveryTest {

    /**
     * Discovery instance under test.
     */
    private ParamixelDiscovery discovery;

    /**
     * Root engine descriptor used for discovery output.
     */
    private TestDescriptor engineDescriptor;

    /**
     * Initializes the discovery instance and engine descriptor.
     */
    @BeforeEach
    public void setUp() {
        discovery = new ParamixelDiscovery();
        engineDescriptor = new ParamixelEngineDescriptor(UniqueId.forEngine("paramixel"), "Paramixel");
    }

    /**
     * Creates a discovery request with the provided selectors.
     *
     * @param selectors the selectors to include
     * @return a discovery request
     */
    private EngineDiscoveryRequest createRequest(final DiscoverySelector... selectors) {
        return LauncherDiscoveryRequestBuilder.request().selectors(selectors).build();
    }

    /**
     * Verifies discovery behavior for class selectors.
     */
    @Nested
    @DisplayName("ClassSelector tests")
    public class ClassSelectorTests {

        /**
         * Ensures a class selector discovers a test class.
         */
        @Test
        @DisplayName("Should discover test class from ClassSelector")
        public void shouldDiscoverTestClassFromClassSelector() {
            ClassSelector selector = DiscoverySelectors.selectClass(SelectorTestClass.class);
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            TestDescriptor classDescriptor =
                    engineDescriptor.getChildren().iterator().next();
            assertThat(classDescriptor).isInstanceOf(ParamixelTestClassDescriptor.class);

            ParamixelTestClassDescriptor paramixelClassDescriptor = (ParamixelTestClassDescriptor) classDescriptor;
            assertThat(paramixelClassDescriptor.getTestClass().getName()).isEqualTo(SelectorTestClass.class.getName());
        }

        /**
         * Ensures non-test classes are ignored.
         */
        @Test
        @DisplayName("Should ignore non-TestClass from ClassSelector")
        public void shouldIgnoreNonTestClassFromClassSelector() {
            ClassSelector selector = DiscoverySelectors.selectClass(NonTestClass.class);
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).isEmpty();
        }

        /**
         * Ensures multiple class selectors are handled.
         */
        @Test
        @DisplayName("Should handle multiple ClassSelectors")
        public void shouldHandleMultipleClassSelectors() {
            ClassSelector selector1 = DiscoverySelectors.selectClass(SelectorTestClass.class);
            ClassSelector selector2 = DiscoverySelectors.selectClass(AnotherTestClass.class);
            EngineDiscoveryRequest request = createRequest(selector1, selector2);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(2);
        }
    }

    /**
     * Verifies discovery behavior for method selectors.
     */
    @Nested
    @DisplayName("MethodSelector tests")
    class MethodSelectorTests {

        /**
         * Ensures a method selector discovers a test class.
         */
        @Test
        @DisplayName("Should discover test class from MethodSelector")
        public void shouldDiscoverTestClassFromMethodSelector() {
            MethodSelector selector = DiscoverySelectors.selectMethod(SelectorTestClass.class, "testMethod");
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            TestDescriptor classDescriptor =
                    engineDescriptor.getChildren().iterator().next();
            assertThat(classDescriptor).isInstanceOf(ParamixelTestClassDescriptor.class);
        }

        /**
         * Ensures method selectors for test classes are supported.
         */
        @Test
        @DisplayName("Should discover test class from MethodSelector for TestClass")
        public void shouldDiscoverFromMethodSelectorWithTestClass() {
            MethodSelector selector = DiscoverySelectors.selectMethod(SelectorTestClass.class, "testMethod");
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
        }
    }

    /**
     * Verifies discovery behavior for package selectors.
     */
    @Nested
    @DisplayName("PackageSelector tests")
    class PackageSelectorTests {

        /**
         * Ensures package selectors discover classes in the package.
         */
        @Test
        @DisplayName("Should discover test classes from PackageSelector")
        public void shouldDiscoverTestClassesFromPackageSelector() {
            PackageSelector selector = DiscoverySelectors.selectPackage("org.paramixel.engine.discovery");
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            // Should find SelectorTestClass and AnotherTestClass in this package
            Set<String> testClassNames = engineDescriptor.getChildren().stream()
                    .map(d -> d.getDisplayName())
                    .collect(Collectors.toSet());

            assertThat(testClassNames)
                    .contains(
                            "org.paramixel.engine.discovery.SelectorTestClass",
                            "org.paramixel.engine.discovery.AnotherTestClass");
        }

        /**
         * Ensures non-existent packages do not throw errors.
         */
        @Test
        @DisplayName("Should not fail for non-existent package")
        public void shouldNotFailForNonExistentPackage() {
            PackageSelector selector = DiscoverySelectors.selectPackage("com.nonexistent.package");
            EngineDiscoveryRequest request = createRequest(selector);

            assertThatCode(() -> discovery.discoverTests(request, engineDescriptor))
                    .doesNotThrowAnyException();
            assertThat(engineDescriptor.getChildren()).isEmpty();
        }

        /**
         * Ensures non-test classes are not discovered from packages.
         */
        @Test
        @DisplayName("Should not discover non-test classes from PackageSelector")
        public void shouldNotDiscoverNonTestClassesFromPackageSelector() {
            PackageSelector selector = DiscoverySelectors.selectPackage("org.paramixel.engine.discovery");
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            // Verify that NonTestClass is not discovered
            Set<String> testClassNames = engineDescriptor.getChildren().stream()
                    .map(d -> d.getDisplayName())
                    .collect(Collectors.toSet());

            assertThat(testClassNames).doesNotContain("NonTestClass");
        }
    }

    /**
     * Verifies behavior when multiple selector types are used.
     */
    @Nested
    @DisplayName("Combined selector tests")
    class CombinedSelectorTests {

        /**
         * Ensures multiple selector types are handled.
         */
        @Test
        @DisplayName("Should handle multiple different selector types")
        public void shouldHandleMultipleDifferentSelectorTypes() {
            ClassSelector classSelector = DiscoverySelectors.selectClass(SelectorTestClass.class);
            MethodSelector methodSelector =
                    DiscoverySelectors.selectMethod(AnotherTestClass.class, "testWithAnnotation");

            EngineDiscoveryRequest request = createRequest(classSelector, methodSelector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(2);
        }

        /**
         * Ensures duplicate classes are not added twice.
         */
        @Test
        @DisplayName("Should deduplicate classes from multiple selectors")
        public void shouldDeduplicateClassesFromMultipleSelectors() {
            // Both selectors point to the same class
            ClassSelector classSelector = DiscoverySelectors.selectClass(SelectorTestClass.class);
            MethodSelector methodSelector = DiscoverySelectors.selectMethod(SelectorTestClass.class, "testMethod");

            EngineDiscoveryRequest request = createRequest(classSelector, methodSelector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
        }
    }

    /**
     * Verifies behavior with empty discovery requests.
     */
    @Nested
    @DisplayName("Empty request tests")
    class EmptyRequestTests {

        /**
         * Ensures empty requests return no classes.
         */
        @Test
        @DisplayName("Should handle empty discovery request")
        public void shouldHandleEmptyDiscoveryRequest() {
            EngineDiscoveryRequest request = createRequest();

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).isEmpty();
        }
    }

    /**
     * Verifies annotation-based filtering behavior.
     */
    @Nested
    @DisplayName("Selector filtering tests")
    class SelectorFilteringTests {

        /**
         * Ensures only @TestClass-annotated classes are discovered.
         */
        @Test
        @DisplayName("Should only discover TestClass annotated classes")
        public void shouldOnlyDiscoverTestClassAnnotatedClasses() {
            PackageSelector selector = DiscoverySelectors.selectPackage("org.paramixel.engine.discovery");
            EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            // Verify all discovered classes have @TestClass
            for (TestDescriptor child : engineDescriptor.getChildren()) {
                assertThat(child).isInstanceOf(ParamixelTestClassDescriptor.class);
                Class<?> testClass = ((ParamixelTestClassDescriptor) child).getTestClass();
                assertThat(testClass).hasAnnotation(Paramixel.TestClass.class);
            }
        }
    }

    @Nested
    @DisplayName("UniqueIdSelector tests")
    class UniqueIdSelectorTests {

        @Test
        @DisplayName("Should discover test class from UniqueIdSelector")
        public void shouldDiscoverTestClassFromUniqueIdSelector() {
            final UniqueId uniqueId =
                    UniqueId.forEngine("paramixel").append("class", SelectorTestClass.class.getName());
            final UniqueIdSelector selector = DiscoverySelectors.selectUniqueId(uniqueId);
            final EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            assertThat(engineDescriptor.getChildren().iterator().next().getDisplayName())
                    .isEqualTo(SelectorTestClass.class.getName());
        }
    }

    @Nested
    @DisplayName("Discovery filters tests")
    class FiltersTests {

        @Test
        @DisplayName("ClassNameFilter should exclude non-matching classes")
        public void classNameFilterShouldExcludeNonMatchingClasses() {
            final EngineDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectPackage("org.paramixel.engine.discovery"))
                    .filters(ClassNameFilter.includeClassNamePatterns(".*SelectorTestClass"))
                    .build();

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            assertThat(engineDescriptor.getChildren().iterator().next().getDisplayName())
                    .isEqualTo(SelectorTestClass.class.getName());
        }

        @Test
        @DisplayName("PackageNameFilter should exclude matching packages")
        public void packageNameFilterShouldExcludeMatchingPackages() {
            final EngineDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectPackage("org.paramixel.engine.discovery"))
                    .filters(PackageNameFilter.excludePackageNames("org.paramixel.engine.discovery"))
                    .build();

            discovery.discoverTests(request, engineDescriptor);
            assertThat(engineDescriptor.getChildren()).isEmpty();
        }
    }

    @Nested
    @DisplayName("NestedClassSelector tests")
    class NestedClassSelectorTests {

        @Test
        @DisplayName("Should discover nested test class from NestedClassSelector")
        public void shouldDiscoverNestedTestClass() {
            final NestedClassSelector selector =
                    DiscoverySelectors.selectNestedClass(java.util.List.of(Outer.class), Outer.NestedTestClass.class);
            final EngineDiscoveryRequest request = createRequest(selector);

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            assertThat(engineDescriptor.getChildren().iterator().next().getDisplayName())
                    .isEqualTo(Outer.NestedTestClass.class.getName());
        }
    }

    @Nested
    @DisplayName("ClasspathRootSelector tests")
    class ClasspathRootSelectorTests {

        @Test
        @DisplayName("Should discover test classes from ClasspathRootSelector with filters")
        public void shouldDiscoverFromClasspathRootSelectorWithFilters() throws Exception {
            final var uri = ParamixelDiscoveryTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            final Path root = Paths.get(uri);
            final ClasspathRootSelector selector =
                    DiscoverySelectors.selectClasspathRoots(Set.of(root)).get(0);
            final EngineDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selector)
                    .filters(ClassNameFilter.includeClassNamePatterns(".*SelectorTestClass"))
                    .build();

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).hasSize(1);
            assertThat(engineDescriptor.getChildren().iterator().next().getDisplayName())
                    .isEqualTo(SelectorTestClass.class.getName());
        }
    }

    @Nested
    @DisplayName("DisplayName/Disabled/Arguments tests")
    class AdditionalDiscoveryBehaviorTests {

        @Test
        @DisplayName("Should use @DisplayName when present for classes and methods")
        public void shouldUseDisplayNameAnnotations() {
            final EngineDiscoveryRequest request =
                    createRequest(DiscoverySelectors.selectClass(DisplayNameTestClass.class));

            discovery.discoverTests(request, engineDescriptor);

            final var clazz = (ParamixelTestClassDescriptor)
                    engineDescriptor.getChildren().iterator().next();
            assertThat(clazz.getDisplayName()).isEqualTo("My Class");

            final var argument = (org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor)
                    clazz.getChildren().iterator().next();
            assertThat(argument.getChildren())
                    .anySatisfy(d -> assertThat(d.getDisplayName()).isEqualTo("My Method"));
        }

        @Test
        @DisplayName("Should skip @Disabled test classes")
        public void shouldSkipDisabledTestClasses() {
            final EngineDiscoveryRequest request =
                    createRequest(DiscoverySelectors.selectClass(DisabledTestClass.class));

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).isEmpty();
        }

        @Test
        @DisplayName("Should skip @Disabled test methods")
        public void shouldSkipDisabledTestMethods() {
            final EngineDiscoveryRequest request =
                    createRequest(DiscoverySelectors.selectClass(DisabledMethodTestClass.class));

            discovery.discoverTests(request, engineDescriptor);

            final var clazz = (ParamixelTestClassDescriptor)
                    engineDescriptor.getChildren().iterator().next();
            final var argument = (org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor)
                    clazz.getChildren().iterator().next();

            assertThat(argument.getChildren()).hasSize(1);
            assertThat(argument.getChildren().iterator().next().getDisplayName())
                    .isEqualTo("enabled");
        }

        @Test
        @DisplayName("Should create argument descriptors from various argument supplier return types")
        public void shouldCreateArgumentsFromSupplierReturnTypes() {
            assertArgumentCount(SupplierStreamTestClass.class, 2);
            assertArgumentCount(SupplierCollectionTestClass.class, 2);
            assertArgumentCount(SupplierIterableTestClass.class, 2);
            assertArgumentCount(SupplierIterableOnlyTestClass.class, 2);
            assertArgumentCount(SupplierArrayTestClass.class, 2);
            assertArgumentCount(SupplierSingleObjectTestClass.class, 1);
        }

        @Test
        @DisplayName("Named arguments should use Named.getName() for argument descriptor display")
        public void namedArgumentsUseNamedName() {
            final TestDescriptor engine = new ParamixelEngineDescriptor(UniqueId.forEngine("paramixel"), "Paramixel");
            final EngineDiscoveryRequest request =
                    createRequest(DiscoverySelectors.selectClass(SupplierStreamTestClass.class));
            discovery.discoverTests(request, engine);

            final var classDescriptor = (ParamixelTestClassDescriptor)
                    engine.getChildren().iterator().next();
            assertThat(classDescriptor.getChildren())
                    .anySatisfy(d -> assertThat(d.getDisplayName()).isEqualTo("b"));
        }

        @Test
        @DisplayName("Should handle argument supplier returning null or throwing by discovering no arguments")
        public void shouldHandleNullOrThrowingArgumentSupplier() {
            assertArgumentCount(SupplierNullTestClass.class, 0);
            assertArgumentCount(SupplierThrowsTestClass.class, 0);
        }

        @Test
        @DisplayName("Should fail discovery when MethodValidator reports signature problems")
        public void shouldFailDiscoveryOnValidationFailure() {
            final EngineDiscoveryRequest request = createRequest(
                    DiscoverySelectors.selectClass(org.paramixel.engine.invalid.InvalidSignatureTestClass.class));
            assertThatThrownBy(() -> discovery.discoverTests(request, engineDescriptor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Validation failed");
        }

        @Test
        @DisplayName("UniqueIdSelector should ignore missing classes")
        public void uniqueIdSelectorShouldIgnoreMissingClasses() {
            final UniqueId missing = UniqueId.forEngine("paramixel")
                    .append("class", "com.example.DoesNotExist")
                    .append("method", "m");
            final EngineDiscoveryRequest request = createRequest(DiscoverySelectors.selectUniqueId(missing));

            discovery.discoverTests(request, engineDescriptor);

            assertThat(engineDescriptor.getChildren()).isEmpty();
        }

        @Test
        @DisplayName("extractClassNameFromUniqueId should return null when no class segment exists")
        public void extractClassNameFromUniqueIdReturnsNullWhenNoClassSegment() throws Exception {
            final java.lang.reflect.Method m =
                    ParamixelDiscovery.class.getDeclaredMethod("extractClassNameFromUniqueId", UniqueId.class);
            m.setAccessible(true);

            final UniqueId uniqueId = UniqueId.forEngine("paramixel").append("method", "m");
            assertThat((String) m.invoke(discovery, uniqueId)).isNull();
        }

        @Test
        @DisplayName("extractClassNameFromUniqueId should return null for null UniqueId")
        public void extractClassNameFromUniqueIdReturnsNullForNullUniqueId() throws Exception {
            final java.lang.reflect.Method m =
                    ParamixelDiscovery.class.getDeclaredMethod("extractClassNameFromUniqueId", UniqueId.class);
            m.setAccessible(true);
            assertThat((String) m.invoke(discovery, new Object[] {null})).isNull();
        }

        @Test
        @DisplayName("getOrderValue should return MAX when no @Order")
        public void getOrderValueReturnsMaxValueWhenNoOrder() throws Exception {
            final java.lang.reflect.Method m =
                    ParamixelDiscovery.class.getDeclaredMethod("getOrderValue", java.lang.reflect.Method.class);
            m.setAccessible(true);
            final java.lang.reflect.Method testMethod = SupplierSingleObjectTestClass.class.getDeclaredMethod(
                    "test", org.paramixel.api.ArgumentContext.class);
            assertThat((int) m.invoke(null, testMethod)).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("buildClassFilter should treat no-package names as empty package")
        public void buildClassFilterTreatsNoPackageNamesAsEmptyPackage() throws Exception {
            final EngineDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .filters(PackageNameFilter.excludePackageNames(""))
                    .build();
            final java.lang.reflect.Method m =
                    ParamixelDiscovery.class.getDeclaredMethod("buildClassFilter", EngineDiscoveryRequest.class);
            m.setAccessible(true);

            @SuppressWarnings("unchecked")
            final Predicate<String> predicate = (Predicate<String>) m.invoke(discovery, request);

            assertThat(predicate.test("NoPackageClass")).isFalse();
        }

        @Test
        @DisplayName("Directory scanning should handle empty package and missing classes")
        public void findClassesInDirectoryHandlesEmptyPackageAndMissingClasses() throws Exception {
            final Path root = Files.createTempDirectory("paramixel-discovery-");
            try {
                final Path pkg = Files.createDirectory(root.resolve("p"));
                Files.createFile(pkg.resolve("Nope.class"));

                final java.lang.reflect.Method m = ParamixelDiscovery.class.getDeclaredMethod(
                        "findClassesInDirectory", File.class, String.class, Set.class);
                m.setAccessible(true);

                final Set<Class<?>> classes = new HashSet<>();
                m.invoke(discovery, root.toFile(), "", classes);
                assertThat(classes).isEmpty();
            } finally {
                // best-effort cleanup
                try {
                    Files.walk(root)
                            .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                    // ignore
                                }
                            });
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }

        @Test
        @DisplayName("Classpath root scanning should return empty when URI is not a directory")
        public void findClassesInClasspathRootReturnsEmptyWhenNotDirectory() throws Exception {
            final Path file = Files.createTempFile("paramixel-root-", ".tmp");
            try {
                final java.lang.reflect.Method m =
                        ParamixelDiscovery.class.getDeclaredMethod("findClassesInClasspathRoot", URI.class);
                m.setAccessible(true);

                @SuppressWarnings("unchecked")
                final Set<Class<?>> classes = (Set<Class<?>>) m.invoke(discovery, file.toUri());
                assertThat(classes).isEmpty();
            } finally {
                Files.deleteIfExists(file);
            }
        }

        private void assertArgumentCount(final Class<?> clazz, final int expectedArguments) {
            final TestDescriptor engine = new ParamixelEngineDescriptor(UniqueId.forEngine("paramixel"), "Paramixel");
            final EngineDiscoveryRequest request = createRequest(DiscoverySelectors.selectClass(clazz));
            discovery.discoverTests(request, engine);

            final var classDescriptor = (ParamixelTestClassDescriptor)
                    engine.getChildren().iterator().next();
            assertThat(classDescriptor.getChildren()).hasSize(expectedArguments);
        }
    }

    @Paramixel.TestClass
    @Paramixel.DisplayName(" My Class ")
    static class DisplayNameTestClass {
        @Paramixel.Test
        @Paramixel.DisplayName("My Method")
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Disabled("because")
    static class DisabledTestClass {
        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class DisabledMethodTestClass {
        @Paramixel.Test
        public void enabled(final org.paramixel.api.ArgumentContext context) {}

        @Paramixel.Test
        @Paramixel.Disabled("skip")
        public void disabled(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierStreamTestClass {
        @Paramixel.ArgumentSupplier
        static java.util.stream.Stream<Object> args() {
            return java.util.stream.Stream.of("a", new NamedArg("b"));
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierCollectionTestClass {
        @Paramixel.ArgumentSupplier
        static java.util.Collection<Object> args() {
            return java.util.List.of("a", new NamedArg("b"));
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierIterableTestClass {
        @Paramixel.ArgumentSupplier
        static Iterable<Object> args() {
            return java.util.List.of("a", new NamedArg("b"));
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierIterableOnlyTestClass {
        @Paramixel.ArgumentSupplier
        static Iterable<Object> args() {
            final java.util.List<Object> list = java.util.List.of("a", new NamedArg("b"));
            return (Iterable<Object>) () -> list.iterator();
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierArrayTestClass {
        @Paramixel.ArgumentSupplier
        static Object[] args() {
            return new Object[] {"a", new NamedArg("b")};
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierSingleObjectTestClass {
        @Paramixel.ArgumentSupplier
        static Object args() {
            return new NamedArg("only");
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierNullTestClass {
        @Paramixel.ArgumentSupplier
        static Object args() {
            return null;
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class SupplierThrowsTestClass {
        @Paramixel.ArgumentSupplier
        static Object args() {
            throw new RuntimeException("boom");
        }

        @Paramixel.Test
        public void test(final org.paramixel.api.ArgumentContext context) {}
    }

    static final class Outer {
        @Paramixel.TestClass
        static class NestedTestClass {
            @Paramixel.Test
            public void test(final org.paramixel.api.ArgumentContext context) {}
        }
    }

    private static final class NamedArg implements org.paramixel.api.Named {
        private final String name;

        private NamedArg(final String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
