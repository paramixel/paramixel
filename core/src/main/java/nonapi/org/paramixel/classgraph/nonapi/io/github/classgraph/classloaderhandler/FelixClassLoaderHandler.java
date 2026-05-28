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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classloaderhandler;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderFinder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * Custom Class Loader Handler for OSGi Felix ClassLoader.
 *
 * <p>
 * The handler adds the bundle jar and all assocaited Bundle-Claspath jars into the classpath to be scanned.
 *
 * @author elrufaie
 */
class FelixClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private FelixClassLoaderHandler() {}

    /**
     * Check whether this {@link ClassLoaderHandler} can handle a given {@link ClassLoader}.
     *
     * @param classLoaderClass
     *            the {@link ClassLoader} class or one of its superclasses.
     * @param log
     *            the log
     * @return true if this {@link ClassLoaderHandler} can handle the {@link ClassLoader}.
     */
    public static boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "org.apache.felix.framework.BundleWiringImpl$BundleClassLoaderJava5")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "org.apache.felix.framework.BundleWiringImpl$BundleClassLoader");
    }

    /**
     * Find the {@link ClassLoader} delegation order for a {@link ClassLoader}.
     *
     * @param classLoader
     *            the {@link ClassLoader} to find the order for.
     * @param classLoaderOrder
     *            a {@link ClassLoaderOrder} object to update.
     * @param log
     *            the log
     */
    public static void findClassLoaderOrder(
            final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder, final LogNode log) {
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        classLoaderOrder.add(classLoader, log);
    }

    /**
     * Get the content location.
     *
     * @param content
     *            the content object
     * @return the content location
     */
    private static File getContentLocation(final Object content, final ReflectionUtils reflectionUtils) {
        return (File) reflectionUtils.invokeMethod(false, content, "getFile");
    }

    /**
     * Adds the bundle.
     *
     * @param bundleWiring
     *            the bundle wiring
     * @param classLoader
     *            the classloader
     * @param classpathOrderOut
     *            the classpath order out
     * @param bundles
     *            the bundles
     * @param scanSpec
     *            the scan spec
     * @param log
     *            the log
     */
    private static void addBundle(
            final Object bundleWiring,
            final ClassLoader classLoader,
            final ClasspathOrder classpathOrderOut,
            final Set<Object> bundles,
            final ScanSpec scanSpec,
            final LogNode log) {
        // Track the bundles we've processed to prevent loops
        bundles.add(bundleWiring);

        // Get the revision for this wiring
        final Object revision = classpathOrderOut.reflectionUtils.invokeMethod(false, bundleWiring, "getRevision");
        // Get the contents
        final Object content = classpathOrderOut.reflectionUtils.invokeMethod(false, revision, "getContent");
        final File location = content != null ? getContentLocation(content, classpathOrderOut.reflectionUtils) : null;
        if (location != null) {
            // Add the bundle object
            classpathOrderOut.addClasspathEntry(location, classLoader, scanSpec, log);

            // And any embedded content
            final List<?> embeddedContent =
                    (List<?>) classpathOrderOut.reflectionUtils.invokeMethod(false, revision, "getContentPath");
            if (embeddedContent != null) {
                for (final Object embedded : embeddedContent) {
                    if (embedded != content) {
                        final File embeddedLocation = embedded != null
                                ? getContentLocation(embedded, classpathOrderOut.reflectionUtils)
                                : null;
                        if (embeddedLocation != null) {
                            classpathOrderOut.addClasspathEntry(embeddedLocation, classLoader, scanSpec, log);
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the classpath entries for the associated {@link ClassLoader}.
     *
     * @param classLoader
     *            the {@link ClassLoader} to find the classpath entries order for.
     * @param classpathOrder
     *            a {@link ClasspathOrder} object to update.
     * @param scanSpec
     *            the {@link ScanSpec}.
     * @param log
     *            the log.
     */
    public static void findClasspathOrder(
            final ClassLoader classLoader,
            final ClasspathOrder classpathOrder,
            final ScanSpec scanSpec,
            final LogNode log) {
        // Get the wiring for the ClassLoader's bundle
        final Set<Object> bundles = new HashSet<>();
        final Object bundleWiring = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "m_wiring");
        addBundle(bundleWiring, classLoader, classpathOrder, bundles, scanSpec, log);

        // Deal with any other bundles we might be wired to. TODO: Use the ScanSpec to narrow down the list of wires
        // that we follow.

        final List<?> requiredWires = (List<?>) classpathOrder.reflectionUtils.invokeMethod(
                false, bundleWiring, "getRequiredWires", String.class, null);
        if (requiredWires != null) {
            for (final Object wire : requiredWires) {
                final Object provider = classpathOrder.reflectionUtils.invokeMethod(false, wire, "getProviderWiring");
                if (!bundles.contains(provider)) {
                    addBundle(provider, classLoader, classpathOrder, bundles, scanSpec, log);
                }
            }
        }
    }
}
