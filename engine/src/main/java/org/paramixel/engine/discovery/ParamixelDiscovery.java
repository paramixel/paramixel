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

import java.io.File;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.junit.platform.commons.util.ClassLoaderUtils;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.NestedClassSelector;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.EngineContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteArgumentsCollector;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.filter.TagFilter;
import org.paramixel.engine.filter.TagFilterFactory;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.validation.TestClassValidator;
import org.paramixel.engine.validation.ValidationFailure;

/**
 * Discovers Paramixel tests from a JUnit Platform discovery request.
 *
 * <p>This class parses JUnit Platform selectors and filters to identify Paramixel test classes
 * annotated with {@link Paramixel.TestClass}. It then validates annotated methods using
 * {@link MethodValidator} and builds a descriptor hierarchy rooted under the engine descriptor.
 *
 * <p><b>Selectors</b>
 * <ul>
 *   <li>{@link ClassSelector}: selects a concrete Java class</li>
 *   <li>{@link MethodSelector}: selects the declaring class of a method</li>
 *   <li>{@link PackageSelector}: scans classes in a package</li>
 *   <li>{@link ClasspathRootSelector}: scans classes beneath a classpath root</li>
 *   <li>{@link NestedClassSelector}: selects a nested class</li>
 *   <li>{@link UniqueIdSelector}: selects by unique id segments</li>
 * </ul>
 *
 * <p><b>Descriptor structure</b>
 * <pre>
 * engine:paramixel
 *   class:&lt;fqcn&gt;
 *     argument:&lt;index&gt;
 *       method:&lt;name&gt;
 * </pre>
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless. It uses a JVM logger for diagnostics and does not require external
 * synchronization.
 *
 */
public final class ParamixelDiscovery {

    /**
     * Logger for discovery events and validation warnings.
     */
    private static final Logger LOGGER = Logger.getLogger(ParamixelDiscovery.class.getName());

    /**
     * Unique ID segment for the engine root.
     */
    private static final String ENGINE_ID_SEGMENT = "paramixel";

    /**
     * Unique ID segment name for class descriptors.
     */
    private static final String CLASS_SEGMENT = "class";

    /**
     * Unique ID segment name for argument descriptors.
     */
    private static final String ARGUMENT_SEGMENT = "argument";

    /**
     * Unique ID segment name for method descriptors.
     */
    private static final String METHOD_SEGMENT = "method";

    /**
     * Engine context used during discovery-time argument supplier invocation.
     *
     * <p>Discovery occurs before engine execution, so full runtime configuration is not available.
     * This context is provided to satisfy {@link ArgumentsCollector#getEngineContext()}.
     */
    private static final EngineContext DISCOVERY_ENGINE_CONTEXT =
            new ConcreteEngineContext(ENGINE_ID_SEGMENT, new Properties(), 1);

    /**
     * Discovers test classes from the given discovery request and populates the engine descriptor.
     *
     * <p>This method:
     * <ol>
     *   <li>Extracts all selectors from the request</li>
     *   <li>Filters to only classes annotated with {@code @Paramixel.TestClass}</li>
     *   <li>Validates each class's annotated methods</li>
     *   <li>Creates test descriptors for valid classes</li>
     *   <li>Handles invalid classes by logging and skipping</li>
     * </ol>
     *
     * @param request the JUnit Platform discovery request
     * @param engineDescriptor the engine descriptor to populate
     */
    public void discoverTests(
            final @NonNull EngineDiscoveryRequest request, final @NonNull TestDescriptor engineDescriptor) {
        LOGGER.fine("Starting Paramixel test discovery");

        final Set<Class<?>> testClasses = discoverTestClasses(request);
        LOGGER.fine("Discovered " + testClasses.size() + " potential test classes");

        final TagFilter tagFilter = TagFilterFactory.fromConfigurationParameters(request.getConfigurationParameters());

        if (tagFilter.hasIncludePatterns()) {
            LOGGER.fine("Applying tag filter - include patterns configured");
        }

        testClasses.stream()
                .filter(tagFilter::matches)
                .sorted(Comparator.comparing((Class<?> clazz) -> getDisplayName(clazz, clazz.getName())))
                .forEach(testClass -> discoverTestClass(testClass, engineDescriptor));

        LOGGER.fine(
                "Discovery complete. Found " + engineDescriptor.getChildren().size() + " test classes");
    }

