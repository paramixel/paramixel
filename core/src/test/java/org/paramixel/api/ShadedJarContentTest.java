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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import nonapi.org.paramixel.ClasspathResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.selector.Selector;

@DisplayName("Embedded ClassGraph jar content")
@Tag("integration")
class ShadedJarContentTest {

    private static File findJar() {
        File coreDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        if (!new File(coreDir, "pom.xml").exists()) {
            coreDir = coreDir.getParentFile();
        }
        String version = Version.version();
        if (Version.UNKNOWN.equals(version)) {
            return null;
        }
        File jar = new File(coreDir, "target/core-" + version + ".jar");
        return jar.isFile() ? jar : null;
    }

    @Test
    @DisplayName("includes embedded ClassGraph source classes under internal/classgraph")
    void includesEmbeddedClassGraphClasses() throws Exception {
        File jar = findJar();
        Assumptions.assumeTrue(jar != null, "Jar not found — skipping (run verify phase to produce it)");

        try (JarFile jarFile = new JarFile(jar)) {
            boolean hasApi = false;
            boolean hasNonapi = false;
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("nonapi/org/paramixel/classgraph/io/github/classgraph/")
                        && name.endsWith(".class")) {
                    hasApi = true;
                }
                if (name.startsWith("nonapi/org/paramixel/classgraph/nonapi/io/github/classgraph/")
                        && name.endsWith(".class")) {
                    hasNonapi = true;
                }
            }
            assertThat(hasApi)
                    .as(
                            "jar must contain embedded ClassGraph API classes under nonapi/org/paramixel/classgraph/io/github/classgraph/")
                    .isTrue();
            assertThat(hasNonapi)
                    .as(
                            "jar must contain embedded ClassGraph nonapi classes under nonapi/org/paramixel/classgraph/nonapi/io/github/classgraph/")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("does not contain original unrelocated ClassGraph classes")
    void doesNotContainUnrelocatedClassGraphClasses() throws Exception {
        File jar = findJar();
        Assumptions.assumeTrue(jar != null, "Jar not found — skipping (run verify phase to produce it)");

        try (JarFile jarFile = new JarFile(jar)) {
            boolean hasOriginalApi = false;
            boolean hasOriginalNonapi = false;
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("io/github/classgraph/") && name.endsWith(".class")) {
                    hasOriginalApi = true;
                }
                if (name.startsWith("nonapi/io/github/classgraph/") && name.endsWith(".class")) {
                    hasOriginalNonapi = true;
                }
            }
            assertThat(hasOriginalApi)
                    .as("jar must not contain unrelocated io/github/classgraph/ classes")
                    .isFalse();
            assertThat(hasOriginalNonapi)
                    .as("jar must not contain unrelocated nonapi/io/github/classgraph/ classes")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("includes ClassGraph NOTICE file")
    void includesClassGraphNoticeFile() throws Exception {
        File jar = findJar();
        Assumptions.assumeTrue(jar != null, "Jar not found — skipping (run verify phase to produce it)");

        try (JarFile jarFile = new JarFile(jar)) {
            assertThat(jarFile.getJarEntry("META-INF/NOTICE"))
                    .as("META-INF/NOTICE must be present in the jar")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("ClasspathResolver discovers actions through embedded ClassGraph")
    void classpathResolverDiscoversActionsThroughEmbeddedClassGraph() throws Exception {
        File jar = findJar();
        Assumptions.assumeTrue(jar != null, "Jar not found — skipping (run verify phase to produce it)");

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader jarClassLoader =
                new URLClassLoader(new URL[] {jar.toURI().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(jarClassLoader);
            Selector selector = Selector.classRegex("ClasspathResolverSmokeFixture");
            var configuration = Configuration.defaultConfiguration();
            Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();
            assertThat(result)
                    .as("ClasspathResolver must discover actions through embedded ClassGraph")
                    .isPresent();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
