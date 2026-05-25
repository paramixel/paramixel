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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.internal.ClasspathResolver;
import org.paramixel.api.selector.Selector;

@DisplayName("Shaded jar content")
@Tag("integration")
class ShadedJarContentTest {

    private static File findShadedJar() {
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
    @DisplayName("includes ClassGraph license file in shaded jar")
    void includesClassGraphLicenseFile() throws Exception {
        File jar = findShadedJar();
        Assumptions.assumeTrue(jar != null, "Shaded jar not found — skipping (run verify phase to produce it)");

        try (JarFile jarFile = new JarFile(jar)) {
            assertThat(jarFile.getJarEntry("LICENSE-ClassGraph.txt"))
                    .as("LICENSE-ClassGraph.txt must be present in the shaded jar")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("contains relocated ClassGraph classes under org.paramixel.shade")
    void containsRelocatedClassGraphClasses() throws Exception {
        File jar = findShadedJar();
        Assumptions.assumeTrue(jar != null, "Shaded jar not found — skipping (run verify phase to produce it)");

        try (JarFile jarFile = new JarFile(jar)) {
            boolean hasRelocatedApi = false;
            boolean hasRelocatedNonapi = false;
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("org/paramixel/shade/io/github/classgraph/") && name.endsWith(".class")) {
                    hasRelocatedApi = true;
                }
                if (name.startsWith("org/paramixel/shade/nonapi/io/github/classgraph/") && name.endsWith(".class")) {
                    hasRelocatedNonapi = true;
                }
            }
            assertThat(hasRelocatedApi)
                    .as("shaded jar must contain relocated API classes under org/paramixel/shade/io/github/classgraph/")
                    .isTrue();
            assertThat(hasRelocatedNonapi)
                    .as(
                            "shaded jar must contain relocated nonapi classes under org/paramixel/shade/nonapi/io/github/classgraph/")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("does not contain original unrelocated ClassGraph classes")
    void doesNotContainUnrelocatedClassGraphClasses() throws Exception {
        File jar = findShadedJar();
        Assumptions.assumeTrue(jar != null, "Shaded jar not found — skipping (run verify phase to produce it)");

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
                    .as("shaded jar must not contain unrelocated io/github/classgraph/ classes")
                    .isFalse();
            assertThat(hasOriginalNonapi)
                    .as("shaded jar must not contain unrelocated nonapi/io/github/classgraph/ classes")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("ClasspathResolver discovers actions through shaded ClassGraph")
    void classpathResolverDiscoversActionsThroughShadedClassGraph() throws Exception {
        File jar = findShadedJar();
        Assumptions.assumeTrue(jar != null, "Shaded jar not found — skipping (run verify phase to produce it)");

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader shadedClassLoader =
                new URLClassLoader(new URL[] {jar.toURI().toURL()}, original)) {
            Thread.currentThread().setContextClassLoader(shadedClassLoader);
            Selector selector = Selector.classRegex("ClasspathResolverSmokeFixture");
            var configuration = Configuration.defaultConfiguration();
            Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();
            assertThat(result)
                    .as("ClasspathResolver must discover actions through shaded ClassGraph")
                    .isPresent();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
