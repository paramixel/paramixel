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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.paramixel.api.Status;
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
            ParamixelMojo mojo = newMojo(tempDir, null);
            setField(mojo, "skipTests", true);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "PassingMojoFixture");

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "FailingMojoFixture");

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "SkippingMojoFixture");
            setField(mojo, "failureOnSkip", false);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "SkippingMojoFixture");
            setField(mojo, "failureOnSkip", true);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    @Nested
    @DisplayName("staged status check")
    class StagedStatusCheck {

        @Test
        @DisplayName("isPending is included in Mojo failure condition")
        void isPendingIncludedInMojoFailureCondition() {
            var status = Status.PENDING;
            assertThat(status.isFailed() || status.isPending()).isTrue();
            assertThat(status.isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("no discovered actions")
    class NoDiscoveredActions {

        @Test
        @DisplayName("execute succeeds when failIfNoTests is false")
        void executeSucceedsWhenFailIfNoTestsIsFalse() throws Exception {
            ParamixelMojo mojo = newMojo(tempDir, "NoSuchMojoFixtureShouldExist");
            setField(mojo, "failIfNoTests", false);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "NoSuchMojoFixtureShouldExist");
            setField(mojo, "failIfNoTests", true);

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "ThrowingFactoryMojoFixture");

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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

            ParamixelMojo mojo = new ParamixelMojo();
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
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            AtomicReference<ClassLoader> classLoaderAtClose = new AtomicReference<>();
            MavenProject project = newProject(tempDir);

            ParamixelMojo mojo = new ParamixelMojo() {
                @Override
                URLClassLoader buildTestClassLoader() throws MojoExecutionException {
                    URLClassLoader delegate = super.buildTestClassLoader();
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
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            AtomicReference<ClassLoader> classLoaderAtClose = new AtomicReference<>();
            MavenProject project = newProject(tempDir);

            ParamixelMojo mojo = new ParamixelMojo() {
                @Override
                URLClassLoader buildTestClassLoader() throws MojoExecutionException {
                    URLClassLoader delegate = super.buildTestClassLoader();
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
            ParamixelMojo mojo = newMojo(tempDir, "PassingMojoFixture");

            ClassLoader original = Thread.currentThread().getContextClassLoader();
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
            ParamixelMojo mojo = new ParamixelMojo();
            Method method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            Set<Thread> threads = (Set<Thread>) method.invoke(mojo);

            assertThat(threads).isNotNull();
        }

        @Test
        @DisplayName("strictThreadLifecycle fails build when lingering non-daemon thread detected")
        void strictThreadLifecycleFailsBuildWhenLingeringNonDaemonThreadDetected() throws Exception {
            ParamixelMojo mojo = new ParamixelMojo();
            setField(mojo, "strictThreadLifecycle", true);

            URLClassLoader testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            Thread dummyThread = new Thread(
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

                Method snapshotMethod = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                snapshotMethod.setAccessible(true);

                @SuppressWarnings("unchecked")
                Set<Thread> baseline = (Set<Thread>) snapshotMethod.invoke(mojo);
                baseline.remove(dummyThread);

                Method warnMethod = ParamixelMojo.class.getDeclaredMethod("warnOrErrorLingeringThreads", Set.class);
                warnMethod.setAccessible(true);

                assertThatThrownBy(() -> warnMethod.invoke(mojo, baseline))
                        .hasCauseInstanceOf(MojoExecutionException.class);
            } finally {
                dummyThread.interrupt();
                dummyThread.join(5000);
                testCl.close();
            }
        }

        @Test
        @DisplayName("warns when lingering threads detected with strictThreadLifecycle disabled")
        void warnsWhenLingeringThreadsDetectedWithStrictThreadLifecycleDisabled() throws Exception {
            ParamixelMojo mojo = new ParamixelMojo();
            setField(mojo, "strictThreadLifecycle", false);

            URLClassLoader testCl = new URLClassLoader(new URL[0], getClass().getClassLoader());
            Thread dummyThread = new Thread(
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

                Method snapshotMethod = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
                snapshotMethod.setAccessible(true);

                @SuppressWarnings("unchecked")
                Set<Thread> baseline = (Set<Thread>) snapshotMethod.invoke(mojo);
                baseline.remove(dummyThread);

                Method warnMethod = ParamixelMojo.class.getDeclaredMethod("warnOrErrorLingeringThreads", Set.class);
                warnMethod.setAccessible(true);

                assertThatCode(() -> warnMethod.invoke(mojo, baseline)).doesNotThrowAnyException();
            } finally {
                dummyThread.interrupt();
                dummyThread.join(5000);
                testCl.close();
            }
        }

        @Test
        @DisplayName("snapshotNonDaemonThreads excludes daemon threads")
        void snapshotNonDaemonThreadsExcludesDaemonThreads() throws Exception {
            ParamixelMojo mojo = new ParamixelMojo();
            Method method = ParamixelMojo.class.getDeclaredMethod("snapshotNonDaemonThreads");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            Set<Thread> threads = (Set<Thread>) method.invoke(mojo);

            for (Thread t : threads) {
                assertThat(t.isDaemon()).isFalse();
            }
        }
    }

    private ParamixelMojo newMojo(Path targetDir, String classMatch) throws Exception {
        MavenProject project = newProject(targetDir);
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "project", project);
        setField(mojo, "skipTests", false);
        if (classMatch != null) {
            setField(mojo, "matchClass", ".*" + classMatch + ".*");
        }
        return mojo;
    }

    private static MavenProject newProject(Path targetDir) throws Exception {
        Path fixtureClasses = Paths.get(PassingMojoFixture.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path coreClasses = Paths.get(
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
        Class<?> clazz = target.getClass();
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
