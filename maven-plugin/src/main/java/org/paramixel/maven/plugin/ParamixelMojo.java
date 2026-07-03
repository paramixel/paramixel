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

package org.paramixel.maven.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nonapi.org.paramixel.support.AnsiColor;
import nonapi.org.paramixel.support.Arguments;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.ConfigurationException;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

/**
 * Maven mojo bound to the {@code test} goal that discovers and executes Paramixel action trees
 * during the {@link LifecyclePhase#TEST} phase with {@link ResolutionScope#TEST} dependency resolution.
 *
 * <p>The mojo resolves action factories from the test classpath using the runner's internal classpath scanner,
 * executes them with a {@link Runner}, and fails the build when {@link Result#isFailed()}
 * is {@code true}. Configuration-based promotion rules ({@code failureOnSkip},
 * {@code failureOnAbort}) are applied by the runner result, so skipped and aborted
 * outcomes are promoted to failed when the corresponding flags are enabled.
 *
 * <p>The action tree is executed exactly once. The result is returned directly from the runner,
 * avoiding redundant re-execution.
 *
 * <p>During execution the thread context classloader is replaced with a test classloader derived
 * from the Maven project's test classpath; the original classloader is restored in a {@code finally}
 * block regardless of outcome.
 *
 * <p>Configuration property precedence, from lowest to highest: Paramixel defaults,
 * POM {@code <properties>} declarations, the {@code reportFile} parameter, and system properties.
 *
 * <p>This mojo is thread-safe and may run concurrently with other mojos in a parallel build.
 */
