/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.Paramixel;

/**
 * Scans directories for @Paramixel.TestClass annotated classes.
 *
 * @author Douglas Hoard
 * @since 0.0.1
 */
public final class TestClassScanner {

    public TestClassScanner() {
        // INTENTIONALLY EMPTY
    }

    public List<Class<?>> scan(final @NonNull File directory, final @NonNull ClassLoader classLoader) {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(classLoader, "classLoader must not be null");

        if (!directory.exists() || !directory.isDirectory()) {
            return Collections.emptyList();
        }

        final Set<String> classNames = new HashSet<>();
        scanDirectoryRecursive(directory, directory.getPath(), classNames);

        final List<Class<?>> testClasses = new ArrayList<>();
        for (final String className : classNames) {
            try {
                final Class<?> clazz = classLoader.loadClass(className);
                if (clazz.isAnnotationPresent(Paramixel.TestClass.class)) {
                    testClasses.add(clazz);
                }
            } catch (final ClassNotFoundException e) {
                // Log warning via Gradle logger in production
            } catch (final NoClassDefFoundError e) {
                // Log warning via Gradle logger in production
            }
        }

        return testClasses;
    }

    private void scanDirectoryRecursive(
            final @NonNull File directory, final @NonNull String basePath, final @NonNull Set<String> classNames) {

        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (final File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, basePath, classNames);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                final String relativePath = file.getPath().substring(basePath.length());
                final String className =
                        relativePath.replace(File.separatorChar, '.').replace(".class", "");
                classNames.add(className.startsWith(".") ? className.substring(1) : className);
            }
        }
    }
}
