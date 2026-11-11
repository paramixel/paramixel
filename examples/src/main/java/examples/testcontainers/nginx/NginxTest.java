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

package examples.testcontainers.nginx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;

import examples.support.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Pool;
import org.paramixel.api.Runner;
import org.paramixel.api.Task;

/**
 * Parameterized integration test that starts Nginx containers for each Docker image
 * listed in {@code /docker-images.txt}, performs an HTTP GET, and asserts the
 * default welcome page is served.
 */
public class NginxTest {

    private static final Logger LOGGER = Logger.createLogger(NginxTest.class);

    private final NginxTestEnvironment environment;

    /**
     * Runs the action factory and exits the JVM.
     *
     * @param args command-line arguments (ignored)
     * @throws Throwable if environment creation fails
     */
    public static void main(final String[] args) throws Throwable {
        System.exit(Runner.builder().build().run(factory()).exitCode());
    }

    /**
     * Builds a parallel task tree with one typed fixture per Nginx Docker image.
     *
     * <p>Each fixture declares its type once; every step names only its method, and the pool named
     * {@code environments} bounds how many fixtures are alive at once.
     *
     * @return the definition for this test
     * @throws Throwable if environment creation fails
     */
    @Paramixel.Factory
    public static Task factory() throws Throwable {
        return parallel(NginxTest.class.getName(), Pool.named("environments"))
                .each(
                        NginxTestEnvironment.createTestEnvironments(),
                        environment -> forInstance(environment.name(), () -> new NginxTest(environment))
                                .before(NginxTest::setUp)
                                .step("testGet()", NginxTest::testGet)
                                .after(NginxTest::tearDown))
                .build();
    }

    private NginxTest(final NginxTestEnvironment environment) {
        this.environment = environment;
    }

    public void setUp() {
        LOGGER.info("[%s] initialize test environment ...", environment.name());

        environment.initialize();
        assertThat(environment.isRunning()).isTrue();
    }

    public void testGet() throws Exception {
        LOGGER.info("[%s] testing GET ...", environment.name());

        int port = environment.getNginxContainer().getMappedPort(80);
        var content = doGet("http://localhost:" + port);
        assertThat(content).contains("Welcome to nginx!");
    }

    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }

    private static String doGet(final String url) throws Exception {
        var result = new StringBuilder();
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);

        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }
}
