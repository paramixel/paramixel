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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.paramixel.api.action.Action;
import org.paramixel.maven.plugin.fixtures.PassingMojoFixture;

@DisplayName("ParamixelMojo execute()")
class ParamixelMojoExecuteTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("skipTests")
    class SkipTests {

        @Test
        @DisplayName("execute returns early when skipTests is true")
        void executeReturnsEarlyWhenSkipTestsIsTrue() throws Exception {
            var mojo = newMojo(tempDir, null);
            setField(mojo, "skipTests", true);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("passing fixture")
    class PassingFixture {

        @Test
        @DisplayName("execute succeeds with passing fixture")
        void executeSucceedsWithPassingFixture() throws Exception {
            var mojo = newMojo(tempDir, "PassingMojoFixture");

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("failing fixture")
    class FailingFixture {

        @Test
        @DisplayName("execute throws MojoFailureException for failing fixture")
        void executeThrowsMojoFailureExceptionForFailingFixture() throws Exception {
            var mojo = newMojo(tempDir, "FailingMojoFixture");

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("skipping fixture")
    class SkippingFixture {

        @Test
        @DisplayName("execute succeeds when failureOnSkip is false")
        void executeSucceedsWhenFailureOnSkipIsFalse() throws Exception {
            var mojo = newMojo(tempDir, "SkippingMojoFixture");
            setField(mojo, "failureOnSkip", false);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Test
        @DisplayName("execute throws MojoFailureException when failureOnSkip is true")
        void executeThrowsMojoFailureExceptionWhenFailureOnSkipIsTrue() throws Exception {
            var mojo = newMojo(tempDir, "SkippingMojoFixture");
            setField(mojo, "failureOnSkip", true);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("failFast")
    class FailFast {

        @Test
        @DisplayName("execute throws MojoFailureException for failing fixture with failFast")
        void executeThrowsForFailingFixtureWithFailFast() throws Exception {
            var mojo = newMojo(tempDir, "FailingMojoFixture");
            setField(mojo, "failFast", true);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Test
        @DisplayName("execute succeeds for passing fixture with failFast")
        void executeSucceedsForPassingFixtureWithFailFast() throws Exception {
            var mojo = newMojo(tempDir, "PassingMojoFixture");
            setField(mojo, "failFast", true);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("no discovered actions")
    class NoDiscoveredActions {

        @Test
        @DisplayName("execute succeeds when failIfNoTests is false")
        void executeSucceedsWhenFailIfNoTestsIsFalse() throws Exception {
            var mojo = newMojo(tempDir, "NoSuchMojoFixtureShouldExist");
            setField(mojo, "failIfNoTests", false);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Test
        @DisplayName("execute throws MojoExecutionException when failIfNoTests is true")
        void executeThrowsMojoExecutionExceptionWhenFailIfNoTestsIsTrue() throws Exception {
            var mojo = newMojo(tempDir, "NoSuchMojoFixtureShouldExist");
            setField(mojo, "failIfNoTests", true);

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute)
                        .isInstanceOf(MojoExecutionException.class)
                        .hasMessage("No Paramixel tests found and failIfNoTests is true");
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("resolver exceptions")
    class ResolverExceptions {

        @Test
        @DisplayName("execute wraps resolver exceptions as MojoExecutionException")
        void executeWrapsResolverExceptionsAsMojoExecutionException() throws Exception {
            var mojo = newMojo(tempDir, "ThrowingFactoryMojoFixture");

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("project build error")
    class ProjectBuildError {

        @Test
        @DisplayName("execute throws MojoExecutionException when project build is null")
        void executeThrowsMojoExecutionExceptionWhenProjectBuildIsNull() throws Exception {
            var project = new MavenProject() {
                @Override
                public Build getBuild() {
                    return null;
                }
            };

            var mojo = new ParamixelMojo();
            setField(mojo, "project", project);

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessage("Project build information is not available");
        }
    }

    @Nested
    @DisplayName("classloader restoration ordering")
    class ClassloaderRestorationOrdering {

        @Test
        @DisplayName("context classloader is restored before URLClassLoader is closed on error path")
        void contextClassLoaderRestoredBeforeUrlClassLoaderIsClosedOnErrorPath() throws Exception {
            var original = Thread.currentThread().getContextClassLoader();
            var classLoaderAtClose = new AtomicReference<ClassLoader>();
            var project = newProject(tempDir);

            ParamixelMojo mojo = new ParamixelMojo() {
                @Override
                URLClassLoader buildTestClassLoader() throws MojoExecutionException {
                    var delegate = super.buildTestClassLoader();
                    return new URLClassLoader(delegate.getURLs(), delegate.getParent()) {
                        @Override
                        public void close() throws IOException {
                            classLoaderAtClose.set(Thread.currentThread().getContextClassLoader());
                            super.close();
                        }
                    };
                }
            };
            setField(mojo, "project", project);
            setField(mojo, "skipTests", false);
            setField(mojo, "matchClass", ".*ThrowingFactoryMojoFixture.*");

            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
                assertThat(classLoaderAtClose.get()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Test
        @DisplayName("context classloader is restored before URLClassLoader is closed on success path")
        void contextClassLoaderRestoredBeforeUrlClassLoaderIsClosedOnSuccessPath() throws Exception {
            var original = Thread.currentThread().getContextClassLoader();
            var classLoaderAtClose = new AtomicReference<ClassLoader>();
            var project = newProject(tempDir);

            ParamixelMojo mojo = new ParamixelMojo() {
                @Override
                URLClassLoader buildTestClassLoader() throws MojoExecutionException {
                    var delegate = super.buildTestClassLoader();
                    return new URLClassLoader(delegate.getURLs(), delegate.getParent()) {
                        @Override
                        public void close() throws IOException {
                            classLoaderAtClose.set(Thread.currentThread().getContextClassLoader());
                            super.close();
                        }
                    };
                }
            };
            setField(mojo, "project", project);
            setField(mojo, "skipTests", false);
            setField(mojo, "matchClass", ".*PassingMojoFixture.*");

            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
                assertThat(classLoaderAtClose.get()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("thread lifecycle check")
    class ThreadLifecycleCheck {

        @Test
        @DisplayName("no warning when no lingering threads after normal execution")
        void noWarningWhenNoLingeringThreadsAfterNormalExecution() throws Exception {
            var mojo = newMojo(tempDir, "PassingMojoFixture");

            var original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatCode(mojo::execute).doesNotThrowAnyException();
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }

        @Test
        @DisplayName("snapshotNonDaemonThreads returns non-null set")
        void snapshotNonDaemonThreadsReturnsNonNullSet() throws Exception {
            var mojo = new ParamixelMojo();
            var method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            var threads = (Set<Thread>) method.invoke(mojo);

            assertThat(threads).isNotNull();
        }

        @Test
        @DisplayName("strictThreadLifecycle fails build when lingering non-daemon thread detected")
        void strictThreadLifecycleFailsBuildWhenLingeringNonDaemonThreadDetected() throws Exception {
            var mojo = new ParamixelMojo();
            setField(mojo, "strictThreadLifecycle", true);

            var testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            var dummyThread = new Thread(
                    () -> {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    "test-lingering-thread");
            dummyThread.setDaemon(false);
            dummyThread.setContextClassLoader(testCl);
            try {
                dummyThread.start();

                var snapshotMethod = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                snapshotMethod.setAccessible(true);

                @SuppressWarnings("unchecked")
                var baseline = (Set<Thread>) snapshotMethod.invoke(mojo);
                baseline.remove(dummyThread);

                var warnMethod = ParamixelMojo.class.getDeclaredMethod("warnOrErrorLingeringThreads", Set.class);
                warnMethod.setAccessible(true);

                assertThatThrownBy(() -> warnMethod.invoke(mojo, baseline))
                        .hasCauseInstanceOf(MojoExecutionException.class);
            } finally {
                dummyThread.interrupt();
                dummyThread.join(5_000);
                testCl.close();
            }
        }

        @Test
        @DisplayName("warns when lingering threads detected with strictThreadLifecycle disabled")
        void warnsWhenLingeringThreadsDetectedWithStrictThreadLifecycleDisabled() throws Exception {
            var mojo = new ParamixelMojo();
            setField(mojo, "strictThreadLifecycle", false);

            var testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            var dummyThread = new Thread(
                    () -> {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    "test-warning-lingering-thread");
            dummyThread.setDaemon(false);
            dummyThread.setContextClassLoader(testCl);
            try {
                dummyThread.start();

                var snapshotMethod = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                snapshotMethod.setAccessible(true);

                @SuppressWarnings("unchecked")
                var baseline = (Set<Thread>) snapshotMethod.invoke(mojo);
                baseline.remove(dummyThread);

                var warnMethod = ParamixelMojo.class.getDeclaredMethod("warnOrErrorLingeringThreads", Set.class);
                warnMethod.setAccessible(true);

                assertThatCode(() -> warnMethod.invoke(mojo, baseline)).doesNotThrowAnyException();
            } finally {
                dummyThread.interrupt();
                dummyThread.join(5_000);
                testCl.close();
            }
        }

        @Test
        @DisplayName("snapshotNonDaemonThreads excludes daemon threads")
        void snapshotNonDaemonThreadsExcludesDaemonThreads() throws Exception {
            var mojo = new ParamixelMojo();
            var method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            var threads = (Set<Thread>) method.invoke(mojo);

            for (Thread t : threads) {
                assertThat(t.isDaemon()).isFalse();
            }
        }

        @Test
        @DisplayName("snapshotNonDaemonThreads excludes threads with system prefixes")
        void snapshotNonDaemonThreadsExcludesThreadsWithSystemPrefixes() throws Exception {
            var testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            var forkJoinThread = new Thread(
                    () -> {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    "ForkJoinPool-1");
            forkJoinThread.setDaemon(false);
            forkJoinThread.setContextClassLoader(testCl);

            var timerThread = new Thread(
                    () -> {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    "Timer-0");
            timerThread.setDaemon(false);
            timerThread.setContextClassLoader(testCl);

            try {
                forkJoinThread.start();
                timerThread.start();

                // Wait for threads to start
                Thread.sleep(100);

                var mojo = new ParamixelMojo();
                var method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                var threads = (Set<Thread>) method.invoke(mojo);

                var threadNames = threads.stream().map(Thread::getName).toList();
                assertThat(threadNames).doesNotContain("ForkJoinPool-1", "Timer-0");
            } finally {
                forkJoinThread.interrupt();
                timerThread.interrupt();
                forkJoinThread.join(5_000);
                timerThread.join(5_000);
                testCl.close();
            }
        }

        @Test
        @DisplayName("snapshotNonDaemonThreads captures threads beyond activeCount + 10")
        void snapshotNonDaemonThreadsCapturesThreadsBeyondBufferLimit() throws Exception {
            var testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            int threadCount = Thread.activeCount() + 15;
            var threads = new ArrayList<Thread>();
            try {
                for (int i = 0; i < threadCount; i++) {
                    var t = new Thread(
                            () -> {
                                try {
                                    Thread.sleep(30_000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            "overflow-test-thread-" + i);
                    t.setDaemon(false);
                    t.setContextClassLoader(testCl);
                    threads.add(t);
                    t.start();
                }

                // Wait for threads to start
                Thread.sleep(200);

                var mojo = new ParamixelMojo();
                var method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                var snapshot = (Set<Thread>) method.invoke(mojo);

                for (Thread t : threads) {
                    assertThat(snapshot).contains(t);
                }
            } finally {
                for (Thread t : threads) {
                    t.interrupt();
                }
                for (Thread t : threads) {
                    t.join(5_000);
                }
                testCl.close();
            }
        }
    }

    private ParamixelMojo newMojo(Path targetDir, String classMatch) throws Exception {
        var project = newProject(targetDir);
        var mojo = new ParamixelMojo();
        setField(mojo, "project", project);
        setField(mojo, "skipTests", false);
        if (classMatch != null) {
            setField(mojo, "matchClass", ".*" + classMatch + ".*");
        }
        return mojo;
    }

    private static MavenProject newProject(Path targetDir) throws Exception {
        var fixtureClasses = Paths.get(PassingMojoFixture.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        var coreClasses = Paths.get(
                Action.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        var build = new Build();
        build.setDirectory(targetDir.toString());
        build.setOutputDirectory(targetDir.resolve("classes").toString());
        build.setTestOutputDirectory(targetDir.resolve("test-classes").toString());

        var project = new MavenProject() {
            private final List<String> testClasspath = List.of(
                    fixtureClasses.toAbsolutePath().toString(),
                    coreClasses.toAbsolutePath().toString());

            @Override
            public List<String> getTestClasspathElements() {
                return testClasspath;
            }
        };
        project.setBuild(build);

        return project;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var clazz = target.getClass();
        Field field = null;
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }
}
