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

import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClassLoaderOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath.ClasspathOrder;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/**
 * Fallback ClassLoaderHandler. Tries to get classpath from a range of possible method and field names.
 */
class FallbackClassLoaderHandler implements ClassLoaderHandler {
    /** Class cannot be constructed. */
    private FallbackClassLoaderHandler() {}

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
        // This is the fallback handler, it handles anything
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
        boolean valid = false;
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getClassPath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getClasspath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "classpath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "classPath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "cp"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "classpath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "classPath"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "cp"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getPath"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getPaths"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "path"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "paths"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "paths"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "paths"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getDir"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getDirs"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "dir"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "dirs"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "dir"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "dirs"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getFile"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getFiles"),
                classLoader,
                scanSpec,
                log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "file"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "files"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "file"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "files"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getJar"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getJars"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "jar"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "jars"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "jar"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "jars"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURL"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURLs"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getUrl"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getUrls"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "url"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "urls"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "url"), classLoader, scanSpec, log);
        valid |= classpathOrder.addClasspathEntryObject(
                classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "urls"), classLoader, scanSpec, log);
        if (log != null) {
            log.log("FallbackClassLoaderHandler " + (valid ? "found" : "did not find")
                    + " classpath entries in unknown ClassLoader " + classLoader);
        }
    }
}
