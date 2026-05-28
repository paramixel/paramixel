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
import java.util.List;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderFinder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** Extract classpath entries from the Tomcat/Catalina WebappClassLoaderBase. */
class TomcatWebappClassLoaderBaseHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private TomcatWebappClassLoaderBaseHandler() {}

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
                classLoaderClass, "org.apache.catalina.loader.WebappClassLoaderBase");
    }

    /**
     * Return true if this classloader delegates to its parent.
     *
     * @param classLoader
     *            the {@link ClassLoader}.
     * @return true if this classloader delegates to its parent.
     */
    private static boolean isParentFirst(final ClassLoader classLoader, final ReflectionUtils reflectionUtils) {
        final Object delegateObject = reflectionUtils.getFieldVal(false, classLoader, "delegate");
        if (delegateObject != null) {
            return (boolean) delegateObject;
        }
        // Assume parent-first delegation order
        return true;
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
        final boolean isParentFirst = isParentFirst(classLoader, classLoaderOrder.reflectionUtils);
        if (isParentFirst) {
            // Use parent-first delegation order
            classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        }
        if ("org.apache.tomee.catalina.TomEEWebappClassLoader"
                .equals(classLoader.getClass().getName())) {
            // TomEEWebappClassLoader has a lot of complex delegation rules, including classname-specific
            // delegation, which is not supported by the current ClassGraph model, so we just try to approximate
            // the delegation order with a fixed order.
            try {
                classLoaderOrder.delegateTo(
                        Class.forName("org.apache.openejb.OpenEJB").getClassLoader(), /* isParent = */ true, log);
            } catch (LinkageError | ClassNotFoundException e) {
                // Ignore
            }
        }
        classLoaderOrder.add(classLoader, log);
        if (!isParentFirst) {
            // Use parent-last delegation order
            classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
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
        // type StandardRoot (implements WebResourceRoot)
        final Object resources = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getResources");
        // type List<URL>
        final Object baseURLs = classpathOrder.reflectionUtils.invokeMethod(false, resources, "getBaseUrls");
        classpathOrder.addClasspathEntryObject(baseURLs, classLoader, scanSpec, log);
        // type List<List<WebResourceSet>>
        // members: preResources, mainResources, classResources, jarResources,
        // postResources
        @SuppressWarnings("unchecked")
        final List<List<?>> allResources =
                (List<List<?>>) classpathOrder.reflectionUtils.getFieldVal(false, resources, "allResources");
        if (allResources != null) {
            // type List<WebResourceSet>
            for (final List<?> webResourceSetList : allResources) {
                // type WebResourceSet
                // {DirResourceSet, FileResourceSet, JarResourceSet, JarWarResourceSet,
                // EmptyResourceSet}
                for (final Object webResourceSet : webResourceSetList) {
                    if (webResourceSet != null) {
                        // For DirResourceSet
                        final File file = (File)
                                classpathOrder.reflectionUtils.invokeMethod(false, webResourceSet, "getFileBase");
                        String base = file == null ? null : file.getPath();
                        if (base == null) {
                            // For FileResourceSet
                            base = (String)
                                    classpathOrder.reflectionUtils.invokeMethod(false, webResourceSet, "getBase");
                        }
                        if (base == null) {
                            // For JarResourceSet and JarWarResourceSet
                            // The absolute path to the WAR file on the file system in which the JAR is
                            // located
                            base = (String) classpathOrder.reflectionUtils.invokeMethod(
                                    false, webResourceSet, "getBaseUrlString");
                        }
                        if (base != null) {
                            // For JarWarResourceSet: the path within the WAR file where the JAR file is
                            // located
                            final String archivePath = (String)
                                    classpathOrder.reflectionUtils.getFieldVal(false, webResourceSet, "archivePath");
                            if (archivePath != null && !archivePath.isEmpty()) {
                                // If archivePath is non-null, this is a jar within a war
                                base += "!" + (archivePath.startsWith("/") ? archivePath : "/" + archivePath);
                            }
                            final String className = webResourceSet.getClass().getName();
                            final boolean isJar = className.equals(
                                            "java.org.apache.catalina.webresources.JarResourceSet")
                                    || className.equals("java.org.apache.catalina.webresources.JarWarResourceSet");
                            // The path within this WebResourceSet where resources will be served from,
                            // e.g. for a resource JAR, this would be "META-INF/resources"
                            final String internalPath = (String) classpathOrder.reflectionUtils.invokeMethod(
                                    false, webResourceSet, "getInternalPath");
                            if (internalPath != null && !internalPath.isEmpty() && !internalPath.equals("/")) {
                                classpathOrder.addClasspathEntryObject(
                                        base
                                                + (isJar ? "!" : "")
                                                + (internalPath.startsWith("/") ? internalPath : "/" + internalPath),
                                        classLoader,
                                        scanSpec,
                                        log);
                            } else {
                                classpathOrder.addClasspathEntryObject(base, classLoader, scanSpec, log);
                            }
                        }
                    }
                }
            }
        }
        // This may or may not duplicate the above
        final Object urls = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURLs");
        classpathOrder.addClasspathEntryObject(urls, classLoader, scanSpec, log);
    }
}
