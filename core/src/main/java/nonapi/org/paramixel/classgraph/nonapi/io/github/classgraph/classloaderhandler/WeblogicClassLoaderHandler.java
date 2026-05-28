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

/** Extract classpath entries from the Weblogic ClassLoaders. */
class WeblogicClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private WeblogicClassLoaderHandler() {}

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
                        classLoaderClass, "weblogic.utils.classloaders.ChangeAwareClassLoader")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "weblogic.utils.classloaders.GenericClassLoader")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "weblogic.utils.classloaders.FilteringClassLoader")
                // TODO: The following two known classloader names have not been tested, and the fields/methods
                // may not match those of the above classloaders.
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "weblogic.servlet.jsp.JspClassLoader")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(
                        classLoaderClass, "weblogic.servlet.jsp.TagFileClassLoader");
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
        classpathOrder.addClasspathPathStr( //
                (String) classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getFinderClassPath"),
                classLoader,
                scanSpec,
                log);
        classpathOrder.addClasspathPathStr( //
                (String) classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getClassPath"),
                classLoader,
                scanSpec,
                log);
    }
}
