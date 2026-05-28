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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classpath;

import java.util.LinkedHashSet;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.scanspec.ScanSpec;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** A class to find the unique ordered classpath elements. */
public class ClassLoaderFinder {
    /** The context class loaders. */
    private final ClassLoader[] contextClassLoaders;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the context class loaders.
     *
     * @return The context classloader, and any other classloader that is not an ancestor of context classloader.
     */
    public ClassLoader[] getContextClassLoaders() {
        return contextClassLoaders;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Return true if the class is, extends, or implements a given named class or interface. */
    // TODO: make this a default method of the ClassLoaderHandler interface in ClassGraph 5.x
    public static boolean classIsOrExtendsOrImplements(Class<?> cls, String className) {
        if (cls == null) {
            return false;
        }
        if (cls.getName().equals(className)) {
            return true;
        }
        if (classIsOrExtendsOrImplements(cls.getSuperclass(), className)) {
            return true;
        }
        for (Class<?> iface : cls.getInterfaces()) {
            if (classIsOrExtendsOrImplements(iface, className)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * A class to find the unique ordered classpath elements.
     *
     * @param scanSpec
     *            The scan spec, or null if none available.
     * @param log
     *            The log.
     */
    ClassLoaderFinder(final ScanSpec scanSpec, final ReflectionUtils reflectionUtils, final LogNode log) {
        LinkedHashSet<ClassLoader> classLoadersUnique;
        LogNode classLoadersFoundLog;
        if (scanSpec.overrideClassLoaders == null) {
            // ClassLoaders were not overridden

            // There's some advice here about choosing the best or the right classloader, but it is not complete
            // (e.g. it doesn't cover parent delegation modes):
            // http://www.javaworld.com/article/2077344/core-java/find-a-way-out-of-the-classloader-maze.html?page=2

            // Get thread context classloader (this is the first classloader to try, since a context classloader
            // can be set as an override on a per-thread basis)
            classLoadersUnique = new LinkedHashSet<>();
            final ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
            if (threadClassLoader != null) {
                classLoadersUnique.add(threadClassLoader);
            }

            // Get classloader for this class, which will generally be the classloader of the class that
            // called ClassGraph (the classloader of the caller is used by Class.forName(className), when
            // no classloader is provided)
            final ClassLoader currClassClassLoader = getClass().getClassLoader();
            if (currClassClassLoader != null) {
                classLoadersUnique.add(currClassClassLoader);
            }

            // Get system classloader (this is a fallback if one of the above do not work)
            final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (systemClassLoader != null) {
                classLoadersUnique.add(systemClassLoader);
            }

            // There is one more classloader in JDK9+, the platform classloader (used for handling extensions),
            // see: http://openjdk.java.net/jeps/261#Class-loaders
            // The method call to get it is ClassLoader.getPlatformClassLoader()
            // However, since it's not possible to get URLs from this classloader, and it is the parent of
            // the application classloader returned by ClassLoader.getSystemClassLoader() (so is delegated to
            // by the application classloader), there is no point adding it here. Modules are scanned
            // directly anyway, so we don't need to get module path entries from the platform classloader.

            // Find classloaders for classes on callstack, in case any were missed
            try {
                final Class<?>[] callStack = new CallStackReader(reflectionUtils).getClassContext(log);
                for (int i = callStack.length - 1; i >= 0; --i) {
                    final ClassLoader callerClassLoader = callStack[i].getClassLoader();
                    if (callerClassLoader != null) {
                        classLoadersUnique.add(callerClassLoader);
                    }
                }
            } catch (final IllegalArgumentException e) {
                if (log != null) {
                    log.log("Could not get call stack", e);
                }
            }

            // Add any custom-added classloaders after system/context/module classloaders
            if (scanSpec.addedClassLoaders != null) {
                classLoadersUnique.addAll(scanSpec.addedClassLoaders);
            }
            classLoadersFoundLog = log == null ? null : log.log("Found ClassLoaders:");

        } else {
            // ClassLoaders were overridden
            classLoadersUnique = new LinkedHashSet<>(scanSpec.overrideClassLoaders);
            classLoadersFoundLog = log == null ? null : log.log("Override ClassLoaders:");
        }

        // Log all identified ClassLoaders
        if (classLoadersFoundLog != null) {
            for (final ClassLoader classLoader : classLoadersUnique) {
                classLoadersFoundLog.log(classLoader.getClass().getName());
            }
        }

        this.contextClassLoaders = classLoadersUnique.toArray(new ClassLoader[0]);
    }
}
