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
        final var classpath = apiLocation + java.io.File.pathSeparator + System.getProperty("java.class.path");
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
    @DisplayName("API-01: the canonical per-environment fixture compiles without class tokens")
    void basicTestExampleCompiles() throws IOException {
        assertCompiles("BasicStyleTest", """
                import static org.paramixel.api.Tasks.*;

                import java.util.List;
                import org.paramixel.api.Paramixel;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class BasicStyleTest {

                    record Environment(String name) {}

                    private final Environment environment;

                    private BasicStyleTest(Environment environment) {
                        this.environment = environment;
                    }

                    @Paramixel.Factory
                    public static Task factory() throws Throwable {
                        return parallel("BasicStyleTest", Pool.named("environments"))
                                .each(
                                        List.of(new Environment("plain"), new Environment("tls")),
                                        environment -> forInstance(environment.name(), () -> new BasicStyleTest(environment))
                                                .before(BasicStyleTest::setUp)
                                                .sequential("tests", tests -> tests
                                                        .step("testHealthy()", BasicStyleTest::testHealthy)
                                                        .step("testDefaultTextMetrics()", BasicStyleTest::testDefaultTextMetrics)
                                                        .step("testOpenMetricsTextMetrics()", BasicStyleTest::testOpenMetricsTextMetrics)
                                                        .step("testPrometheusTextMetrics()", BasicStyleTest::testPrometheusTextMetrics)
                                                        .step("testPrometheusProtobufMetrics()", BasicStyleTest::testPrometheusProtobufMetrics))
                                                .after(BasicStyleTest::tearDown))
                                .build();
                    }

                    private void setUp() throws Exception {
                        environment.name();
                    }

                    private void testHealthy() {}

                    private void testDefaultTextMetrics() {}

                    private void testOpenMetricsTextMetrics() {}

                    private void testPrometheusTextMetrics() {}

                    private void testPrometheusProtobufMetrics() {}

                    private void tearDown() {}
                }
                """);
    }

    @Test
    @DisplayName("API-02: the canonical compatibility matrix compiles")
    void kafkaExampleCompiles() throws IOException {
        assertCompiles("KafkaStyleTest", """
                import static org.paramixel.api.Tasks.*;

                import java.util.List;
                import org.paramixel.api.Paramixel;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class KafkaStyleTest {

                    record Environment(String name) {}

                    private final Environment environment;

                    private KafkaStyleTest(Environment environment) {
                        this.environment = environment;
                    }

                    @Paramixel.Factory
                    public static Task factory() throws Throwable {
                        return parallel("Kafka compatibility", Pool.named("environments"))
                                .each(
                                        List.of(new Environment("kafka-1"), new Environment("kafka-2")),
                                        env -> forInstance(env.name(), () -> new KafkaStyleTest(env))
                                                .before(KafkaStyleTest::setUp)
                                                .sequential("tests", tests -> tests
                                                        .step("produce()", KafkaStyleTest::produce)
                                                        .step("consume()", KafkaStyleTest::consume))
                                                .after(KafkaStyleTest::tearDown))
                                .build();
                    }

                    private void setUp() throws Throwable {}

                    private void produce() throws Throwable {}

                    private void consume() {}

                    private void tearDown() {}
                }
                """);
    }

    @Test
    @DisplayName("API-03/06/07: nested subtrees, no-instance tasks, and heterogeneous matrices compile")
    void nestedAndHeterogeneousExamplesCompile() throws IOException {
        assertCompiles("NestedStyleTest", """
                import static org.paramixel.api.Tasks.*;

                import java.util.List;
                import org.paramixel.api.Pool;
                import org.paramixel.api.Task;

                public final class NestedStyleTest {

                    interface Fixture { void verify(); }

                    static final class One implements Fixture { public void verify() {} }

                    static final class Two implements Fixture { public void verify() {} }

                    public static Task definition() {
                        return sequential("Integration suite")
                                .step("Validate configuration", () -> {})
                                .each(
                                        List.of(new One(), new Two()),
                                        fixture -> forInstance(fixture.getClass().getSimpleName(), () -> fixture)
                                                .before(Fixture::verify)
                                                .step("Verify", Fixture::verify)
                                                .after(Fixture::verify))
                                .child(task("Group").step("Ping", () -> {}))
                                .parallel("Matrix", Pool.named("environments"), matrix -> matrix
                                        .each(List.of("a", "b"), name -> forInstance(name, () -> new One())
                                                .step("Consume", One::verify, children -> children
                                                        .parallel("Record checks", Pool.named("checks"), checks -> checks
                                                                .step("Verify payload", One::verify)
                                                                .step("Verify headers", One::verify)))))
                                .build();
                    }
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
    @DisplayName("API-08: an incompatible method reference is rejected")
    void incompatibleMethodReferenceDoesNotCompile() throws IOException {
        assertDoesNotCompile("IncompatibleReference", """
                import static org.paramixel.api.Tasks.forInstance;

                public final class IncompatibleReference {

                    static final class Fixture { void run() {} }

                    static final class Other { void run() {} }

                    public static void definition() {
                        forInstance("x", Fixture::new).step("run", Other::run);
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