    /**
     * Discovers all test classes from the discovery request.
     *
     * <p>This method handles:
     * <ul>
     *   <li>ClassSelector - direct class selection</li>
     *   <li>MethodSelector - selects the class containing the method</li>
     *   <li>PackageSelector - all classes in a package</li>
     *   <li>ClasspathRootSelector - all classes in a classpath root</li>
     *   <li>NestedClassSelector - nested classes</li>
     * </ul>
     *
     * @param request the discovery request
     * @return set of discovered test classes
     */
    private Set<Class<?>> discoverTestClasses(final @NonNull EngineDiscoveryRequest request) {
        final Predicate<String> classFilter = buildClassFilter(request);
        final Set<Class<?>> testClasses = new LinkedHashSet<>();

        // Handle ClassSelector - direct class selection
        for (ClassSelector selector : request.getSelectorsByType(ClassSelector.class)) {
            final Class<?> clazz = selector.getJavaClass();
            if (isParamixelTestClass(clazz) && classFilter.test(clazz.getName())) {
                testClasses.add(clazz);
                LOGGER.fine("Added class from ClassSelector: " + clazz.getName());
            }
        }

        // Handle MethodSelector - select class containing the method
        for (MethodSelector selector : request.getSelectorsByType(MethodSelector.class)) {
            final Class<?> clazz = selector.getJavaClass();
            if (isParamixelTestClass(clazz) && classFilter.test(clazz.getName())) {
                testClasses.add(clazz);
                LOGGER.fine("Added class from MethodSelector: " + clazz.getName());
            }
        }

        // Handle PackageSelector - all classes in a package
        for (PackageSelector selector : request.getSelectorsByType(PackageSelector.class)) {
            final String packageName = selector.getPackageName();
            final Set<Class<?>> classesInPackage = findClassesInPackage(packageName);
            for (Class<?> clazz : classesInPackage) {
                if (isParamixelTestClass(clazz) && classFilter.test(clazz.getName())) {
                    testClasses.add(clazz);
                    LOGGER.fine("Added class from PackageSelector: " + clazz.getName());
                }
            }
        }

        // Handle ClasspathRootSelector - all classes in a classpath root
        for (ClasspathRootSelector selector : request.getSelectorsByType(ClasspathRootSelector.class)) {
            final URI classpathRoot = selector.getClasspathRoot();
            final Set<Class<?>> classesInRoot = findClassesInClasspathRoot(classpathRoot);
            for (Class<?> clazz : classesInRoot) {
                if (isParamixelTestClass(clazz) && classFilter.test(clazz.getName())) {
                    testClasses.add(clazz);
                    LOGGER.fine("Added class from ClasspathRootSelector: " + clazz.getName());
                }
            }
        }

        // Handle NestedClassSelector - nested classes
        for (NestedClassSelector selector : request.getSelectorsByType(NestedClassSelector.class)) {
            final Class<?> clazz = selector.getNestedClass();
            if (isParamixelTestClass(clazz) && classFilter.test(clazz.getName())) {
                testClasses.add(clazz);
                LOGGER.fine("Added class from NestedClassSelector: " + clazz.getName());
            }
        }

        // Handle UniqueIdSelector - select tests by unique ID
        for (UniqueIdSelector selector : request.getSelectorsByType(UniqueIdSelector.class)) {
            final UniqueId uniqueId = selector.getUniqueId();
            final String className = extractClassNameFromUniqueId(uniqueId);
            if (className != null && classFilter.test(className)) {
                try {
                    final ClassLoader classLoader = ClassLoaderUtils.getDefaultClassLoader();
                    final Class<?> clazz = classLoader.loadClass(className);
                    if (isParamixelTestClass(clazz)) {
                        testClasses.add(clazz);
                        LOGGER.fine("Added class from UniqueIdSelector: " + className);
                    }
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, "Could not load class from UniqueIdSelector: " + className);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading class from UniqueIdSelector: " + className, e);
                }
            }
        }

