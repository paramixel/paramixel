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

package org.paramixel.engine.discovery;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.junit.platform.commons.util.ClassLoaderUtils;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.NestedClassSelector;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.paramixel.api.Paramixel;

/**
 * Scans classpath for Paramixel test classes.
 *
 * <p>This class handles all classpath scanning operations including:
 * <ul>
 *   <li>Direct class selection via ClassSelector</li>
 *   <li>Method-based selection via MethodSelector</li>
 *   <li>Package scanning via PackageSelector</li>
 *   <li>Classpath root scanning via ClasspathRootSelector</li>
 *   <li>Nested class selection via NestedClassSelector</li>
 *   <li>Unique ID-based selection via UniqueIdSelector</li>
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless. It uses a JVM logger for diagnostics.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class ClasspathScanner {

    /**
     * Logger for discovery events.
     */
    private static final Logger LOGGER = Logger.getLogger(ClasspathScanner.class.getName());

    /**
     * Creates a new scanner instance.
     */
    public ClasspathScanner() {
        // INTENTIONALLY EMPTY - stateless utility
    }

    /**
     * Discovers all Paramixel test classes from the discovery request.
     *
     * @param request the discovery request
     * @param classFilter predicate to filter class names
     * @return set of discovered test classes
     */
    public Set<Class<?>> discoverTestClasses(
            final @NonNull EngineDiscoveryRequest request, final @NonNull Predicate<String> classFilter) {
        final Set<Class<?>> testClasses = new java.util.LinkedHashSet<>();

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
            final String className = extractClassNameFromUniqueId(selector.getUniqueId());
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
    public Predicate<String> buildClassFilter(final @NonNull EngineDiscoveryRequest request) {
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
     * Checks if a class is a Paramixel test class.
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
        final Set<Class<?>> classes = new java.util.LinkedHashSet<>();
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
     * Finds all classes in a directory.
     *
     * @param directory the directory
     * @param packageName the package name
     * @param classes the set to add discovered classes to
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
                try {
                    final String className = packageName.isEmpty()
                            ? file.getName().substring(0, file.getName().length() - 6)
                            : packageName + "."
                                    + file.getName().substring(0, file.getName().length() - 6);
                    classes.add(ClassLoaderUtils.getDefaultClassLoader().loadClass(className));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to load class: " + file.getName(), e);
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
        final Set<Class<?>> classes = new java.util.LinkedHashSet<>();
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
     * Extracts class name from unique ID.
     *
     * @param uniqueId the unique ID
     * @return class name or null if not found
     */
    private String extractClassNameFromUniqueId(final org.junit.platform.engine.UniqueId uniqueId) {
        for (org.junit.platform.engine.UniqueId.Segment segment : uniqueId.getSegments()) {
            if ("class".equals(segment.getType())) {
                return segment.getValue();
            }
        }
        return null;
    }
}
