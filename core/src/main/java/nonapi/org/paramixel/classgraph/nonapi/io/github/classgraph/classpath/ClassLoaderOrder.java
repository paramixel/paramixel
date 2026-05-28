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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classloaderhandler.ClassLoaderHandlerRegistry;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.classloaderhandler.ClassLoaderHandlerRegistry.ClassLoaderHandlerRegistryEntry;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.LogNode;

/** A class to find all unique classloaders. */
public class ClassLoaderOrder {
    /** The {@link ClassLoader} order. */
    private final Map<ClassLoader, List<ClassLoaderHandlerRegistryEntry>> classLoaderOrder = new LinkedHashMap<>();

    public ReflectionUtils reflectionUtils;

    /**
     * The set of all {@link ClassLoader} instances that have been added to the order so far, so that classloaders
     * don't get added twice.
     */
    // Need to use IdentityHashMap for maps and sets here, because TomEE weirdly makes instances of
    // CxfContainerClassLoader equal to (via .equals()) the instance of TomEEWebappClassLoader that it
    // delegates to (#515)
    private final Set<ClassLoader> added = Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());

    /**
     * The set of all {@link ClassLoader} instances that have been delegated to so far, to prevent an infinite loop
     * in delegation.
     */
    private final Set<ClassLoader> delegatedTo = Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());

    /**
     * The set of all parent {@link ClassLoader} instances that have been delegated to so far, to enable
     * {@link ClassGraph#ignoreParentClassLoaders()}.
     */
    private final Set<ClassLoader> allParentClassLoaders =
            Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());

    // -------------------------------------------------------------------------------------------------------------

    public ClassLoaderOrder(final ReflectionUtils reflectionUtils) {
        this.reflectionUtils = reflectionUtils;
    }

    /**
     * Get the {@link ClassLoader} order.
     *
     * @return the {@link ClassLoader} order, as a pair: {@link ClassLoader},
     *         {@link ClassLoaderHandlerRegistryEntry}.
     */
    public List<Entry<ClassLoader, List<ClassLoaderHandlerRegistryEntry>>> getClassLoaderOrder() {
        return new ArrayList<>(classLoaderOrder.entrySet());
    }

    /**
     * Get the all parent classloaders.
     *
     * @return all parent classloaders
     */
    public Set<ClassLoader> getAllParentClassLoaders() {
        return allParentClassLoaders;
    }

    /** Get the ClassLoaderHandler(s) that can handle a given ClassLoader. */
    private static List<ClassLoaderHandlerRegistryEntry> getClassLoaderHandlerRegistryEntries(
            final ClassLoader classLoader, final LogNode log) {
        List<ClassLoaderHandlerRegistryEntry> ents = new ArrayList<>();
        boolean matched = false;
        for (final ClassLoaderHandlerRegistryEntry ent : ClassLoaderHandlerRegistry.CLASS_LOADER_HANDLERS) {
            if (ent.canHandle(classLoader.getClass(), log)) {
                // This ClassLoaderHandler can handle the ClassLoader class, or one of its superclasses
                ents.add(ent);
                matched = true;
            }
        }
        if (!matched) {
            ents.add(ClassLoaderHandlerRegistry.FALLBACK_HANDLER);
        }
        return ents;
    }

    /**
     * Add a {@link ClassLoader} to the ClassLoader order at the current position.
     *
     * @param classLoader
     *            the class loader
     * @param log
     *            the log
     */
    public void add(final ClassLoader classLoader, final LogNode log) {
        if (classLoader == null) {
            return;
        }
        if (added.add(classLoader)) {
            classLoaderOrder.put(classLoader, getClassLoaderHandlerRegistryEntries(classLoader, log));
        }
    }

    /**
     * Recursively delegate to another {@link ClassLoader}.
     *
     * @param classLoader
     *            the class loader
     * @param isParent
     *            true if this is a parent of another classloader
     * @param log
     *            the log
     */
    public void delegateTo(final ClassLoader classLoader, final boolean isParent, final LogNode log) {
        if (classLoader == null) {
            return;
        }
        // Check if this is a parent before checking if the classloader is already in the delegatedTo set,
        // so that if the classloader is a context classloader but also a parent, it still gets marked as
        // a parent classloader.
        if (isParent) {
            allParentClassLoaders.add(classLoader);
        }
        // Don't delegate to a classloader twice
        if (delegatedTo.add(classLoader)) {
            add(classLoader, log);
            // Recurse to get delegation order
            // (note: may be wrong if multiple ClassLoaderHandlers can handle this classloader)
            for (final ClassLoaderHandlerRegistryEntry entry : getClassLoaderHandlerRegistryEntries(
                    classLoader, /* Don't log twice -- also logged by add method above */ null)) {
                entry.findClassLoaderOrder(classLoader, this, log);
            }
        }
    }
}
