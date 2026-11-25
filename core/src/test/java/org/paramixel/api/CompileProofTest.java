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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Source-level compile proofs for the public DSL.
 *
 * <p>The canonical examples must compile under Java 17 with no class tokens, no unchecked casts, and no generic type
 * witnesses, and the deliberate misuse patterns must fail to compile. A compile-only proof is used rather than
 * reflection because the guarantees are about inference and overload resolution, not runtime behavior.
 */
class CompileProofTest {

    @TempDir
    Path tempDir;

    private String lastOutput = "";

    private boolean compiles(final String className, final String source) throws IOException {
        final var sourceFile = tempDir.resolve(className + ".java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        final var output = tempDir.resolve("out").resolve(className);
        Files.createDirectories(output);
        final var apiLocation =
                Task.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        final var classpath = apiLocation + File.pathSeparator + System.getProperty("java.class.path");
        final var windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        final var javac = Path.of(System.getProperty("java.home"), "bin", windows ? "javac.exe" : "javac");
        final var process = new ProcessBuilder(
                        javac.toString(),
                        "-classpath",
                        classpath,
                        "-d",
                        output.toString(),
                        "-proc:none",
                        "-Xlint:unchecked",
                        "-Werror",
                        sourceFile.toString())
                .redirectErrorStream(true)
                .start();
        final String compilerOutput;
        try (var stream = process.getInputStream()) {
            compilerOutput = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            final var finished = process.waitFor(120, TimeUnit.SECONDS);
            final var success = finished && process.exitValue() == 0;
            if (!success) {
                lastOutput = compilerOutput;
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void assertCompiles(final String className, final String body) throws IOException {
        assertThat(compiles(className, body))
                .as("expected %s to compile:%n%s", className, lastOutput)
                .isTrue();
    }

    private void assertDoesNotCompile(final String className, final String body) throws IOException {
        assertThat(compiles(className, body))
                .as("expected %s to fail compilation", className)
                .isFalse();
    }

    @Test
    @DisplayName("API-01: the canonical per-environment fixture compiles without class tokens on steps")
    void basicTestExampleCompiles() throws IOException {
        assertCompiles("BasicStyleTest", """
                import static org.paramixel.api.Tasks.parallel;
                import static org.paramixel.api.Tasks.sequential;

                import java.util.List;
                import org.paramixel.api.Context;
                import org.paramixel.api.Paramixel;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class BasicStyleTest {

                    record Environment(String name) {}

                    private static final String ENVIRONMENT = "environment";
                    private static final String SET_UP = "setUp";
                    private static final String TEAR_DOWN = "tearDown";

                    @Paramixel.Factory
                    public static Task factory() throws Throwable {
                        final var root = parallel(BasicStyleTest.class, "BasicStyleTest", Pool.named("environments"));
                        for (final var environment : List.of(new Environment("plain"), new Environment("tls"))) {
                            root.child(sequential(environment.name())
                                    .bind(ENVIRONMENT, environment)
                                    .before(SET_UP)
                                    .child(sequential("tests")
                                            .step("testHealthy")
                                            .step("testTextMetrics")
                                            .step("testOpenMetrics"))
                                    .after(TEAR_DOWN));
                        }
                        return root.build();
                    }

                    @Paramixel.Id(SET_UP)
                    public static void setUp(final Context context) throws Exception {
                        context.require(ENVIRONMENT, Environment.class).name();
                    }

                    @Paramixel.Id("testHealthy")
                    public static void testHealthy(final Context context) {}

                    @Paramixel.Id("testTextMetrics")
                    public static void testTextMetrics(final Context context) {}

                    @Paramixel.Id("testOpenMetrics")
                    public static void testOpenMetrics(final Context context) {}

                    @Paramixel.Id(TEAR_DOWN)
                    public static void tearDown(final Context context) {}
                }
                """);
    }

    @Test
    @DisplayName("API-02: the canonical compatibility matrix compiles")
    void kafkaExampleCompiles() throws IOException {
        assertCompiles("KafkaStyleTest", """
                import static org.paramixel.api.Tasks.parallel;
                import static org.paramixel.api.Tasks.sequential;

                import java.util.List;
                import org.paramixel.api.Context;
                import org.paramixel.api.Paramixel;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class KafkaStyleTest {

                    record Environment(String name) {}

                    private static final String ENVIRONMENT = "environment";
                    private static final String MESSAGE = "message";

                    @Paramixel.Factory
                    public static Task factory() throws Throwable {
                        final var root = parallel(KafkaStyleTest.class, "Kafka compatibility", Pool.named("environments"));
                        for (final var environment : List.of(new Environment("kafka-1"), new Environment("kafka-2"))) {
                            root.child(sequential(environment.name())
                                    .bind(ENVIRONMENT, environment)
                                    .before("setUp")
                                    .child(sequential("tests").step("produce").step("consume"))
                                    .after("tearDown"));
                        }
                        return root.build();
                    }

                    @Paramixel.Id("setUp")
                    public static void setUp(final Context context) throws Throwable {}

                    @Paramixel.Id("produce")
                    public static void produce(final Context context) throws Throwable {
                        context.put(MESSAGE, "payload");
                    }

                    @Paramixel.Id("consume")
                    public static void consume(final Context context) {
                        context.require(MESSAGE, String.class);
                    }

                    @Paramixel.Id("tearDown")
                    public static void tearDown(final Context context) throws Throwable {}
                }
                """);
    }

    @Test
    @DisplayName("API-03/06/07: nested subtrees, bodies with children, and heterogeneous matrices compile")
    void nestedAndHeterogeneousExamplesCompile() throws IOException {
        assertCompiles("NestedStyleTest", """
                import static org.paramixel.api.Tasks.parallel;
                import static org.paramixel.api.Tasks.sequential;

                import java.util.List;
                import org.paramixel.api.Context;
                import org.paramixel.api.Paramixel;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class NestedStyleTest {

                    interface Fixture {}

                    static final class One implements Fixture {}

                    static final class Two implements Fixture {}

                    private static final String FIXTURE = "fixture";
                    private static final String INDEX = "index";

                    public static Task definition() {
                        final var root = sequential(NestedStyleTest.class, "Integration suite")
                                .step("validateConfiguration");
                        for (final Fixture fixture : List.of(new One(), new Two())) {
                            root.child(sequential(fixture.getClass().getSimpleName())
                                    .bind(FIXTURE, fixture)
                                    .before("verifySetup")
                                    .step("verify")
                                    .after("verifyTeardown"));
                        }
                        root.child(sequential("Group").step("ping"));
                        final var matrix = parallel("Matrix", Pool.named("environments"));
                        for (final var name : List.of("a", "b")) {
                            matrix.child(sequential(name)
                                    .bind(INDEX, name)
                                    .child(sequential("Consume")
                                            .body("consume")
                                            .child(parallel("Record checks", Pool.named("checks"))
                                                    .step("verifyPayload")
                                                    .step("verifyHeaders"))));
                        }
                        return root.child(matrix).build();
                    }

                    @Paramixel.Id("validateConfiguration")
                    public static void validateConfiguration(final Context context) {}

                    @Paramixel.Id("verifySetup")
                    public static void verifySetup(final Context context) {}

                    @Paramixel.Id("verify")
                    public static void verify(final Context context) {}

                    @Paramixel.Id("verifyTeardown")
                    public static void verifyTeardown(final Context context) {}

                    @Paramixel.Id("ping")
                    public static void ping(final Context context) {}

                    @Paramixel.Id("consume")
                    public static void consume(final Context context) {
                        context.require(INDEX, String.class);
                    }

                    @Paramixel.Id("verifyPayload")
                    public static void verifyPayload(final Context context) {}

                    @Paramixel.Id("verifyHeaders")
                    public static void verifyHeaders(final Context context) {}
                }
                """);
    }

    @Test
    @DisplayName("API-04: independent() does not exist on a parallel builder")
    void independentOnParallelDoesNotCompile() throws IOException {
        assertDoesNotCompile("ParallelIndependent", """
                import static org.paramixel.api.Tasks.*;
                import org.paramixel.api.Pool;

                public final class ParallelIndependent {
                    public static void definition() {
                        parallel("x", Pool.named("p")).independent();
                    }
                }
                """);
    }

    @Test
    @DisplayName("API-05: a raw string pool identifier does not compile")
    void rawStringPoolDoesNotCompile() throws IOException {
        assertDoesNotCompile("RawStringPool", """
                import org.paramixel.api.Tasks;

                public final class RawStringPool {
                    public static void definition() {
                        Tasks.parallel("x", "pool");
                    }
                }
                """);
    }

    @Test
    @DisplayName("API-08: the typed authoring surface is not public")
    void typedAuthoringSurfaceIsNotPublic() throws IOException {
        assertDoesNotCompile("TypedInstance", """
                import static org.paramixel.api.Tasks.instance;

                public final class TypedInstance {

                    static final class Fixture { void run() {} }

                    public static void definition() {
                        instance("x", Fixture::new).step("run", Fixture::run);
                    }
                }
                """);

        assertDoesNotCompile("TypedStep", """
                import static org.paramixel.api.Tasks.sequential;

                public final class TypedStep {
                    public static void definition() {
                        sequential("x").step("run", () -> {});
                    }
                }
                """);
    }

    @Test
    @DisplayName("API-05: a pool is not an executor")
    void poolIsNotAnExecutor() throws IOException {
        assertDoesNotCompile("PoolExecutor", """
                import java.util.concurrent.ExecutorService;
                import org.paramixel.api.Pool;

                public final class PoolExecutor {
                    public static void definition() {
                        ExecutorService executor = Pool.named("p");
                    }
                }
                """);
    }
}
