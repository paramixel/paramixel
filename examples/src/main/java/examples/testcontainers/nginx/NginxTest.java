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

import examples.support.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Spec;

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
        Runner.defaultRunner().runAndExit(factory());
    }

    /**
     * Builds a parallel action tree with one instance branch per Nginx Docker image,
     * each managing a {@code NginxTest} instance with setUp, test, and tearDown lifecycle.
     *
     * @return the action tree for this test
     * @throws Throwable if environment creation fails
     */
    @Paramixel.Factory
    public static Spec<?> factory() throws Throwable {
        var parallel = Parallel.of(NginxTest.class.getName());
        for (NginxTestEnvironment environment : NginxTestEnvironment.createTestEnvironments()) {
            parallel.child(Instance.of(environment.name(), () -> new NginxTest(environment))
                    .child(Lifecycle.<NginxTest>of("lifecycle")
                            .before("setUp()", "Before", NginxTest::setUp)
                            .child("testGet()", NginxTest::testGet)
                            .after("tearDown()", "After", NginxTest::tearDown)
                            .resolve()));
        }
        return parallel;
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
        String content = doGet("http://localhost:" + port);
        assertThat(content).contains("Welcome to nginx!");
    }

    public void tearDown() {
        LOGGER.info("[%s] destroy test environment ...", environment.name());

        environment.close();
    }

    private static String doGet(final String url) throws Exception {
        var result = new StringBuilder();
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
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
