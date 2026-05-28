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

import java.util.SortedSet;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderFinder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * Handle the Plexus ClassWorlds ClassRealm ClassLoader.
 *
 * @author lukehutch
 */
class PlexusClassWorldsClassRealmClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private PlexusClassWorldsClassRealmClassLoaderHandler() {}

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
                classLoaderClass, "org.codehaus.plexus.classworlds.realm.ClassRealm");
    }

    /**
     * Checks if is this classloader uses a parent-first strategy.
     *
     * @param classRealmInstance
     *            the ClassRealm instance
     * @return true if classloader uses a parent-first strategy
     */
    private static boolean isParentFirstStrategy(
            final ClassLoader classRealmInstance, final ReflectionUtils reflectionUtils) {
        final Object strategy = reflectionUtils.getFieldVal(false, classRealmInstance, "strategy");
        if (strategy != null) {
            final String strategyClassName = strategy.getClass().getName();
            if (strategyClassName.equals("org.codehaus.plexus.classworlds.strategy.SelfFirstStrategy")
                    || strategyClassName.equals("org.codehaus.plexus.classworlds.strategy.OsgiBundleStrategy")) {
                // Strategy is self-first
                return false;
            }
        }
        // Strategy is org.codehaus.plexus.classworlds.strategy.ParentFirstStrategy (or failed to find strategy)
        return true;
    }

    /**
     * Find the {@link ClassLoader} delegation order for a {@link ClassLoader}.
     *
     * @param classRealm
     *            the {@link ClassLoader} to find the order for.
     * @param classLoaderOrder
     *            a {@link ClassLoaderOrder} object to update.
     * @param log
     *            the log
     */
    public static void findClassLoaderOrder(
            final ClassLoader classRealm, final ClassLoaderOrder classLoaderOrder, final LogNode log) {
        // From ClassRealm#loadClassFromImport(String) -> getImportClassLoader(String)
        final Object foreignImports = classLoaderOrder.reflectionUtils.getFieldVal(false, classRealm, "foreignImports");
        if (foreignImports != null) {
            @SuppressWarnings("unchecked")
            final SortedSet<Object> foreignImportEntries = (SortedSet<Object>) foreignImports;
            for (final Object entry : foreignImportEntries) {
                final ClassLoader foreignImportClassLoader =
                        (ClassLoader) classLoaderOrder.reflectionUtils.invokeMethod(false, entry, "getClassLoader");
                // Treat foreign import classloader as if it is a parent classloader
                classLoaderOrder.delegateTo(foreignImportClassLoader, /* isParent = */ true, log);
            }
        }

        // Get delegation order -- different strategies have different delegation orders
        final boolean isParentFirst = isParentFirstStrategy(classRealm, classLoaderOrder.reflectionUtils);

        // From ClassRealm#loadClassFromSelf(String) -> findLoadedClass(String) for self-first strategy
        if (!isParentFirst) {
            // Add self before parent
            classLoaderOrder.add(classRealm, log);
        }

        // From ClassRealm#loadClassFromParent -- N.B. we are ignoring parentImports, which is used to filter
        // a class name before deciding whether or not to call the parent classloader (so ClassGraph will be
        // able to load classes by name that are not imported from the parent classloader).
        final ClassLoader parentClassLoader =
                (ClassLoader) classLoaderOrder.reflectionUtils.invokeMethod(false, classRealm, "getParentClassLoader");
        classLoaderOrder.delegateTo(parentClassLoader, /* isParent = */ true, log);
        classLoaderOrder.delegateTo(classRealm.getParent(), /* isParent = */ true, log);

        // From ClassRealm#loadClassFromSelf(String) -> findLoadedClass(String) for parent-first strategy
        if (isParentFirst) {
            // Add self after parent
            classLoaderOrder.add(classRealm, log);
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
        // ClassRealm extends URLClassLoader
        URLClassLoaderHandler.findClasspathOrder(classLoader, classpathOrder, scanSpec, log);
    }
}
