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

package nonapi.org.paramixel.classgraph.io.github.classgraph;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.reflection.ReflectionUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.JarUtils;
import nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils.StringUtils;

/**
 * Information on the module path. Note that this will only include module system parameters actually listed in
 * commandline arguments -- in particular this does not include classpath elements from the traditional classpath,
 * or system modules.
 */
public class ModulePathInfo {
    /**
     * The module path provided on the commandline by the {@code --module-path} or {@code -p} switch, as an ordered
     * set of module names, in the order they were listed on the commandline.
     *
     * <p>
     * Note that some modules (such as system modules) will not be in this set, as they are added to the module
     * system automatically by the runtime. Call {@link ClassGraph#getModules()} or {@link ScanResult#getModules()}
     * to get all modules visible at runtime.
     */
    public final Set<String> modulePath = new LinkedHashSet<>();

    /**
     * The modules added to the module path on the commandline using the {@code --add-modules} switch, as an ordered
     * set of module names, in the order they were listed on the commandline. Note that valid module names include
     * {@code ALL-DEFAULT}, {@code ALL-SYSTEM}, and {@code ALL-MODULE-PATH} (see
     * <a href="https://openjdk.java.net/jeps/261">JEP 261</a> for info).
     */
    public final Set<String> addModules = new LinkedHashSet<>();

    /**
     * The module patch directives listed on the commandline using the {@code --patch-module} switch, as an ordered
     * set of strings in the format {@code <module>=<file>}, in the order they were listed on the commandline.
     */
    public final Set<String> patchModules = new LinkedHashSet<>();

    /**
     * The module {@code exports} directives added on the commandline using the {@code --add-exports} switch, as an
     * ordered set of strings in the format {@code <source-module>/<package>=<target-module>(,<target-module>)*}, in
     * the order they were listed on the commandline. Additionally, if this {@link ModulePathInfo} object was
     * obtained from {@link ScanResult#getModulePathInfo()} rather than {@link ClassGraph#getModulePathInfo()}, any
     * additional {@code Add-Exports} entries found in manifest files during classpath scanning will be appended to
     * this list, in the format {@code <source-module>/<package>=ALL-UNNAMED}.
     */
    public final Set<String> addExports = new LinkedHashSet<>();

    /**
     * The module {@code opens} directives added on the commandline using the {@code --add-opens} switch, as an
     * ordered set of strings in the format {@code <source-module>/<package>=<target-module>(,<target-module>)*}, in
     * the order they were listed on the commandline. Additionally, if this {@link ModulePathInfo} object was
     * obtained from {@link ScanResult#getModulePathInfo()} rather than {@link ClassGraph#getModulePathInfo()}, any
     * additional {@code Add-Opens} entries found in manifest files during classpath scanning will be appended to
     * this list, in the format {@code <source-module>/<package>=ALL-UNNAMED}.
     */
    public final Set<String> addOpens = new LinkedHashSet<>();

    /**
     * The module {@code reads} directives added on the commandline using the {@code --add-reads} switch, as an
     * ordered set of strings in the format {@code <source-module>=<target-module>}, in the order they were listed
     * on the commandline.
     */
    public final Set<String> addReads = new LinkedHashSet<>();

    /** The fields. */
    private final List<Set<String>> fields = Arrays.asList( //
            modulePath, //
            addModules, //
            patchModules, //
            addExports, //
            addOpens, //
            addReads //
            );

    /** The module path commandline switches. */
    private static final List<String> argSwitches = Arrays.asList( //
            "--module-path=", //
            "--add-modules=", //
            "--patch-module=", //
            "--add-exports=", //
            "--add-opens=", //
            "--add-reads=" //
            );

    /** The module path commandline switch value delimiters. */
    private static final List<Character> argPartSeparatorChars = Arrays.asList( //
            File.pathSeparatorChar, // --module-path (delimited path format)
            ',', // --add-modules (comma-delimited)
            '\0', // --patch-module (only one param per switch)
            '\0', // --add-exports (only one param per switch)
            '\0', // --add-opens (only one param per switch)
            '\0' // --add-reads (only one param per switch)
            );

    /* Module path info. */
    public ModulePathInfo() {}

    /** Set to true once {@link #getRuntimeInfo()} is called. */
    private final AtomicBoolean gotRuntimeInfo = new AtomicBoolean();

    /** Fill in module info from VM commandline parameters. */
    void getRuntimeInfo(final ReflectionUtils reflectionUtils) {
        // Only call this reflective method if ModulePathInfo is specifically requested, to avoid illegal
        // access warning on some JREs, e.g. Adopt JDK 11 (#605)
        if (!gotRuntimeInfo.getAndSet(true)) {
            // Read the raw commandline arguments to get the module path override parameters.
            // If the java.management module is not present in the deployed runtime (for JDK 9+), or the runtime
            // does not contain the java.lang.management package (e.g. the Android build system, which also does
            // not support JPMS currently), then skip trying to read the commandline arguments (#404).
            final Class<?> managementFactory =
                    reflectionUtils.classForNameOrNull("java.lang.management.ManagementFactory");
            final Object runtimeMXBean = managementFactory == null
                    ? null
                    : reflectionUtils.invokeStaticMethod(
                            /* throwException = */ false, managementFactory, "getRuntimeMXBean");
            @SuppressWarnings("unchecked")
            final List<String> commandlineArguments = runtimeMXBean == null
                    ? null
                    : (List<String>) reflectionUtils.invokeMethod(
                            /* throwException = */ false, runtimeMXBean, "getInputArguments");
            if (commandlineArguments != null) {
                for (final String arg : commandlineArguments) {
                    for (int i = 0; i < fields.size(); i++) {
                        final String argSwitch = argSwitches.get(i);
                        if (arg.startsWith(argSwitch)) {
                            final String argParam = arg.substring(argSwitch.length());
                            final Set<String> argField = fields.get(i);
                            final char sepChar = argPartSeparatorChars.get(i);
                            if (sepChar == '\0') {
                                // Only one param per switch
                                argField.add(argParam);
                            } else {
                                // Split arg param into parts
                                argField.addAll(Arrays.asList(
                                        JarUtils.smartPathSplit(argParam, sepChar, /* scanSpec = */ null)));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Return the module path info in commandline format.
     *
     * @return the module path commandline string.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(1024);
        if (!modulePath.isEmpty()) {
            buf.append("--module-path=");
            buf.append(StringUtils.join(File.pathSeparator, modulePath));
        }
        if (!addModules.isEmpty()) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-modules=");
            buf.append(StringUtils.join(",", addModules));
        }
        for (final String patchModulesEntry : patchModules) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--patch-module=");
            buf.append(patchModulesEntry);
        }
        for (final String addExportsEntry : addExports) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-exports=");
            buf.append(addExportsEntry);
        }
        for (final String addOpensEntry : addOpens) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-opens=");
            buf.append(addOpensEntry);
        }
        for (final String addReadsEntry : addReads) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append("--add-reads=");
            buf.append(addReadsEntry);
        }
        return buf.toString();
    }
}