@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class ParamixelMojo extends AbstractMojo {

    /**
     * Constructs a mojo with default parameter values.
     *
     * <p>Maven injects {@code @Parameter} fields after construction and before {@link #execute()} is invoked.
     */
    public ParamixelMojo() {
        // Intentionally empty
    }

    /**
     * The Maven project being built; used to resolve test classpath elements for action discovery.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * When {@code true}, skip Paramixel test execution entirely.
     */
    @Parameter(property = "paramixel.skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * When {@code true}, fail the build when no action factories are discovered.
     */
    @Parameter(property = "paramixel.failIfNoTests", defaultValue = "false")
    private boolean failIfNoTests;

    /**
     * When {@code true}, SKIP results are treated as failures and cause the build to fail.
     */
    @Parameter(property = "paramixel.failureOnSkip", defaultValue = "false")
    private boolean failureOnSkip;

    /**
     * When {@code true}, ABORTED results are treated as failures and cause the build to fail.
     */
    @Parameter(property = "paramixel.failureOnAbort", defaultValue = "true")
    private boolean failureOnAbort;

    /**
     * When {@code true}, the scheduler skips remaining unscheduled root children after the first
     * failed or aborted action.
     */
    @Parameter(property = "paramixel.failFast", defaultValue = "false")
    private boolean failFast;

    /**
     * Path to the per-run summary report; when unset, no report file is generated.
     */
    @Parameter(property = "paramixel.report.file")
    private String reportFile;

    /**
     * Package-name regular expression for filtering discovered action factories.
     *
     * <p>When set, only classes whose package name matches the pattern are considered for discovery.
     */
    @Parameter(property = "paramixel.match.package.regex")
    private String matchPackage;

    /**
     * Fully qualified class-name regular expression for filtering discovered action factories.
     *
     * <p>When set, only classes whose fully qualified name matches the pattern are considered for discovery.
     */
    @Parameter(property = "paramixel.match.class.regex")
    private String matchClass;

    /**
     * Tag regular expression for filtering discovered action factories.
     *
     * <p>When set, only methods tagged with a matching {@code @Paramixel.Tag} value are considered for discovery.
     */
    @Parameter(property = "paramixel.match.tag.regex")
    private String matchTag;

    /**
     * Additional Paramixel configuration properties declared in the POM; override Paramixel defaults
     * but are themselves overridden by system properties.
     */
    @Parameter
    private List<Property> properties;

    /**
     * When {@code true}, fail the build when non-daemon threads are still running after Paramixel execution
     * that retain a reference to the test classloader.
     */
    @Parameter(property = "paramixel.strictThreadLifecycle", defaultValue = "false")
    private boolean strictThreadLifecycle;

    /**
     * Thread name prefixes for well-known JVM system threads that should be excluded
     * from the lingering-thread leak detector. Prefix-matched — a thread named
     * {@code "ForkJoinPool-1"} is excluded by the prefix {@code "ForkJoinPool"}.
     */
    private static final Set<String> SYSTEM_THREAD_PREFIXES = Set.of(
            "main",
            "Reference Handler",
            "Finalizer",
            "Signal Dispatcher",
            "Attach Listener",
            "Common-Cleaner",
            "notification-thread",
            "ForkJoinPool",
            "Timer",
            "process-reaper");

    /**
     * Discovers and executes Paramixel action trees from the test classpath.
     *
     * <p>Replaces the thread context classloader with a test classloader for the duration of execution,
     * restoring the original classloader in a {@code finally} block. After execution, checks for
     * non-daemon threads that retain a reference to the test classloader and warns or fails depending
     * on {@code strictThreadLifecycle}.
     *
     * <p>Fails the build when {@link Result#isFailed()} is {@code true}. Configuration-based
     * promotion rules ({@code failureOnSkip}, {@code failureOnAbort}) are applied by the runner result.
     *
     * @throws MojoExecutionException when configuration is invalid, action resolution fails,
     *     lingering threads are detected with {@code strictThreadLifecycle} enabled,
     *     or an unexpected error occurs during execution
     * @throws MojoFailureException when {@link Result#isFailed()} is {@code true}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            getLog().info("Paramixel tests are skipped");
            return;
        }

        Objects.requireNonNull(project, "MavenProject is null");

        final var originalClassLoader = Thread.currentThread().getContextClassLoader();
        final var preExecutionThreads = snapshotNonDaemonThreads();

        try (var testClassLoader = buildTestClassLoader()) {
            Thread.currentThread().setContextClassLoader(testClassLoader);
            try {
                final var configuration = buildConfiguration(testClassLoader);
                final var selector = buildSelector();

                var runner = Runner.builder()
                        .configuration(configuration)
                        .listener(Listener.defaultListener(configuration))
                        .build();

                var optionalResult = runner.run(selector);
                if (optionalResult.isEmpty()) {
                    if (failIfNoTests) {
                        throw new MojoExecutionException("No Paramixel tests found and failIfNoTests is true");
                    }

                    getLog().info("No Paramixel tests found");
                    return;
                }

                var result = optionalResult.orElseThrow();
                if (result.isFailed()) {
                    throw new MojoFailureException(AnsiColor.BOLD_RED_TEXT.format("TESTS FAILED"));
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
                warnOrErrorLingeringThreads(preExecutionThreads);
            }
        } catch (ConfigurationException e) {
            throw new MojoExecutionException("Failed to build Paramixel configuration: " + e.getMessage(), e);
        } catch (ResolverException e) {
            throw new MojoExecutionException("Failed to resolve Paramixel actions: " + e.getMessage(), e);
        } catch (MojoFailureException | MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run Paramixel tests", e);
        }
    }

    /**
     * Builds a {@link Selector} from POM parameters for action discovery filtering.
     *
     * <p>When no match parameters are configured, returns {@link Selector#all()}.
     *
     * @return the selector for action discovery
     */
    Selector buildSelector() {
        var selectors = new ArrayList<Selector>();
        if (matchPackage != null && !matchPackage.isBlank()) {
            selectors.add(Selector.packageRegex(matchPackage));
        }
        if (matchClass != null && !matchClass.isBlank()) {
            selectors.add(Selector.classRegex(matchClass));
        }
        if (matchTag != null && !matchTag.isBlank()) {
            selectors.add(Selector.tagRegex(matchTag));
        }
        return switch (selectors.size()) {
            case 0 -> Selector.all();
            case 1 -> selectors.get(0);
            default -> Selector.and(selectors);
        };
    }

    /**
     * Snapshots all live non-daemon, non-system threads at the current moment.
     *
     * <p>Uses {@link Thread#getAllStackTraces()} to guarantee a complete snapshot of every
     * live thread. Unlike {@link Thread#enumerate(Thread[])}, which silently drops threads
     * when the supplied buffer is too small, {@code getAllStackTraces()} returns all live
     * threads regardless of count.
     *
     * <p>Collecting stack traces is more expensive than enumeration, but the method is
     * called at most three times per build (pre-execution baseline, warning check,
     * and error check), so the cost is negligible compared to build and test
     * execution time.
     *
     * <p>If a {@link SecurityManager} is installed that denies
     * {@code RuntimePermission("getStackTrace")}, this method throws a
     * {@link SecurityException}. No security manager is installed by default in
     * modern JVM builds.
     *
     * @return a mutable set of live non-daemon threads excluding known JVM system threads
     */
    private Set<Thread> snapshotNonDaemonThreads() {
        var result = new HashSet<Thread>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isDaemon()
                    || t.getThreadGroup() == null
                    || "system".equals(t.getThreadGroup().getName())
                    || SYSTEM_THREAD_PREFIXES.stream().anyMatch(t.getName()::startsWith)) {
                continue;
            }
            result.add(t);
        }
        return result;
    }

    /**
     * Compares the current set of non-daemon threads against a pre-execution baseline
     * and warns or fails when new threads are detected that hold a reference to the test classloader.
     *
     * @param baseline the mutable set of non-daemon threads captured before action execution
     * @throws MojoExecutionException when {@code strictThreadLifecycle} is {@code true}
     *     and lingering threads are detected
     */
    private void warnOrErrorLingeringThreads(final Set<Thread> baseline) throws MojoExecutionException {
        var current = snapshotNonDaemonThreads();
        current.removeAll(baseline);

        var lingering = current.stream()
                .filter(t -> t.getContextClassLoader() instanceof URLClassLoader)
                .toList();

        if (!lingering.isEmpty()) {
            var message = "Non-daemon threads are still running after Paramixel execution; "
                    + "these threads may fail when the test classloader is closed:";
            if (strictThreadLifecycle) {
                throw new MojoExecutionException(message);
            }
            getLog().warn(message);
            for (Thread t : lingering) {
                getLog().warn("  - " + t.getName() + " [id=" + t.getId() + ", state=" + t.getState() + "]");
            }
        }
    }

    /**
     * Builds the configuration from default, POM, and system properties.
     *
     * @param classLoader the classloader for classpath resource loading
     * @return the merged configuration
     * @throws MojoExecutionException if configuration loading fails
     */
    private Configuration buildConfiguration(final ClassLoader classLoader) throws MojoExecutionException {
        var configMap = new LinkedHashMap<String, String>();

        // Start from default configuration (classpath + system properties + defaults)
        var defaultConfig = Configuration.defaultConfiguration(classLoader);
        defaultConfig.keySet().forEach(key -> defaultConfig.getString(key).ifPresent(v -> configMap.put(key, v)));

        // POM <properties> override defaults
        if (properties != null) {
            var seenKeys = new HashSet<String>();
            for (Property property : properties) {
                var key = property.getKey();
                var value = property.getValue();

                if (key == null) {
                    throw new MojoExecutionException("Paramixel property key is null");
                }

                if (key.isBlank()) {
                    throw new MojoExecutionException("Paramixel property key is blank");
                }

                if (value == null) {
                    throw new MojoExecutionException("Paramixel property '" + key + "' value is null");
                }

                if (value.isBlank()) {
                    throw new MojoExecutionException("Paramixel property '" + key + "' value is blank");
                }

                if (!seenKeys.add(key)) {
                    getLog().warn("Duplicate Paramixel property key '" + key + "' — later value overrides earlier");
                }

                configMap.put(key, value);
            }
        }

        // <configuration><reportFile> overrides POM properties
        putIfNotBlank(configMap, Configuration.REPORT_FILE, reportFile);

        // <configuration><failureOnSkip> overrides POM properties
        if (failureOnSkip) {
            configMap.put(Configuration.FAILURE_ON_SKIP, "true");
        }

        // <configuration><failureOnAbort> overrides POM properties
        configMap.put(Configuration.FAILURE_ON_ABORT, String.valueOf(failureOnAbort));

        // <configuration><failIfNoTests> overrides POM properties
        if (failIfNoTests) {
            configMap.put(Configuration.FAIL_IF_NO_TESTS, "true");
        }

        // <configuration><failFast> overrides POM properties
        if (failFast) {
            configMap.put(Configuration.FAIL_FAST, "true");
        }

        // System properties always win (matches JUnit Platform precedence).
        // Note: defaultConfiguration(classLoader) above already includes system
        // properties; this loop provides the authoritative point-in-time snapshot
        // that overrides any earlier values.
        var systemProps = System.getProperties();
        List<Map.Entry<Object, Object>> snapshot;
        synchronized (systemProps) {
            snapshot = systemProps.entrySet().stream().toList();
        }
        for (var entry : snapshot) {
            var k = String.valueOf(entry.getKey());
            configMap.put(k, String.valueOf(entry.getValue()));
        }

        return Configuration.of(configMap);
    }

    private static void putIfNotBlank(final Map<String, String> configuration, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            configuration.put(key, value);
        }
    }

    /**
     * Builds a {@link URLClassLoader} from the Maven project's test classpath elements.
     *
     * <p>The returned classloader uses the mojo's own classloader as the parent.
     * Blank and null classpath elements are silently skipped.
     *
     * @return a new test classloader backed by the resolved classpath URLs
     * @throws MojoExecutionException when the project build information is unavailable,
     *     test dependency resolution fails, or a classpath element cannot be converted to a URL
     */
    URLClassLoader buildTestClassLoader() throws MojoExecutionException {
        final var classpathUrls = buildTestClasspathUrls();
        return new URLClassLoader(classpathUrls.toArray(new URL[0]), getClass().getClassLoader());
    }

    private List<URL> buildTestClasspathUrls() throws MojoExecutionException {
        if (project.getBuild() == null) {
            throw new MojoExecutionException("Project build information is not available");
        }

        final List<String> classpathElements;
        try {
            classpathElements = project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(
                    "Failed to resolve Maven test classpath; run the mojo in a phase with test dependency resolution",
                    e);
        }

        final var classpathUrls = new LinkedHashSet<URL>();
        try {
            for (String element : classpathElements) {
                if (element == null || element.isBlank()) {
                    getLog().debug("Skipping blank Maven test classpath element");
                    continue;
                }
                classpathUrls.add(new File(element).toURI().toURL());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build test classpath URLs", e);
        }

        return new ArrayList<>(classpathUrls);
    }

    /**
     * A key-value Paramixel configuration property declared in the POM.
     *
     * <p>Instances are mutable; both {@code key} and {@code value} may be reassigned after construction.
     */
    public static class Property {

        private String key;

        private String value;

        /**
         * Constructs a property with null key and value; callers must set both before use.
         */
        public Property() {
            // Intentionally empty
        }

        /**
         * Returns the configuration property key.
         *
         * @return the property key, or {@code null} if not yet assigned
         */
        public String getKey() {
            return key;
        }

        /**
         * Assigns the configuration property key, rejecting blank and null values.
         *
         * @param key the configuration property key used to look up the Paramixel property; must not be blank
         * @throws NullPointerException if {@code key} is {@code null}
         * @throws IllegalArgumentException if {@code key} is blank
         */
        public void setKey(final String key) {
            Objects.requireNonNull(key, "key is null");

            Arguments.requireNonBlank(key, "key is blank");

            this.key = key;
        }

        /**
         * Returns the configuration property value.
         *
         * @return the property value, or {@code null} if not yet assigned
         */
        public String getValue() {
            return value;
        }

        /**
         * Assigns the configuration property value, rejecting null and blank values.
         *
         * @param value the configuration property value; must not be null or blank
         * @throws NullPointerException if {@code value} is {@code null}
         * @throws IllegalArgumentException if {@code value} is blank
         */
        public void setValue(final String value) {
            Objects.requireNonNull(value, "value is null");

            Arguments.requireNonBlank(value, "value is blank");

            this.value = value;
        }

        /**
         * Compares this property to another for equality based on both {@code key} and {@code value}.
         *
         * @param o the object to compare against
         * @return {@code true} if the other object is a {@code Property} with the same key and value
         */
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Property property)) {
                return false;
            }
            return Objects.equals(key, property.key) && Objects.equals(value, property.value);
        }

        /**
         * Returns a hash code derived from both {@code key} and {@code value}.
         *
         * @return the hash code for this property
         */
        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        /**
         * Returns a string representation of this property for debugging purposes.
         *
         * @return a string in the form {@code Property{key='...', value='...'}}
         */
        @Override
        public String toString() {
            return "Property{key='" + key + "', value='" + value + "'}";
        }
    }
}