        return testClasses;
    }

    /**
     * Builds a predicate that applies all discovery filters from the request.
     *
     * @param request the discovery request
     * @return a predicate that tests if a class name passes all filters
     */
    private Predicate<String> buildClassFilter(final @NonNull EngineDiscoveryRequest request) {
        final List<Predicate<String>> filters = new ArrayList<>();

        // Class name filter
        final List<ClassNameFilter> classNameFilters = request.getFiltersByType(ClassNameFilter.class);
        if (!classNameFilters.isEmpty()) {
            filters.add(className -> {
                for (ClassNameFilter filter : classNameFilters) {
                    if (filter.apply(className).excluded()) {
                        return false;
                    }
                }
                return true;
            });
        }

        // Package name filter
        final List<PackageNameFilter> packageNameFilters = request.getFiltersByType(PackageNameFilter.class);
        if (!packageNameFilters.isEmpty()) {
            filters.add(className -> {
                String packageName = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : "";
                for (PackageNameFilter filter : packageNameFilters) {
                    if (filter.apply(packageName).excluded()) {
                        return false;
                    }
                }
                return true;
            });
        }

        if (filters.isEmpty()) {
            return className -> true;
        }

        return className -> {
            for (Predicate<String> filter : filters) {
                if (!filter.test(className)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Checks if a class is an Paramixel test class.
     *
     * @param clazz the class to check
     * @return true if the class has @Paramixel.TestClass annotation
     */
    private boolean isParamixelTestClass(final @NonNull Class<?> clazz) {
        return clazz.isAnnotationPresent(Paramixel.TestClass.class);
    }

    /**
     * Finds all classes in a given package.
     *
     * @param packageName the package name
     * @return set of classes in the package
     */
    private Set<Class<?>> findClassesInPackage(final @NonNull String packageName) {
        final Set<Class<?>> classes = new LinkedHashSet<>();
        try {
            final ClassLoader classLoader = ClassLoaderUtils.getDefaultClassLoader();
            final String packagePath = packageName.replace('.', '/');
            final URL packageUrl = classLoader.getResource(packagePath);

            if (packageUrl != null) {
                final File packageDir = new File(packageUrl.getFile());
                if (packageDir.exists() && packageDir.isDirectory()) {
                    findClassesInDirectory(packageDir, packageName, classes);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to scan package: " + packageName, e);
        }
        return classes;
    }

    /**
     * Recursively finds classes in a directory.
     *
     * @param directory the directory to scan
     * @param packageName the package name
     * @param classes the set to add found classes to
     */
    private void findClassesInDirectory(
            final @NonNull File directory, final @NonNull String packageName, final @NonNull Set<Class<?>> classes) {
        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (packageName.isEmpty()) {
                    findClassesInDirectory(file, file.getName(), classes);
                } else {
                    findClassesInDirectory(file, packageName + "." + file.getName(), classes);
                }
            } else if (file.getName().endsWith(".class")) {
                final String className = packageName + "."
                        + file.getName().substring(0, file.getName().length() - 6);
                try {
                    final ClassLoader classLoader = ClassLoaderUtils.getDefaultClassLoader();
                    final Class<?> clazz = classLoader.loadClass(className);
                    classes.add(clazz);
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, "Could not load class: " + className);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading class: " + className, e);
                }
            }
        }
    }

    /**
     * Finds all classes in a classpath root.
     *
     * @param classpathRoot the classpath root URI
     * @return set of classes in the classpath root
     */
    private Set<Class<?>> findClassesInClasspathRoot(final @NonNull URI classpathRoot) {
        final Set<Class<?>> classes = new LinkedHashSet<>();
        try {
            final File rootDir = new File(classpathRoot);
            if (rootDir.exists() && rootDir.isDirectory()) {
                findClassesInDirectory(rootDir, "", classes);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to scan classpath root: " + classpathRoot, e);
        }
        return classes;
    }

    /**
     * Discovers a single test class and adds its descriptor to the engine.
     *
     * <p>This method:
     * <ol>
     *   <li>Validates all annotated methods in the class</li>
     *   <li>Creates a ClassDescriptor</li>
     *   <li>Creates MethodDescriptors for each @Paramixel.Test</li>
     *   <li>Creates InvocationDescriptors based on argument supplier</li>
     * </ol>
     *
     * @param testClass the test class to discover
     * @param engineDescriptor the parent engine descriptor
     * @throws IllegalStateException if {@code testClass} fails validation
     */
    private void discoverTestClass(final @NonNull Class<?> testClass, final @NonNull TestDescriptor engineDescriptor) {
        if (isDisabled(testClass)) {
            final Paramixel.Disabled disabled = testClass.getAnnotation(Paramixel.Disabled.class);
            LOGGER.fine("Skipping disabled test class: " + testClass.getName()
                    + (disabled.value().isEmpty() ? "" : " - " + disabled.value()));
            return;
        }

        final List<ValidationFailure> validationFailures = TestClassValidator.validateTestClass(testClass);

        if (!validationFailures.isEmpty()) {
            LOGGER.warning("Test class " + testClass.getName() + " has validation failures:");
            for (ValidationFailure failure : validationFailures) {
                LOGGER.warning("  - " + failure.getMessage());
            }
            throw new IllegalStateException(
                    "Validation failed for test class " + testClass.getName() + "; see logs for details.");
        }

        final UniqueId classUniqueId = engineDescriptor.getUniqueId().append(CLASS_SEGMENT, testClass.getName());

        final String displayName = getDisplayName(testClass, testClass.getName());

        final ParamixelTestClassDescriptor classDescriptor =
                new ParamixelTestClassDescriptor(classUniqueId, testClass, displayName);

        engineDescriptor.addChild(classDescriptor);

        discoverTestMethods(testClass, classDescriptor);

        LOGGER.fine("Discovered test class: " + testClass.getName());
    }

    /**
     * Discovers all test methods in a test class.
     *
     * @param testClass the test class
     * @param classDescriptor the class descriptor to populate
     */
    private void discoverTestMethods(
            final @NonNull Class<?> testClass, final @NonNull ParamixelTestClassDescriptor classDescriptor) {
        final List<Method> testMethods = getFlattenedTestMethods(testClass);

        testMethods.sort(
                Comparator.comparingInt(ParamixelDiscovery::getOrderValue).thenComparing(Method::getName));

        if (testMethods.isEmpty()) {
            LOGGER.fine("No enabled test methods found in class: " + testClass.getName());
            return;
        }

        final SupplierArguments supplierArguments = getSupplierArguments(testClass);
        classDescriptor.setArgumentParallelism(supplierArguments.parallelism);
        final Object[] arguments = supplierArguments.arguments;

        for (int argumentIndex = 0; argumentIndex < arguments.length; argumentIndex++) {
            final Object argument = arguments[argumentIndex];
            final String argumentName;

            if (argument instanceof Named) {
                argumentName = ((Named) argument).getName();
            } else {
                argumentName = "argument:" + argumentIndex;
            }

            final UniqueId argumentUniqueId =
                    classDescriptor.getUniqueId().append(ARGUMENT_SEGMENT, String.valueOf(argumentIndex));

            final ParamixelTestArgumentDescriptor argumentDescriptor =
                    new ParamixelTestArgumentDescriptor(argumentUniqueId, argumentIndex, argument, argumentName);

            classDescriptor.addChild(argumentDescriptor);

            for (Method testMethod : testMethods) {
                final UniqueId methodUniqueId =
                        argumentDescriptor.getUniqueId().append(METHOD_SEGMENT, testMethod.getName());

                final String methodDisplayName = getDisplayName(testMethod, testMethod.getName());

                final ParamixelTestMethodDescriptor methodDescriptor =
                        new ParamixelTestMethodDescriptor(methodUniqueId, testMethod, methodDisplayName);

                argumentDescriptor.addChild(methodDescriptor);
            }
        }
    }

    private List<Method> getFlattenedTestMethods(final @NonNull Class<?> testClass) {
        final Map<String, Method> bySignature = new LinkedHashMap<>();

        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Paramixel.Test.class)) {
                    continue;
                }
                if (isDisabled(method)) {
                    continue;
                }

                final String signatureKey = signatureKey(method);

                // Most-specific (subclass) declaration wins.
                bySignature.putIfAbsent(signatureKey, method);
            }
        }

        return new ArrayList<>(bySignature.values());
    }

    private static String signatureKey(final @NonNull Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append(method.getName());
        builder.append('(');
        final Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Gets arguments from the argument supplier method, if present.
     *
     * @param testClass the test class
     * @return array of arguments, or single null element if no supplier
     */
    private SupplierArguments getSupplierArguments(final @NonNull Class<?> testClass) {
        Method selected = null;
        final List<Method> ignored = new ArrayList<>();

        for (Class<?> current = testClass;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Paramixel.ArgumentsCollector.class)) {
                    continue;
                }
                if (selected == null) {
                    selected = method;
                } else {
                    ignored.add(method);
                }
            }
        }

        if (selected == null) {
            return SupplierArguments.noSupplier();
        }

        if (!ignored.isEmpty()) {
            LOGGER.warning(
                    "Multiple @Paramixel.ArgumentsCollector methods found in hierarchy for " + testClass.getName()
                            + "; using " + selected.getDeclaringClass().getName() + "#" + selected.getName()
                            + " and ignoring " + ignored.size() + " others");
        }

        try {
            // Collector-driven: public static void arguments(ArgumentsCollector collector)
            if (selected.getParameterCount() == 1
                    && selected.getParameterTypes()[0].equals(ArgumentsCollector.class)
                    && selected.getReturnType().equals(void.class)) {
                final ConcreteArgumentsCollector collector = new ConcreteArgumentsCollector(DISCOVERY_ENGINE_CONTEXT);
                ParamixelReflectionInvoker.invokeStatic(selected, collector);
                return new SupplierArguments(collector.toArray(), collector.getParallelism());
            }

            // Return-based supplier: public static <ReturnType> arguments()
            if (selected.getParameterCount() == 0) {
                final Object result = ParamixelReflectionInvoker.invokeStatic(selected);
                if (result == null) {
                    return SupplierArguments.empty();
                }
                if (result instanceof Stream) {
                    return new SupplierArguments(((Stream<?>) result).toArray(), SupplierArguments.DEFAULT_PARALLELISM);
                }
                if (result instanceof Collection) {
                    return new SupplierArguments(
                            ((Collection<?>) result).toArray(), SupplierArguments.DEFAULT_PARALLELISM);
                }
                if (result instanceof Iterable) {
                    final List<Object> list = new ArrayList<>();
                    for (Object item : (Iterable<?>) result) {
                        list.add(item);
                    }
                    return new SupplierArguments(list.toArray(), SupplierArguments.DEFAULT_PARALLELISM);
                }
                if (result instanceof Object[]) {
                    return new SupplierArguments((Object[]) result, SupplierArguments.DEFAULT_PARALLELISM);
                }
                return new SupplierArguments(new Object[] {result}, SupplierArguments.DEFAULT_PARALLELISM);
            }

            LOGGER.warning("Invalid @Paramixel.ArgumentsCollector method signature: " + selected);
            return SupplierArguments.empty();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to invoke arguments collector: " + selected.getName(), t);
            return SupplierArguments.empty();
        }
    }

    /**
     * Holds supplier output and the resolved per-class parallelism.
     *
     * <p>This type is private because it is an internal transport between supplier invocation and
     * descriptor construction.
     */
    private static final class SupplierArguments {

        /**
         * Default parallelism used when no supplier overrides it.
         *
         * <p>The value is initialized to {@code max(1, availableProcessors)}.
         */
        private static final int DEFAULT_PARALLELISM =
                Math.max(1, Runtime.getRuntime().availableProcessors());

        /** Collected argument values in iteration order; never {@code null}. */
        private final Object[] arguments;

        /**
         * Resolved parallelism for the test class.
         *
         * <p>The value is always {@code >= 1}.
         */
        private final int parallelism;

        /**
         * Creates a supplier result container.
         *
         * @param arguments the collected arguments; never {@code null}
         * @param parallelism the resolved parallelism; must be {@code >= 1}
         */
        private SupplierArguments(final Object[] arguments, final int parallelism) {
            this.arguments = arguments;
            this.parallelism = parallelism;
        }

        /**
         * Returns an empty supplier result.
         *
         * <p>This result indicates that a supplier exists but does not provide any arguments.
         *
         * @return an empty supplier result; never {@code null}
         */
        private static SupplierArguments empty() {
            return new SupplierArguments(new Object[0], DEFAULT_PARALLELISM);
        }

        /**
         * Returns a result indicating that no argument supplier exists.
         *
         * <p>The engine uses this sentinel value to create a single argument bucket with
         * {@code null} so that non-parameterized test classes still execute.
         *
         * @return a sentinel supplier result; never {@code null}
         */
        private static SupplierArguments noSupplier() {
            return new SupplierArguments(new Object[] {null}, DEFAULT_PARALLELISM);
        }
    }

    /**
     * Gets the display name for a class or method, checking for @DisplayName annotation.
     *
     * @param annotated the annotated element (Class or Method)
     * @param defaultName the default name to use if no annotation or empty
     * @return the display name
     */
    private String getDisplayName(final @NonNull AnnotatedElement annotated, final @NonNull String defaultName) {
        final Paramixel.DisplayName displayNameAnnotation = annotated.getAnnotation(Paramixel.DisplayName.class);
        if (displayNameAnnotation != null) {
            final String name = displayNameAnnotation.value();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return defaultName;
    }

    /**
     * Checks if a class or method is disabled using @Disabled annotation.
     *
     * @param annotated the annotated element (Class or Method)
     * @return true if the element is disabled
     */
    private boolean isDisabled(final @NonNull AnnotatedElement annotated) {
        return annotated.isAnnotationPresent(Paramixel.Disabled.class);
    }

    /**
     * Extracts the class name from a unique ID.
     *
     * <p>The unique ID format is: {@code [:paramixel:]/class:[className]/argument:[argumentIndex]/method:[methodName]}
     *
     * @param uniqueId the unique ID to parse
     * @return the class name, or null if not found
     */
    private String extractClassNameFromUniqueId(final @NonNull UniqueId uniqueId) {
        if (uniqueId == null) {
            return null;
        }

        for (UniqueId.Segment segment : uniqueId.getSegments()) {
            if (CLASS_SEGMENT.equals(segment.getType())) {
                return segment.getValue();
            }
        }

        return null;
    }

    /**
     * Returns the effective ordering value for a test method.
     *
     * <p>This method reads {@link Paramixel.Order} and falls back to {@link Integer#MAX_VALUE}
     * when the annotation is absent.
     *
     * @param method the method to inspect; never {@code null}
     * @return the configured order value, or {@link Integer#MAX_VALUE} when unordered
     */
    private static int getOrderValue(final Method method) {
        final Paramixel.Order order = method.getAnnotation(Paramixel.Order.class);
        if (order == null) {
            return Integer.MAX_VALUE;
        }
        return order.value();
    }
}
