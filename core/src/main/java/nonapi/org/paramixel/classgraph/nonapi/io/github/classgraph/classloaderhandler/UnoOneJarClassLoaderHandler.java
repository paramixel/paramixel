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

import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderFinder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** Extract classpath entries from the Uno-Jar's JarClassLoader and One-Jar's JarClassLoader. */
class UnoOneJarClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private UnoOneJarClassLoaderHandler() {}

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
                        classLoaderClass, "com.needhamsoftware.unojar.JarClassLoader")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "com.simontuffs.onejar.JarClassLoader");
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

        // For Uno-Jar:

        final String unoJarOneJarPath =
                (String) classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getOneJarPath");
        classpathOrder.addClasspathEntry(unoJarOneJarPath, classLoader, scanSpec, log);

        // If this property is defined, Uno-Jar jar path was specified on commandline. Otherwise, jar path
        // should be contained in java.class.path (which will be separately picked up by ClassGraph, as
        // long as classloaders/classpath are not overloaded and parent classloaders are not ignored).
        final String unoJarJarPath = System.getProperty("uno-jar.jar.path");
        classpathOrder.addClasspathEntry(unoJarJarPath, classLoader, scanSpec, log);

        // For One-Jar:

        // If this property is defined, One-Jar jar path was specified on commandline. Otherwise, jar path
        // should be contained in java.class.path (which will be separately picked up by ClassGraph, as
        // long as classloaders/classpath are not overloaded and parent classloaders are not ignored).
        final String oneJarJarPath = System.getProperty("one-jar.jar.path");
        classpathOrder.addClasspathEntry(oneJarJarPath, classLoader, scanSpec, log);

        // If this property is defined, additional classpath entries were specified in OneJar format
        // on the commandline, with '|' as a separator
        final String oneJarClassPath = System.getProperty("one-jar.class.path");
        if (oneJarClassPath != null) {
            classpathOrder.addClasspathEntryObject(oneJarClassPath.split("\\|"), classLoader, scanSpec, log);
        }

        // For both UnoJar and OneJar, "libs/" and "main/" will be automatically picked up as library roots
        // for nested jars, based on ClassLoaderHandlerRegistry.AUTOMATIC_LIB_DIR_PREFIXES.
        // ("main/" contains "main.jar".)
    }
}
