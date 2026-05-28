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

/**
 * This handler uses
 * {@link nonapi.nonapi.org.paramixel.classgraph.io.github.classgraph.classloaderhandler.ClassLoaderHandler.DelegationOrder#PARENT_LAST} to support
 * the <code>RestartClassLoader</code> of Spring Boot's devtools. <code>RestartClassLoader</code> provides parent
 * last loading for specified URLs (those are all that are supposed to be changed during development). Therefor the
 * handler for that class loader also has to delegate in <code>PARENT_LAST</code> order.
 */
class SpringBootRestartClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private SpringBootRestartClassLoaderHandler() {}

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
                classLoaderClass, "org.springframework.boot.devtools.restart.classloader.RestartClassLoader");
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
        // The Restart classloader is a parent-last classloader, so add the Restart classloader itself to the
        // classloader order first
        classLoaderOrder.add(classLoader, log);

        // Delegate to the parent of the RestartClassLoader
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
    }

    /**
     * Find the classpath entries for the associated {@link ClassLoader}.
     *
     * Spring Boot's RestartClassLoader sits in front of the parent class loader and watches a given set of
     * directories for changes. While those classes are reachable from the parent class loader directly, they should
     * always be loaded through direct access from the RestartClassLoader until it's completely turned of by means
     * of Spring Boot Developer tools.
     *
     * The RestartClassLoader shades only the project classes and additional directories that are configurable, so
     * itself needs access to parent, but last.
     *
     * See: <a href="https://github.com/classgraph/classgraph/issues/267">#267</a>,
     * <a href="https://github.com/classgraph/classgraph/issues/268">#268</a>
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
        // The Restart classloader doesn't itself store any URLs
    }
}
